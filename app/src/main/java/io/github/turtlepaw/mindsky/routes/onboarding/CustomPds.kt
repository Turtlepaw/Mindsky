package io.github.turtlepaw.mindsky.routes.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.routes.TopAppBarCommon
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.christian.ozone.XrpcBlueskyApi

enum class ConnectionState {
    Connecting,
    Connected,
    Failed,
    Idle
}

@Destination<RootGraph>
@Composable
fun CustomPds(navigator: DestinationsNavigator) {
    val viewModel = rememberOnboardingViewModel()

    var pdsUrl by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(ConnectionState.Idle) }

    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(pdsUrl) {
        debounceJob?.cancel()
        if (pdsUrl.isBlank()) {
            isConnected = ConnectionState.Idle
            return@LaunchedEffect
        }
        debounceJob = coroutineScope.launch {
            // Debounce delay: 500ms after user stops typing
            kotlinx.coroutines.delay(500L)
            isConnected = ConnectionState.Connecting
            try {
                val httpClient = HttpClient(OkHttp) {
                    install(Logging) {
                        logger = object : Logger {
                            override fun log(message: String) {
                                Log.v("Ktor_Default", message)
                            }
                        }
                        level = LogLevel.HEADERS
                    }
                    defaultRequest {
                        url.takeFrom(pdsUrl)
                    }
                    expectSuccess = true
                }

                val api = XrpcBlueskyApi(httpClient)
                val server = api.describeServer()
                Log.d("CustomPds", "Connected to server: ${server}")
                isConnected = ConnectionState.Connected
            } catch (e: Exception) {
                isConnected = ConnectionState.Failed
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.custom_pds,
                MaterialTheme.typography.titleMedium
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter your custom PDS URL",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "This is the URL of the server you want to use for your account.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = pdsUrl,
                        onValueChange = { pdsUrl = it.trim() },
                        label = { Text("Custom PDS URL") },
                        placeholder = { Text("https://your.pds.url") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                onClick = {
                }
            ) {
                when (isConnected) {
                    ConnectionState.Connecting -> Text("Connecting...")
                    ConnectionState.Connected -> Text("Connected! Proceed")
                    ConnectionState.Failed -> Text("Connection Failed. Try Again")
                    ConnectionState.Idle -> Text("Continue")
                }
            }
        }
    }
}

