package io.github.turtlepaw.mindsky.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.turtlepaw.mindsky.R
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BouncingStar(
    modifier: Modifier = Modifier,
    starSize: Dp = 200.dp,
    endPadding: Dp = 35.dp,
    initialUpwardVelocityDps: Float = 650f,
    gravityDpsPerSecSquared: Float = 700f,
    dampingFactor: Float = 1f, // Increased for more persistent bounce
    settleThresholdDps: Dp = 3.dp
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val starSizePx = with(density) { starSize.toPx() }
        val gravityPxPerSecSquared = with(density) { gravityDpsPerSecSquared.dp.toPx() }
        val hapticFeedback = LocalHapticFeedback.current
        val initialVelocityPxPerSec = with(density) { -initialUpwardVelocityDps.dp.toPx() }

        val positionY = remember { Animatable(0f) }
        var velocityYPxPerSec by remember { mutableStateOf(0f) }

        val infiniteTransition = rememberInfiniteTransition(label = "starSpin")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing)
            ),
            label = "starRotation"
        )

        val bounceFloorPx = constraints.maxHeight - starSizePx

        LaunchedEffect(constraints.maxHeight, density, starSize, initialUpwardVelocityDps) {
            positionY.snapTo(bounceFloorPx)
            velocityYPxPerSec = with(density) { -initialUpwardVelocityDps.dp.toPx() }
        }

        LaunchedEffect(constraints.maxHeight, density, starSize, gravityPxPerSecSquared, dampingFactor, settleThresholdDps) {
            var lastFrameTimeNanos = System.nanoTime()

            while (isActive) {
                val newFrameTimeNanos = withFrameNanos { it }
                val deltaTimeSeconds = (newFrameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = newFrameTimeNanos

                if (deltaTimeSeconds <= 0f) continue

                var currentPosY = positionY.value
                velocityYPxPerSec += gravityPxPerSecSquared * deltaTimeSeconds
                currentPosY += velocityYPxPerSec * deltaTimeSeconds

                if (currentPosY >= bounceFloorPx) {
                    currentPosY = bounceFloorPx
                    if (velocityYPxPerSec > 0) {
                        velocityYPxPerSec = initialVelocityPxPerSec

                        // Trigger haptic on each bounce
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                positionY.snapTo(currentPosY)
            }
        }

        val staticXOffset = constraints.maxWidth - starSizePx - with(density) { endPadding.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        staticXOffset.roundToInt(),
                        positionY.value.roundToInt()
                    )
                }
                .size(starSize)
                .graphicsLayer {
                    rotationZ = rotationAngle
                }
        ){
            Icon(
                painter = painterResource(id = R.drawable.ic_mindsky_outlined),
                contentDescription = "Mindsky Icon",
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary.copy(.85f),
            )
        }
    }
}