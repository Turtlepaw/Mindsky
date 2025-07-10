package io.github.turtlepaw.mindsky.cache

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

class LabelManager(context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val labelerCache: LabelerCache = LabelerCache(context)
    private val labelerDefinitionsCache: LabelerDefCache = LabelerDefCache(context)

    val labelersFlow = labelerCache.labelersFlow
    val labelersDefinitionFlow = labelerDefinitionsCache.definitionsFlow

    suspend fun preWarmDefinitions(blueskyApi: AuthenticatedXrpcBlueskyApi) {
        coroutineScope.launch {
            labelersFlow.collect {
                labelerDefinitionsCache.getDefinitions(
                    it,
                    blueskyApi
                )
            }
        }
    }

    suspend fun revalidateLabelers(blueskyApi: AuthenticatedXrpcBlueskyApi) {
        labelerCache.fetchAndCacheLabelers(blueskyApi)
    }
}