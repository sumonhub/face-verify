package com.banglatrac.workopolo.face.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import kotlin.math.max

val OvalShape = GenericShape { size, _ -> addOval(Rect(Offset.Zero, size)) }

/** Design default: 300 ms for ring changes (the mock is static; the duration is ours). */
private val RingSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * The face oval: a 6dp ring drawn like CSS `conic-gradient(ring 0 p%, track p% 100%)` starting at
 * 12 o'clock, around a navy oval that holds the camera preview (or the placeholder person).
 */
@Composable
fun FaceOval(
    width: Dp,
    height: Dp,
    progress: Float,
    ringColor: Color,
    modifier: Modifier = Modifier,
    overlay: Color? = null,
    content: @Composable BoxScope.() -> Unit = { FaceOvalPlaceholder(iconSize = 92.dp) },
) {
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), RingSpec, label = "ovalProgress")
    val ring by animateColorAsState(ringColor, label = "ovalRing")
    val track = FaceColors.white(0.12f)
    Box(
        modifier
            .size(width, height)
            .drawBehind {
                val oval = Path().apply { addOval(Rect(Offset.Zero, size)) }
                val side = max(size.width, size.height)
                clipPath(oval) {
                    rotate(-90f) {
                        drawRect(
                            brush = Brush.sweepGradient(
                                0f to ring, sweep to ring, sweep to track, 1f to track,
                                center = center,
                            ),
                            topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                            size = Size(side, side),
                        )
                    }
                }
            }
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(OvalShape)
                .background(FaceColors.Navy),
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (overlay != null) {
            Box(Modifier.fillMaxSize().clip(OvalShape).background(overlay))
        }
    }
}

@Composable
fun BoxScope.FaceOvalPlaceholder(iconSize: Dp) {
    FaceIcon(
        glyph = FaceGlyph.Person,
        contentDescription = null,
        size = iconSize,
        strokeWidth = 1.2f,
        tint = FaceColors.white(0.28f),
        modifier = Modifier.align(Alignment.Center),
    )
}

/** Enrollment intro: a dashed oval outline, no ring. */
@Composable
fun DashedFaceOval(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width, height)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawOval(
                    color = FaceColors.Sky.copy(alpha = 0.6f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        FaceIcon(FaceGlyph.Person, null, 72.dp, 1.2f, FaceColors.Sky.copy(alpha = 0.7f))
    }
}

@Preview
@Composable
private fun FaceOvalPreview() {
    Box(Modifier.background(FaceColors.Night).padding(16.dp)) {
        FaceOval(width = 250.dp, height = 330.dp, progress = 0.66f, ringColor = FaceColors.Mint)
    }
}

@Preview
@Composable
private fun FaceOvalMismatchPreview() {
    Box(Modifier.background(FaceColors.Night).padding(16.dp)) {
        FaceOval(230.dp, 300.dp, 1f, FaceColors.MismatchRed, overlay = FaceColors.MismatchTint)
    }
}
