package io.github.turtlepaw.mindsky.repositories

import android.content.Context
import android.util.Log
import app.bsky.labeler.GetServicesResponseViewUnion
import com.atproto.label.Label
import com.atproto.label.LabelValueDefinition
import io.github.turtlepaw.mindsky.cache.LabelerCache
import io.github.turtlepaw.mindsky.cache.LabelerDefCache
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

class LabelRepository(
    private val context: Context,
    private val blueskyApi: AuthenticatedXrpcBlueskyApi,
    private val labelerCache: LabelerCache
) {
    private val labelerDefCache = LabelerDefCache(context)

    // Prevent concurrent fetches of same data
    private val fetchingDefinitions = mutableSetOf<String>()
    private val definitionsCache = mutableMapOf<String, GetServicesResponseViewUnion>()

    suspend fun preWarmDefinitions(labelerDids: List<String>) {
        Log.d("LabelRepository", "Pre-warming definitions for ${labelerDids.size} labelers.")
        getDefinitionsThreadSafe(labelerDids)
    }

    fun resolveLabels(labels: List<Label>): List<LabelValueDefinition> {
        if (labels.isEmpty()) return emptyList()

        val labelerDids = labels.map { it.src }.distinct().map { it.did }
        val definitions = mutableMapOf<String, GetServicesResponseViewUnion>()
        val missingDids = mutableListOf<String>()

        labelerDids.forEach { did ->
            definitionsCache[did]?.let {
                definitions[did] = it
            } ?: missingDids.add(did)
        }

        if (missingDids.isNotEmpty()) {
            Log.w(
                "LabelRepository",
                "Missing definitions for labelers: $missingDids. They were not pre-warmed."
            )
        }

        return labels.mapNotNull { label ->
            resolveLabel(label, definitions[label.src.did])
        }
    }

    private suspend fun getDefinitionsThreadSafe(labelerDids: List<String>): Map<String, GetServicesResponseViewUnion> {
        // Check memory cache first
        val cached = mutableMapOf<String, GetServicesResponseViewUnion>()
        val toFetch = mutableListOf<String>()

        labelerDids.forEach { did ->
            if (definitionsCache.containsKey(did)) {
                cached[did] = definitionsCache[did]!!
            } else if (!fetchingDefinitions.contains(did)) {
                toFetch.add(did)
                fetchingDefinitions.add(did) // Mark as being fetched
            }
        }

        try {
            if (toFetch.isNotEmpty()) {
                Log.d("LabelRepository", "Fetching definitions for: $toFetch")
                val fresh = labelerDefCache.getDefinitions(toFetch, blueskyApi)

                // Update memory cache
                definitionsCache.putAll(fresh)
                cached.putAll(fresh)
                Log.d("LabelRepository", "Fetched ${fresh.size} new definitions.")
            }
        } finally {
            // Always remove from fetching set
            toFetch.forEach { fetchingDefinitions.remove(it) }
        }

        return cached
    }

    private fun resolveLabel(
        label: Label,
        definition: GetServicesResponseViewUnion?
    ): LabelValueDefinition? {
        if (definition == null) return null

        // Extract display text from labeler definition
        return when (definition) {
            is GetServicesResponseViewUnion.LabelerViewDetailed -> {
                definition.value.policies?.labelValueDefinitions?.find { it.identifier == label.`val` }
            }
            // Handle other view types
            else -> {
                Log.e("LabelRepository", "Unknown view type: ${definition.javaClass.simpleName}")
                null
            }
        }
    }
}
