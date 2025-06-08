package io.github.turtlepaw.mindsky.routes

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

@Destination<RootGraph>
@Composable
fun Onboarding(navigator: DestinationsNavigator) {
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
