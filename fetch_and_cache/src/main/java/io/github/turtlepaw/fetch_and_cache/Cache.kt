package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json.Default.encodeToString
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json.Default.decodeFromString


data class LoadResult<V>(
    val value: V?,
    val isLoading: Boolean,
    val error: String?
)

class Cache<V>(
    private val fetcher: suspend (key: String) -> V,
    private val serializer: KSerializer<V>,
    private val identifier: String,
    context: Context
) {
    val memory = mutableMapOf<String, V>()
    val mutex = Mutex()

    init {
        val file = File(context.cacheDir, identifier)
        if (file.exists()) {
            val mapSerializer =
                MapSerializer(String.serializer(), serializer)
            memory.putAll(decodeFromString(mapSerializer, file.readText()))
        }
    }

    suspend fun loadAsync(key: String, context: Context): LoadResult<V> {
        var value: V? = null
        var isLoading = true
        var error: String? = null

        try {
            value = fetcher(key)
            mutex.withLock {
                memory[key] = value!!
                persistResult(context)
            }
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false

        return LoadResult(
            value = value,
            isLoading = isLoading,
            error = error
        )
    }

    @Composable
    fun load(key: String): LoadResult<V> {
        var value: V? by remember { mutableStateOf(memory[key]) }
        var isLoading by remember { mutableStateOf(value == null) }
        var error by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        LaunchedEffect(key) {
            try {
                value = fetcher(key)
                memory[key] = value!!
                persistResult(context)
            } catch (e: Exception) {
                error = e.message
            }
            isLoading = false
        }

        return LoadResult(
            value = value,
            isLoading = isLoading,
            error = error
        )
    }

    private fun persistResult(context: Context){
        val file = File(context.cacheDir, identifier)
        val mapSerializer =
            MapSerializer(String.serializer(), serializer)

        file.writeText(
            encodeToString(mapSerializer, memory)
        )
    }
}