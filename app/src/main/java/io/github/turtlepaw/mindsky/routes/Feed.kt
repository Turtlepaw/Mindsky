package io.github.turtlepaw.mindsky.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.ml.ModelDownloadWorker
import io.github.turtlepaw.mindsky.replaceCurrent
import java.io.File

@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val files = listOf(
            File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME),
            File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        )
        if (!files.all { it.exists() && it.length() > 0 }) {
            nav.replaceCurrent(DownloadModelDestination)
        }
    }
}