// PostCache.kt
package io.github.turtlepaw.mindsky.cache

import android.content.Context
import android.util.Log
import app.bsky.feed.PostView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import sh.christian.ozone.api.AtUri
import java.io.File

class PostCache(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "posts")
    private val maxCacheSize = 100 * 1024 * 1024 // 100MB
    private val maxCacheAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    private val currentCacheVersion = 2 // Increment when PostView structure changes

    private val prefs = context.getSharedPreferences("post_cache_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        useAlternativeNames = false
        allowStructuredMapKeys = true
        // This helps with some polymorphic issues
        classDiscriminator = "\$type"
    }

    init {
        // Create cache directory if it doesn't exist
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Check cache version and clear if outdated
        val storedVersion = prefs.getInt("cache_version", 0)
        if (storedVersion != currentCacheVersion) {
            Log.d("PostCache", "Cache version changed from $storedVersion to $currentCacheVersion, clearing cache")
            runBlocking { clearCache() }
            prefs.edit().putInt("cache_version", currentCacheVersion).apply()
        }
    }

    suspend fun cachePost(post: PostView) = withContext(Dispatchers.IO) {
        try {
            // Pre-validate that the post can be decoded before caching
            if (!isPostDecodable(post)) {
                Log.w("PostCache", "Skipping cache for non-decodable post: ${post.uri.atUri}")
                return@withContext
            }

            val fileName = sanitizeFileName(post.uri.atUri)
            val cacheFile = File(cacheDir, "$fileName.json")

            val cacheEntry = PostCacheEntry(
                post = post,
                timestamp = System.currentTimeMillis(),
                version = currentCacheVersion
            )

            val jsonString = json.encodeToString(cacheEntry)
            cacheFile.writeText(jsonString)

            Log.d("PostCache", "Cached post: ${post.uri.atUri}")

            // Clean up old cache entries periodically
            if (System.currentTimeMillis() % 10 == 0L) {
                cleanupOldEntries()
            }

        } catch (e: Exception) {
            Log.e("PostCache", "Error caching post: ${post.uri.atUri}", e)
        }
    }

    suspend fun getCachedPost(uri: AtUri): PostView? = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFileName(uri.atUri)
            val cacheFile = File(cacheDir, "$fileName.json")

            if (!cacheFile.exists()) {
                Log.d("PostCache", "No cached file found for: ${uri.atUri}")
                return@withContext null
            }

            val jsonString = cacheFile.readText()
            val cacheEntry = json.decodeFromString<PostCacheEntry>(jsonString)

            // Check version compatibility
            if (cacheEntry.version < currentCacheVersion) {
                Log.d("PostCache", "Cache entry version outdated for: ${uri.atUri}")
                cacheFile.delete()
                return@withContext null
            }

            // Check if cache entry is still valid
            if (System.currentTimeMillis() - cacheEntry.timestamp > maxCacheAge) {
                Log.d("PostCache", "Cache entry expired for: ${uri.atUri}")
                cacheFile.delete()
                return@withContext null
            }

            // Validate that the cached post is still decodable
            if (!isPostDecodable(cacheEntry.post)) {
                Log.w("PostCache", "Cached post no longer decodable, removing: ${uri.atUri}")
                cacheFile.delete()
                return@withContext null
            }

            Log.d("PostCache", "Retrieved cached post: ${uri.atUri}")
            return@withContext cacheEntry.post

        } catch (e: Exception) {
            Log.e("PostCache", "Error retrieving cached post: ${uri.atUri}", e)

            // Delete the corrupted cache file immediately
            val fileName = sanitizeFileName(uri.atUri)
            val cacheFile = File(cacheDir, "$fileName.json")
            if (cacheFile.exists()) {
                try {
                    cacheFile.delete()
                    Log.d("PostCache", "Deleted corrupted cache file for: ${uri.atUri}")
                } catch (deleteException: Exception) {
                    Log.e("PostCache", "Error deleting corrupted cache file", deleteException)
                }
            }

            // If it's a serialization error, consider clearing the entire cache
            if (e is SerializationException) {
                Log.w("PostCache", "Serialization error detected, considering cache invalidation")
                // Optionally clear all cache if this happens frequently
                // invalidateAllCache()
            }

            return@withContext null
        }
    }

    suspend fun removeCachedPost(uri: AtUri) = withContext(Dispatchers.IO) {
        try {
            val fileName = sanitizeFileName(uri.atUri)
            val cacheFile = File(cacheDir, "$fileName.json")

            if (cacheFile.exists()) {
                cacheFile.delete()
                Log.d("PostCache", "Removed cached post: ${uri.atUri}")
            }
        } catch (e: Exception) {
            Log.e("PostCache", "Error removing cached post: ${uri.atUri}", e)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    file.delete()
                }
            }
            Log.d("PostCache", "Cache cleared")
        } catch (e: Exception) {
            Log.e("PostCache", "Error clearing cache", e)
        }
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            return@withContext cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e("PostCache", "Error calculating cache size", e)
            return@withContext 0L
        }
    }

    // Helper function to check if a post can be decoded without throwing
    private fun isPostDecodable(postView: PostView): Boolean {
        return try {
            // Try to decode the post record to see if it will work
            postView.record.decodeAs<app.bsky.feed.Post>()
            true
        } catch (e: SerializationException) {
            Log.d("PostCache", "Post not decodable due to serialization: ${postView.uri.atUri}")
            false
        } catch (e: Exception) {
            Log.d("PostCache", "Post not decodable due to error: ${postView.uri.atUri}")
            false
        }
    }

    // Add a method to get cache statistics
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
            val totalSize = files.sumOf { it.length() }
            var validEntries = 0
            var expiredEntries = 0
            var corruptedEntries = 0
            val currentTime = System.currentTimeMillis()

            files.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val cacheEntry = json.decodeFromString<PostCacheEntry>(jsonString)

                    when {
                        currentTime - cacheEntry.timestamp > maxCacheAge -> expiredEntries++
                        cacheEntry.version < currentCacheVersion -> expiredEntries++
                        else -> validEntries++
                    }
                } catch (e: Exception) {
                    corruptedEntries++
                }
            }

            CacheStats(
                totalFiles = files.size,
                totalSize = totalSize,
                validEntries = validEntries,
                expiredEntries = expiredEntries,
                corruptedEntries = corruptedEntries
            )
        } catch (e: Exception) {
            Log.e("PostCache", "Error calculating cache stats", e)
            CacheStats(0, 0, 0, 0, 0)
        }
    }

    // Add this for better cleanup of problematic entries
    suspend fun cleanupProblematicEntries() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles()?.filter { it.name.endsWith(".json") } ?: return@withContext
            var cleaned = 0

            files.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val cacheEntry = json.decodeFromString<PostCacheEntry>(jsonString)

                    // Test if the cached post is decodable
                    if (!isPostDecodable(cacheEntry.post)) {
                        file.delete()
                        cleaned++
                        Log.d("PostCache", "Cleaned problematic cache entry: ${file.name}")
                    }
                } catch (e: Exception) {
                    // If we can't even read the cache entry, delete it
                    file.delete()
                    cleaned++
                    Log.d("PostCache", "Cleaned corrupted cache entry: ${file.name}")
                }
            }

            Log.d("PostCache", "Cleanup completed, removed $cleaned problematic entries")
        } catch (e: Exception) {
            Log.e("PostCache", "Error during problematic entries cleanup", e)
        }
    }

    private suspend fun cleanupOldEntries() = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val files = cacheDir.listFiles() ?: return@withContext

            // Remove expired files
            files.filter { it.name.endsWith(".json") }.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val cacheEntry = json.decodeFromString<PostCacheEntry>(jsonString)

                    if (currentTime - cacheEntry.timestamp > maxCacheAge ||
                        cacheEntry.version < currentCacheVersion) {
                        file.delete()
                        Log.d("PostCache", "Deleted expired cache file: ${file.name}")
                    }
                } catch (e: Exception) {
                    // If we can't read the file, delete it
                    file.delete()
                    Log.d("PostCache", "Deleted corrupted cache file: ${file.name}")
                }
            }

            // If cache is still too large, remove oldest files
            val remainingFiles = cacheDir.listFiles()?.filter { it.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() } ?: return@withContext

            var totalSize = remainingFiles.sumOf { it.length() }

            if (totalSize > maxCacheSize) {
                remainingFiles.forEach { file ->
                    if (totalSize <= maxCacheSize) return@forEach

                    totalSize -= file.length()
                    file.delete()
                    Log.d("PostCache", "Deleted cache file for size limit: ${file.name}")
                }
            }

        } catch (e: Exception) {
            Log.e("PostCache", "Error during cache cleanup", e)
        }
    }

    private fun sanitizeFileName(uri: String): String {
        // Replace invalid filename characters with underscores
        return uri.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    /**
     * Clears all cache entries and forces a fresh start.
     * Useful if there are serialization compatibility issues.
     */
    suspend fun invalidateAllCache() = withContext(Dispatchers.IO) {
        try {
            clearCache()
            prefs.edit().putInt("cache_version", currentCacheVersion).apply()
            Log.d("PostCache", "All cache invalidated due to serialization issues")
        } catch (e: Exception) {
            Log.e("PostCache", "Error invalidating cache", e)
        }
    }

    /**
     * Check if cache needs maintenance and perform it
     */
    suspend fun performMaintenance() = withContext(Dispatchers.IO) {
        try {
            val stats = getCacheStats()
            Log.d("PostCache", "Cache stats: $stats")

            // If more than 20% of entries are corrupted or expired, clean them up
            val problematicRatio = (stats.corruptedEntries + stats.expiredEntries).toFloat() / stats.totalFiles
            if (problematicRatio > 0.2f) {
                Log.d("PostCache", "High ratio of problematic entries ($problematicRatio), performing cleanup")
                cleanupProblematicEntries()
                cleanupOldEntries()
            }
        } catch (e: Exception) {
            Log.e("PostCache", "Error during maintenance", e)
        }
    }
}

@Serializable
data class PostCacheEntry(
    val post: PostView,
    val timestamp: Long,
    val version: Int = 1
)

data class CacheStats(
    val totalFiles: Int,
    val totalSize: Long,
    val validEntries: Int,
    val expiredEntries: Int,
    val corruptedEntries: Int
) {
    override fun toString(): String {
        return "CacheStats(files=$totalFiles, size=${totalSize/1024}KB, valid=$validEntries, expired=$expiredEntries, corrupted=$corruptedEntries)"
    }
}