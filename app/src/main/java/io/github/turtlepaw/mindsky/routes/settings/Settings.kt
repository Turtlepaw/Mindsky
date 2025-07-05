package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.InterestClustersDestination
import com.ramcosta.composedestinations.generated.destinations.InterestSourcesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.preference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Settings(navigator: DestinationsNavigator) {
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.settings_title)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            preference(
                key = "interest_sources",
                title = {
                    R.string.interest_sources.StringComposable()
                },
                summary = {
                    R.string.interest_sources_description.StringComposable()
                },
                onClick = {
                    navigator.navigate(InterestSourcesDestination)
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
            preference(
                key = "interest_clusters",
                title = {
                    R.string.interest_clusters.StringComposable()
                },
                summary = {
                    R.string.interest_clusters_description.StringComposable()
                },
                onClick = {
                    navigator.navigate(InterestClustersDestination)
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
//                item {
//                    Button(
//                        onClick = {
//                            WorkManager.getInstance(context).enqueueImmediateWorkers()
//                        }
//                    ) {
//                        Text("Launch embedding")
//                    }
//                }
        }
    }
}

object TopAppBarCommon {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun withBack(navigator: DestinationsNavigator, title: Int?, titleStyle: TextStyle? = null) {
        TopAppBar(
            title = {
                if (title != null) Text(
                    stringResource(title),
                    style = titleStyle ?: MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = { navigator.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}