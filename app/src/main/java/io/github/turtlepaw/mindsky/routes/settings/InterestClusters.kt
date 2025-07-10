package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun InterestClusters(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var clusters by remember { mutableStateOf(emptyList<InterestCluster>()) }

    LaunchedEffect(Unit) {
        isLoading = true
        val box = ObjectBox.store.boxFor(InterestCluster::class.java)
        clusters = box.all
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.interest_clusters)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (isLoading) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            } else if (clusters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.interest_clusters_empty),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                items(clusters) {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            //contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = it.id.toString(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}