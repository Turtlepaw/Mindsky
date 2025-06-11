package io.github.turtlepaw.mindsky.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionManager(private val context: Context) {

    private val alias = "mindsky_aes_key"
    private val prefsName = "mindsky_prefs"
    private val keySession = "user_session"
    private val gson = Gson()

    private val _sessionFlow = MutableStateFlow<UserSession?>(null)
    val sessionFlow: StateFlow<UserSession?> = _sessionFlow.asStateFlow()

    init {
        generateSecretKeyIfNotExists()
        _sessionFlow.value = getSession() // load saved session on startup
    }

    private fun generateSecretKeyIfNotExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(4 + iv.size + cipherText.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(cipherText)
        return Base64.encodeToString(buffer.array(), Base64.DEFAULT)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val buffer = ByteBuffer.wrap(bytes)
        val ivSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val cipherText = ByteArray(buffer.remaining())
        buffer.get(cipherText)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun saveSession(session: UserSession) {
        val json = gson.toJson(session)
        val encrypted = encrypt(json)
        prefs.edit { putString(keySession, encrypted) }
        _sessionFlow.value = session
    }

    fun getSession(): UserSession? {
        val encrypted = prefs.getString(keySession, null) ?: return null
        return try {
            val decrypted = decrypt(encrypted)
            gson.fromJson(decrypted, UserSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearSession() {
        prefs.edit { remove(keySession) }
        _sessionFlow.value = null
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
}
