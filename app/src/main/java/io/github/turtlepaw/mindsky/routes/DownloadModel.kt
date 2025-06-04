package io.github.turtlepaw.mindsky.routes

import android.text.Layout
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.ml.DownloadStage
import io.github.turtlepaw.mindsky.ml.ModelDownloadWorker
import io.github.turtlepaw.mindsky.replaceCurrent
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun DownloadModel(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val workManager = WorkManager.getInstance(context)
    val workId = remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(Unit) {
        val workRequest = ModelDownloadWorker.buildWorkRequest(DownloadStage.Model)

        workManager.enqueueUniqueWork(
            "modelDownload",
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        // Observe all workers in the unique work chain
        workManager.getWorkInfosForUniqueWorkLiveData("modelDownload").observeForever { workInfos ->
            // Find first running or enqueued worker
            val runningWork = workInfos.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }

            if (runningWork == null || runningWork.state.isFinished) {
                navigator.replaceCurrent(FeedDestination)
                workId.value = null
            } else {
                // Track the currently running worker's id to observe progress
                workId.value = runningWork.id
            }
        }
    }

    val workInfo by workId.value?.let {
        workManager.getWorkInfoByIdLiveData(it).observeAsState()
    } ?: remember { mutableStateOf(null) }

    val progress = workInfo?.progress?.getInt("progress", -1) ?: -1
    val stageString = workInfo?.progress?.getString("stage")

    val stage = remember(stageString) {
        try {
            stageString?.let { DownloadStage.valueOf(it) }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically)
        ) {
            //ContainedLoadingIndicator()
            if(progress >= 0){
                LinearWavyProgressIndicator(
                    progress = { progress.toFloat() / 100f },
                )
            } else {
                LinearWavyProgressIndicator()
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if(progress >= 0) "Downloading ${if (stage == DownloadStage.Tokenizer) "tokenizer" else "model"}..." else "Queuing download...",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Downloading MiniLM L6 v2 from \n🤗 Hugging Face",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}