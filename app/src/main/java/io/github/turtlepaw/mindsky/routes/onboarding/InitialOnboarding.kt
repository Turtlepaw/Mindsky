package io.github.turtlepaw.mindsky.routes.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.BouncingStar

@Destination<RootGraph>
@Composable
fun InitialOnboarding(navigator: DestinationsNavigator) {
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp) // Ensures it stays below the top bar
            ) {
                BouncingStar(
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 25.dp, end = 20.dp)
                        .offset(y = (-250).dp)
                ) {
                    Text(
                        "Your feed,\nunfolded.",
                        style = MaterialTheme.typography.displayMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Welcome to Mindsky. Your Bluesky feed powered by on-device AI.",
                        style = MaterialTheme.typography.bodyLarge,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.Transparent)
                    .align(Alignment.BottomCenter)
            ) {
//                HorizontalDivider(
//                    modifier = Modifier.align(Alignment.TopCenter),
//                )

                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(onClick = {
                        navigator.navigate(LoginDestination)
                    }) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}
