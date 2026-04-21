package io.github.turtlepaw.mindsky.cache

import android.content.Context
import android.util.Log
import app.bsky.actor.ProfileViewDetailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import sh.christian.ozone.api.Did
import java.io.File

class ProfileCache(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "profiles")
    private val maxCacheSize = 50 * 1024 * 1024 // 50MB
    private val maxCacheAge = 12 * 60 * 60 * 1000L // 12 hours in milliseconds
    private val currentCacheVersion = 1

    private val prefs = context.getSharedPreferences("profile_cache_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "\$type"
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val storedVersion = prefs.getInt("cache_version", 0)
        if (storedVersion != currentCacheVersion) {
            runBlocking { clearCache() }
            prefs.edit().putInt("cache_version", currentCacheVersion).apply()
        }
    }

    suspend fun cacheProfile(profile: ProfileViewDetailed) = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFileName(profile.did.did)
            val cacheFile = File(cacheDir, "$fileName.json")

            val cacheEntry = ProfileCacheEntry(
                profile = profile,
                timestamp = System.currentTimeMillis(),
                version = currentCacheVersion
            )

            val jsonString = json.encodeToString(cacheEntry)
            cacheFile.writeText(jsonString)

            Log.d("ProfileCache", "Cached profile: ${profile.did.did}")

            if (System.currentTimeMillis() % 20 == 0L) {
                cleanupOldEntries()
            }
        } catch (e: Exception) {
            Log.e("ProfileCache", "Error caching profile: ${profile.did.did}", e)
        }
    }

    suspend fun getCachedProfile(did: Did): ProfileViewDetailed? = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFileName(did.did)
            val cacheFile = File(cacheDir, "$fileName.json")

            if (!cacheFile.exists()) return@withContext null

            val jsonString = cacheFile.readText()
            val cacheEntry = json.decodeFromString<ProfileCacheEntry>(jsonString)

            if (cacheEntry.version < currentCacheVersion ||
                System.currentTimeMillis() - cacheEntry.timestamp > maxCacheAge) {
                cacheFile.delete()
                return@withContext null
            }

            return@withContext cacheEntry.profile
        } catch (e: Exception) {
            Log.e("ProfileCache", "Error retrieving cached profile: ${did.did}", e)
            return@withContext null
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e("ProfileCache", "Error clearing cache", e)
        }
    }

    private suspend fun cleanupOldEntries() = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val files = cacheDir.listFiles() ?: return@withContext

            files.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val cacheEntry = json.decodeFromString<ProfileCacheEntry>(jsonString)
                    if (currentTime - cacheEntry.timestamp > maxCacheAge ||
                        cacheEntry.version < currentCacheVersion) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    file.delete()
                }
            }

            val remainingFiles = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
            var totalSize = remainingFiles.sumOf { it.length() }
            if (totalSize > maxCacheSize) {
                remainingFiles.forEach { file ->
                    if (totalSize <= maxCacheSize) return@forEach
                    totalSize -= file.length()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileCache", "Error during cache cleanup", e)
        }
    }

    private fun sanitizeFileName(did: String): String {
        return did.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

@Serializable
data class ProfileCacheEntry(
    val profile: ProfileViewDetailed,
    val timestamp: Long,
    val version: Int
)
