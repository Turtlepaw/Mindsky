package io.github.turtlepaw.mindsky.routes.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.generated.destinations.PdsListDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.MindskyApplication
import io.github.turtlepaw.mindsky.auth.AuthState
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.replaceCurrent
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel

@Destination<RootGraph>
@Composable
fun Login(navigator: DestinationsNavigator) {
    val viewModel = rememberOnboardingViewModel()

    var handle by remember { mutableStateOf("") }
    val context = LocalContext.current

    val authViewModel = rememberAuthViewModel()
    val authState by authViewModel.authState.collectAsState()
    val isLoading = authState == AuthState.Loading
    val errorMessage = (authState as? AuthState.Error)?.message

    androidx.compose.runtime.LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            val session = (authState as AuthState.Authenticated).session
            val application = context.applicationContext as MindskyApplication
            application.configureAuthenticatedApi(session)
            navigator.popBackStack()
            navigator.replaceCurrent(DownloadModelDestination)
        }
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Login to",
                style = MaterialTheme.typography.titleSmall
            )

            val pds = Pds.All.find { it.url == viewModel.pds }
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                if (pds != null && pds.iconUrl != null) {
                    Avatar(
                        modifier = Modifier.size(35.dp),
                        pds.iconUrl,
                        "${pds.name} icon",
                        clip = MaterialTheme.shapes.medium
                    )
                }

                Column(
                    horizontalAlignment = if (pds == null) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        pds?.name ?: "Custom PDS",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
//            OutlinedTextField(
//                value = hostUrl,
//                onValueChange = { hostUrl = it.trim() },
//                label = { Text("Bluesky Host URL") },
//                placeholder = { Text("e.g., https://bsky.social") },
//                modifier = Modifier.fillMaxWidth(),
//                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
//                singleLine = true
//            )
//            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("Handle") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val trimmed = handle.trim()
                    Log.d("LoginRoute", "Starting OAuth for handle: $trimmed")
                    authViewModel.startOAuthFlow(trimmed)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = handle.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text("Continue")
            }
            OutlinedButton(
                onClick = {
                    navigator.navigate(PdsListDestination)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create an account")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Public,
                    "Globe",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(MaterialTheme.typography.bodySmall.toDp() + 2.dp)
                )
                Text(
                    pds?.url ?: viewModel.pds,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
