package io.github.turtlepaw.mindsky.routes.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import io.github.turtlepaw.mindsky.R
import kotlinx.coroutines.launch

@Destination<RootGraph>
@Composable
fun Features() {
    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp, alignment = Alignment.CenterHorizontally)
                ) {
                    Text(
                        "What's great about Mindsky?",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Feature(
                    icon = Icons.Rounded.ViewInAr,
                    contentDescription = "Target Box",
                    title = "Personalized Feed",
                    description = "A feed personalized to you based on your likes."
                )
            }
            item {
                Feature(
                    icon = Icons.Rounded.Lock,
                    contentDescription = "Lock",
                    title = "Your data stays on your device",
                    description = "Data is stored and processed on-device."
                )
            }
            item {
                Feature(
                    icon = Icons.Rounded.CloudOff,
                    contentDescription = "Cloud Off",
                    title = "Serverless",
                    description = "We literally don't even own a server."
                )
            }
            item {
                Card(
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                            16.dp
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Science,
                            contentDescription = "Science",
                            modifier = Modifier.size(28.dp),
                        )
                        Text("Mindsky is an open-source experimental app.")
                    }
                }
            }
        }
    }
}

@Composable
fun Feature(icon: ImageVector, contentDescription: String, title: String, description: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )

        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}