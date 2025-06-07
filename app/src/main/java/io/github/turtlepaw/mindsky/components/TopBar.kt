package io.github.turtlepaw.mindsky.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.turtlepaw.mindsky.R
import kotlinx.coroutines.launch

@Composable
fun TopBarBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,                 // Restored
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), // Restored
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), // Restored
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun TopBarInteractiveElements(
    listState: LazyListState,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        label = "Scale Animation"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Icon(
                painter = painterResource(R.drawable.ic_mindsky),
                contentDescription = "Mindsky icon",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(30.dp)
                    .scale(scale)
                    .animateContentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Scroll to top first
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                        // Then invoke the click handler from Feed.kt
                        onIconClick()
                    },
            )
        }
    }
}
