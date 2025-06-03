package io.github.turtlepaw.atproto.authentication

import android.net.DnsResolver
import io.github.turtlepaw.atproto.authentication.AtpAuthentication.Companion.isValidDid
import io.github.turtlepaw.atproto.authentication.AtpAuthentication.Companion.isValidHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object Identity {
    val client = OkHttpClient()

    private suspend fun getTXTRecords(domain: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://dns.google/resolve?name=$domain&type=TXT"
        val request = Request.Builder().url(url).build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(responseBody)
            val answers = json.optJSONArray("Answer") ?: return@withContext emptyList()

            (0 until answers.length()).mapNotNull { i ->
                val data = answers.getJSONObject(i).optString("data")
                data.trim('"') // Remove quotes from TXT values
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveHandleToDid(handle: String): String? {
        if (!isValidHandle(handle)) {
            // Log.e("AtpAuthentication", "Invalid handle format: $handle")
            return null
        }

        // Method 1: HTTPS Well-Known
        // TODO: Implement network request to fetch https://{handle}/.well-known/atproto-did
        // Example using a hypothetical httpGetSuspend function:
         try {
             val responseText = httpGetSuspend("https://{handle}/.well-known/atproto-did")
             val potentialDid = responseText.trim()
             if (isValidDid(potentialDid)) {
                 return potentialDid
             } else {
                 // Log.w("AtpAuthentication", "Fetched string is not a valid DID: $potentialDid")
             }
         } catch (e: Exception) {
             // Log.e("AtpAuthentication", "Error fetching well-known DID for $handle", e)
         }

        // Method 2: DNS TXT Record (More complex to implement on Android directly)
        // Log.d("AtpAuthentication", "DNS TXT record lookup for _atproto.{handle} not yet implemented as primary method.")

        // For now, returning null as network request is a TODO
        return null
    }

    suspend fun resolveDidToDocument(did: String): String? { // Or return a data class representing the DID document
        if (!isValidDid(did)) {
            // Log.e("AtpAuthentication", "Invalid DID format: $did")
            return null
        }

        // TODO: Implement network requests using an HTTP client (e.g., OkHttp)
        // and JSON parsing (e.g., kotlinx.serialization)

        return when {
            did.startsWith("did:plc:") -> {
                // TODO: Implement network request to fetch https://plc.directory/{did}
                // Example:
                // try {
                //     val responseJson = httpGetSuspend("https://plc.directory/{did}")
                //     // TODO: Validate or parse responseJson (e.g., into a data class)
                //     return responseJson // or parsed data class
                // } catch (e: Exception) {
                //     // Log.e("AtpAuthentication", "Error fetching did:plc document for $did", e)
                //     return null
                // }
                null // Placeholder
            }
            did.startsWith("did:web:") -> {
                val domain = did.substringAfter("did:web:")
                // Minimal validation for the extracted part, more robust validation might be needed
                if (domain.isBlank() || domain.contains("/") || domain.contains(":")) {
                    // Log.e("AtpAuthentication", "Invalid domain extracted from did:web: $domain")
                    return null
                }
                // TODO: Implement network request to fetch https://{domain}/.well-known/did.json
                // Example:
                // try {
                //     val responseJson = httpGetSuspend("https://{domain}/.well-known/did.json")
                //     // TODO: Validate or parse responseJson (e.g., into a data class)
                //     return responseJson // or parsed data class
                // } catch (e: Exception) {
                //     // Log.e("AtpAuthentication", "Error fetching did:web document for $did", e)
                //     return null
                // }
                null // Placeholder
            }
            else -> {
                // Log.w("AtpAuthentication", "Unsupported DID method: $did")
                return null
            }
        }
    }
}