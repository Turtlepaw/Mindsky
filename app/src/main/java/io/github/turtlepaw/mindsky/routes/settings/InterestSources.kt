package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.sliderPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun InterestSources(navigator: DestinationsNavigator) {
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.interest_sources)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sliderPreference(
                key = "following_feed_mix",
                title = {
                    R.string.following_feed_mix.StringComposable()
                },
                defaultValue = 0f,
                valueSteps = 10,
                valueRange = 0f..1f,
            ) {
                // percentage of following feed mix
                val steps = 10
                val snappedValue = (it * steps).roundToInt() / steps.toFloat()

                Box(
                    modifier = Modifier.requiredWidthIn(min = 55.dp, max = 55.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "${(snappedValue * 100).toInt()}%")
                }
            }
        }
    }
}