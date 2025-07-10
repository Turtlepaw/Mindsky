package io.github.turtlepaw.mindsky.cache

import android.content.Context
import android.util.Log
import app.bsky.actor.PreferencesUnion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

@Serializable
data class LabelerConfig(
    val dids: List<String>,
    val lastUpdated: Long
)

class LabelerCache(private val context: Context) {
    private val cacheFile = context.cacheDir.resolve("labeler_cache.json")
    private val cacheTimeout = 5 * 60 * 1000L // 30 minutes
    private val fallbackLabelers = listOf<String>() // Default fallback

    private val _labelersFlow = MutableStateFlow(fallbackLabelers)
    val labelersFlow: StateFlow<List<String>> = _labelersFlow.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    init {
        // Load cached labelers on init
        CoroutineScope(Dispatchers.IO).launch {
            loadCachedLabelers()
        }
    }

    private suspend fun loadCachedLabelers() {
        try {
            if (cacheFile.exists()) {
                val cachedData = json.decodeFromString<LabelerConfig>(cacheFile.readText())
                _labelersFlow.value = cachedData.dids
                Log.d("LabelerCache", "Loaded cached labelers: ${cachedData.dids.size} entries")
            }
        } catch (e: Exception) {
            Log.e("LabelerCache", "Failed to load cached labelers: $e", e)
            _labelersFlow.value = fallbackLabelers
        }
    }

    suspend fun getLabelers(blueskyApi: AuthenticatedXrpcBlueskyApi?): List<String> {
        val cached = getCachedConfig()
        val now = System.currentTimeMillis()

        if (cached != null && (now - cached.lastUpdated) < cacheTimeout) {
            return cached.dids
        }

        // Cache is stale or missing, fetch fresh data
        Log.d("LabelerCache", "Cache stale or missing, fetching fresh labelers")
        return fetchAndCacheLabelers(blueskyApi)
    }

    suspend fun fetchAndCacheLabelers(blueskyApi: AuthenticatedXrpcBlueskyApi?): List<String> {
        return try {
            val fresh = fetchFreshLabelers(blueskyApi)
            saveToCache(fresh)
            _labelersFlow.value = fresh
            fresh
        } catch (e: Exception) {
            Log.e("LabelerCache", "Failed to fetch fresh labelers: $e")
            fallbackLabelers
        }
    }

    private suspend fun fetchFreshLabelers(blueskyApi: AuthenticatedXrpcBlueskyApi?): List<String> {
        if (blueskyApi == null) {
            Log.w("LabelerCache", "BlueskyApi not available, using fallback labelers")
            return fallbackLabelers
        }

        return try {
            val prefs: PreferencesUnion.LabelersPref =
                blueskyApi.getPreferences().requireResponse().preferences
                    .filterIsInstance<PreferencesUnion.LabelersPref>()
                    .first()

            prefs.value.labelers.map { it.did.did }
        } catch (e: Exception) {
            Log.e("LabelerCache", "Failed to fetch labelers via BlueskyApi: $e")
            fallbackLabelers
        }
    }

    private suspend fun getCachedConfig(): LabelerConfig? {
        return try {
            if (cacheFile.exists()) {
                json.decodeFromString<LabelerConfig>(cacheFile.readText())
            } else null
        } catch (e: Exception) {
            Log.e("LabelerCache", "Failed to read cache: $e")
            null
        }
    }

    private suspend fun saveToCache(labelers: List<String>) {
        if (labelers.isEmpty()) {
            Log.w("LabelerCache", "Attempted to save an empty list of labelers. Skipping.")
            return
        }

        writeLock.withLock {
            try {
                val config = LabelerConfig(
                    dids = labelers,
                    lastUpdated = System.currentTimeMillis()
                )
                cacheFile.writeText(json.encodeToString(LabelerConfig.serializer(), config))
                Log.d("LabelerCache", "Saved ${labelers.size} labelers to cache")
            } catch (e: Exception) {
                Log.e("LabelerCache", "Failed to save to cache: $e")
            }
        }
    }

    fun forceRefresh(blueskyApi: AuthenticatedXrpcBlueskyApi?) {
        Log.d("LabelerCache", "Force refresh requested")
        CoroutineScope(Dispatchers.IO).launch {
            fetchAndCacheLabelers(blueskyApi)
        }
    }
}