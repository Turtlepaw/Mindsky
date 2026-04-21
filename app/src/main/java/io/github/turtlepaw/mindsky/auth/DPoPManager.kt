package io.github.turtlepaw.mindsky.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import org.json.JSONObject

/**
 * DPoP (Demonstrating Proof-of-Possession) Manager
 *
 * Implements RFC 9449 for AT Protocol OAuth.
 */
class DPoPManager private constructor(context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val sharedPrefs = context.getSharedPreferences("dpop_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DPoPManager"
        private const val KEY_ALIAS = "dpop_key_pair"
        private const val KEY_THUMBPRINT = "dpop_key_thumbprint"

        @Volatile
        private var instance: DPoPManager? = null

        fun getInstance(context: Context): DPoPManager {
            return instance ?: synchronized(this) {
                instance ?: DPoPManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
    }

    /**
     * Generate a new EC P-256 key pair in Android KeyStore.
     */
    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            setUserAuthenticationRequired(false)
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()

        val thumbprint = calculateThumbprint()
        sharedPrefs.edit().putString(KEY_THUMBPRINT, thumbprint).apply()

        Log.d(TAG, "Generated new DPoP key pair with thumbprint: $thumbprint")
    }

    /**
     * Get JWK thumbprint for the current key.
     */
    fun getThumbprint(): String {
        return sharedPrefs.getString(KEY_THUMBPRINT, null) ?: calculateThumbprint().also {
            sharedPrefs.edit().putString(KEY_THUMBPRINT, it).apply()
        }
    }

    /**
     * Calculate JWK thumbprint according to RFC 7638.
     */
    private fun calculateThumbprint(): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey as java.security.interfaces.ECPublicKey
        val point = publicKey.w

        val x = encodeUnsignedBigInt(point.affineX)
        val y = encodeUnsignedBigInt(point.affineY)

        val jwk = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""

        val digest = MessageDigest.getInstance("SHA-256").digest(jwk.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Generate a DPoP proof JWT for a specific HTTP request.
     */
    fun generateProof(
        method: String,
        url: String,
        accessToken: String? = null,
        nonce: String? = null
    ): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey as java.security.interfaces.ECPublicKey
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey

        val point = publicKey.w
        val x = encodeUnsignedBigInt(point.affineX)
        val y = encodeUnsignedBigInt(point.affineY)

        val jwk = JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", x)
            put("y", y)
        }

        val header = JSONObject().apply {
            put("typ", "dpop+jwt")
            put("alg", "ES256")
            put("jwk", jwk)
        }

        val now = System.currentTimeMillis() / 1000

        val payload = JSONObject().apply {
            put("jti", UUID.randomUUID().toString())
            put("htm", method.uppercase())
            put("htu", stripQueryAndFragment(url))
            put("iat", now)

            if (nonce != null) {
                put("nonce", nonce)
            }

            if (accessToken != null) {
                val tokenHash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.toByteArray(Charsets.UTF_8))
                val ath = Base64.encodeToString(
                    tokenHash,
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                put("ath", ath)
            }
        }

        val headerEncoded = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
        val payloadEncoded = base64UrlEncode(payload.toString().toByteArray(Charsets.UTF_8))
        val signatureInput = "$headerEncoded.$payloadEncoded"

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signatureInput.toByteArray(Charsets.UTF_8))
        }.sign()

        val signatureEncoded = base64UrlEncode(convertDerToJoseSignature(signature))

        return "$signatureInput.$signatureEncoded"
    }

    private fun stripQueryAndFragment(url: String): String {
        return url.split('?', '#').first()
    }

    private fun convertDerToJoseSignature(derSignature: ByteArray): ByteArray {
        var offset = 0

        if (derSignature[offset++] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing SEQUENCE")
        }

        val (_, seqLengthBytes) = readDerLength(derSignature, offset)
        offset += seqLengthBytes

        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing R INTEGER")
        }

        val (rLength, rLengthBytes) = readDerLength(derSignature, offset)
        offset += rLengthBytes

        var r = derSignature.copyOfRange(offset, offset + rLength)
        offset += rLength

        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing S INTEGER")
        }

        val (sLength, sLengthBytes) = readDerLength(derSignature, offset)
        offset += sLengthBytes

        var s = derSignature.copyOfRange(offset, offset + sLength)

        while (r.isNotEmpty() && r[0] == 0x00.toByte() && r.size > 32) {
            r = r.copyOfRange(1, r.size)
        }
        while (s.isNotEmpty() && s[0] == 0x00.toByte() && s.size > 32) {
            s = s.copyOfRange(1, s.size)
        }

        val rPadded = r.padStart(32)
        val sPadded = s.padStart(32)

        return rPadded + sPadded
    }

    private fun readDerLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val firstByte = data[offset].toInt() and 0xFF

        return if (firstByte < 0x80) {
            Pair(firstByte, 1)
        } else {
            val numLengthBytes = firstByte and 0x7F
            var length = 0
            for (i in 1..numLengthBytes) {
                length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
            }
            Pair(length, 1 + numLengthBytes)
        }
    }

    private fun ByteArray.padStart(targetLength: Int): ByteArray {
        if (size >= targetLength) return copyOfRange(size - targetLength, size)

        val padded = ByteArray(targetLength)
        System.arraycopy(this, 0, padded, targetLength - size, size)
        return padded
    }

    private fun encodeUnsignedBigInt(value: BigInteger): String {
        val bytes = value.toByteArray()
        val unsigned = if (bytes[0] == 0.toByte() && bytes.size > 1) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

        val padded = unsigned.padStart(32)
        return Base64.encodeToString(padded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun regenerateKeys() {
        keyStore.deleteEntry(KEY_ALIAS)
        generateKeyPair()
    }
}
