package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json.Default.encodeToString
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


data class SingleCacheLoadResult<V>(
    val value: Map<String, V>,
    val isLoading: Boolean,
    val error: Throwable?
)

/**
 * Holds and persists a large amount of values while fetching in background.
 */
class SingleCache<V>(
    private val fetcher: suspend () -> Map<String, V>,
    private val serializer: KSerializer<V>,
    private val identifier: String,
) {
    companion object {
        @Composable
        fun <V> rememberLoad(
            fetcher: suspend () -> Map<String, V>,
            serializer: KSerializer<V>,
            identifier: String,
        ): SingleCacheLoadResult<V> {
            val cache = remember { SingleCache(fetcher, serializer, identifier) }
            return cache.load()
        }
    }
    var memory = mutableMapOf<String, V>()
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "\$type"
    }

    private fun readDisk(context: Context): Map<String, V>? {
        val file = File(context.cacheDir, getIdentifier())
        if (!file.exists()) return null


        val mapSerializer =
            MapSerializer(String.serializer(), serializer)

        return json.decodeFromString(mapSerializer, file.readText())
    }

    fun getIdentifier() = identifier.hashCode().toString() + ".json"

    private fun writeDisk(context: Context) {
        val file = File(context.cacheDir, getIdentifier())
        file.parentFile?.mkdirs()
        val mapSerializer =
            MapSerializer(String.serializer(), serializer)


        file.writeText(
            json.encodeToString(mapSerializer, memory)
        )
    }

    @Composable
    fun load(): SingleCacheLoadResult<V> {
        val context = LocalContext.current

        var value by remember { mutableStateOf<Map<String, V>>(emptyMap()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<Throwable?>(null) }

        LaunchedEffect(identifier) {
            // Memory first
            if (memory.isNotEmpty()) {
                value = memory.toMap()
                isLoading = false
            } else {
                // Disk second
                readDisk(context)?.let {
                    memory.putAll(it)
                    value = it
                    isLoading = false
                }
            }
        }

        LaunchedEffect(identifier + "_refresh") {
            try {
                mutex.withLock {
                    val fresh = fetcher()
                    memory.clear()
                    memory.putAll(fresh)

                    value = memory.toMap()
                    writeDisk(context)
                }
            } catch (e: Exception) {
                error = e
                // try again in 15s
                delay(15.seconds)
                this.launch {
                    mutex.withLock {
                        val fresh = fetcher()
                        memory.clear()
                        memory.putAll(fresh)
                        value = memory.toMap()
                        writeDisk(context)
                    }
                }
            } finally {
                isLoading = false
            }
        }

        return SingleCacheLoadResult(
            value = value,
            isLoading = isLoading,
            error = error
        )
    }
}