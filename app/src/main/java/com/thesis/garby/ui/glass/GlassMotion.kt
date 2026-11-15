package com.thesis.garby.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thesis.garby.performance.LocalPerformanceProfile
import com.thesis.garby.ui.theme.GarbyColors

// -------------------------------------------------------------------------------------------
// Screen changes
// -------------------------------------------------------------------------------------------
// Tabs sit side by side under the bottom bar, so a change moves the way the bar does: the new
// screen arrives from the side of its tab, settles with a spring and a slight scale, while the
// old one eases away the other way. Nothing here runs on the conservative performance profile.

fun tabEnter(fromIndex: Int, toIndex: Int, reducedEffects: Boolean): EnterTransition {
    if (reducedEffects) return EnterTransition.None
    val direction = if (toIndex >= fromIndex) 1 else -1
    return slideInHorizontally(
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    ) { width -> direction * width / 5 } +
        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
        scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f)
}

fun tabExit(fromIndex: Int, toIndex: Int, reducedEffects: Boolean): ExitTransition {
    if (reducedEffects) return ExitTransition.None
    val direction = if (toIndex >= fromIndex) -1 else 1
    return slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width ->
        direction * width / 8
    } + fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 0.98f)
}

/** Content that rises into place when it replaces its skeleton. */
@Composable
fun RevealWhenLoaded(
    loaded: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val reducedEffects = LocalPerformanceProfile.current.reducedEffects
    AnimatedContent(
        targetState = loaded,
        modifier = modifier,
        transitionSpec = {
            if (reducedEffects) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(260)) + slideInVertically(
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                ) { height -> height / 10 }) togetherWith fadeOut(tween(140))
            }
        },
        label = "reveal"
    ) { ready ->
        if (ready) content() else skeleton()
    }
}

// -------------------------------------------------------------------------------------------
// Loading
// -------------------------------------------------------------------------------------------

/** 0..1, sweeping once every 1.4 s; null on the conservative profile (a still placeholder). */
@Composable
fun rememberSheenPhase(): State<Float>? {
    if (LocalPerformanceProfile.current.reducedEffects) return null
    return rememberInfiniteTransition(label = "sheen").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sheenPhase"
    )
}

/** A band of light travelling across a glass placeholder. Read in the draw phase only. */
fun sheenBrush(width: Float, phase: Float): Brush {
    val centre = width * (phase * 1.8f - 0.4f)
    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.06f)
        ),
        start = Offset(centre - width * 0.35f, 0f),
        end = Offset(centre + width * 0.35f, 0f)
    )
}

/** One rounded placeholder bar. */
@Composable
fun SkeletonBar(modifier: Modifier = Modifier, height: Dp = 14.dp, phase: State<Float>? = null) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind {
                drawRect(sheenBrush(size.width.coerceAtLeast(1f), phase?.value ?: 0.45f))
            }
    )
}

/** A glass card shaped like the content it stands in for: a title and [lines] rows. */
@Composable
fun SkeletonCard(label: String, modifier: Modifier = Modifier, lines: Int = 3) {
    val phase = rememberSheenPhase()
    GlassPanel(modifier = modifier.semantics { contentDescription = label }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBar(Modifier.fillMaxWidth(0.42f), height = 18.dp, phase = phase)
            repeat(lines) { index ->
                SkeletonBar(Modifier.fillMaxWidth(if (index % 2 == 0) 0.92f else 0.68f), phase = phase)
            }
        }
    }
}

/** GARBY's own loader: a LiDAR sweep going round a scope. */
@Composable
fun RadarLoader(label: String, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val reducedEffects = LocalPerformanceProfile.current.reducedEffects
    val angle = if (reducedEffects) null else rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "radarAngle"
    )
    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val stroke = 1.5.dp.toPx()
            drawCircle(GarbyColors.GlassStrokeFaint, radius)
            listOf(1f, 0.66f, 0.33f).forEach { ring ->
                drawCircle(GarbyColors.GlassStroke, radius * ring - stroke, style = Stroke(stroke))
            }
            rotate(angle?.value ?: 300f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to GarbyColors.EmberGlow.copy(alpha = 0.75f)
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    size = Size(radius * 2f, radius * 2f)
                )
                drawLine(
                    GarbyColors.EmberGlow, center, Offset(center.x + radius - stroke, center.y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                )
            }
            drawCircle(GarbyColors.TextPrimary, 3.dp.toPx())
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextMuted)
    }
}
