package io.github.turtlepaw.mindsky.cache

import android.content.Context
import android.util.Log
import app.bsky.labeler.GetServicesQueryParams
import app.bsky.labeler.GetServicesResponseViewUnion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.Did

@Serializable
private data class CachedLabelerDef(
    val did: String,
    val definitionsJson: String,
    val lastUpdated: Long
)

@Serializable
private data class LabelerDefConfig(
    val definitions: Map<String, CachedLabelerDef>,
    val lastUpdated: Long
)

class LabelerDefCache(context: Context) {
    private val cacheFile = context.cacheDir.resolve("labeler_definitions_cache.json")
    private val cacheTimeout = 24 * 60 * 60 * 1000L // 24 hours
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = kotlinx.coroutines.sync.Mutex()

    private val _definitionsFlow =
        MutableStateFlow<Map<String, GetServicesResponseViewUnion>>(emptyMap())
    val definitionsFlow: StateFlow<Map<String, GetServicesResponseViewUnion>> =
        _definitionsFlow.asStateFlow()

    init {
        // Load initial cached data
        _definitionsFlow.value = loadFromCache()
    }

    private fun loadFromCache(): Map<String, GetServicesResponseViewUnion> {
        return try {
            if (!cacheFile.exists()) return emptyMap()

            val config = json.decodeFromString<LabelerDefConfig>(cacheFile.readText())
            val now = System.currentTimeMillis()

            config.definitions
                .filter { (now - it.value.lastUpdated) < cacheTimeout }
                .mapNotNull { (did, def) ->
                    try {
                        did to json.decodeFromString<GetServicesResponseViewUnion>(def.definitionsJson)
                    } catch (e: Exception) {
                        Log.e("LabelerDefCache", "Failed to decode cached definition for $did", e)
                        null
                    }
                }.toMap()
        } catch (e: Exception) {
            Log.e("LabelerDefCache", "Failed to read cache", e)
            emptyMap()
        }
    }

    suspend fun getDefinitions(
        labelerDids: List<String>,
        blueskyApi: AuthenticatedXrpcBlueskyApi
    ): Map<String, GetServicesResponseViewUnion> {
        val cached = _definitionsFlow.value
        val missing = labelerDids.filter { it !in cached }

        if (missing.isEmpty()) {
            return cached.filterKeys { it in labelerDids }
        }

        val fresh = fetchDefinitions(missing.map { Did(it) }, blueskyApi)
        if (fresh.isNotEmpty()) {
            writeLock.withLock {
                val updated = cached + fresh
                _definitionsFlow.value = updated
                saveToCache(updated)
            }
        }

        return _definitionsFlow.value.filterKeys { it in labelerDids }
    }

    private suspend fun fetchDefinitions(
        labelerDids: List<Did>,
        blueskyApi: AuthenticatedXrpcBlueskyApi
    ): Map<String, GetServicesResponseViewUnion> {
        return try {
            val response = blueskyApi.getServices(
                GetServicesQueryParams(dids = labelerDids, detailed = true)
            ).requireResponse()

            response.views.mapNotNull { view ->
                when (view) {
                    is GetServicesResponseViewUnion.LabelerView ->
                        view.value.creator.did.did to view

                    is GetServicesResponseViewUnion.LabelerViewDetailed ->
                        view.value.creator.did.did to view

                    is GetServicesResponseViewUnion.Unknown -> {
                        Log.w("LabelerDefCache", "Unknown view type: ${view.value}")
                        null
                    }
                }
            }.toMap()
        } catch (e: Exception) {
            Log.e("LabelerDefCache", "Failed to fetch definitions", e)
            emptyMap()
        }
    }

    private fun saveToCache(definitions: Map<String, GetServicesResponseViewUnion>) {
        try {
            val now = System.currentTimeMillis()
            val cached = definitions.mapValues { (_, def) ->
                CachedLabelerDef(
                    did = when (def) {
                        is GetServicesResponseViewUnion.LabelerView -> def.value.creator.did.did
                        is GetServicesResponseViewUnion.LabelerViewDetailed -> def.value.creator.did.did
                        is GetServicesResponseViewUnion.Unknown -> ""
                    },
                    definitionsJson = json.encodeToString(def),
                    lastUpdated = now
                )
            }

            val config = LabelerDefConfig(
                definitions = cached,
                lastUpdated = now
            )

            cacheFile.writeText(json.encodeToString(config))
            Log.d("LabelerDefCache", "Saved ${definitions.size} definitions to cache")
        } catch (e: Exception) {
            Log.e("LabelerDefCache", "Failed to save to cache", e)
        }
    }
}