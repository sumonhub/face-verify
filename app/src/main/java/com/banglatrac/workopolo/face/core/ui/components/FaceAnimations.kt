package com.banglatrac.workopolo.face.core.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors

/**
 * CSS `.ai-caret { animation: aiBlink 1.1s steps(1) infinite }`,
 * `@keyframes aiBlink { 0%,45% {opacity:1} 50%,100% {opacity:0} }`: shown for 550 ms, hidden for 550 ms.
 */
@Composable
fun AiCaret(height: Dp, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "aiBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1100
                1f at 0 using LinearEasing
                1f at 549 using LinearEasing
                0f at 550 using LinearEasing
                0f at 1100
            },
        ),
        label = "aiBlinkAlpha",
    )
    Box(
        modifier
            .size(width = 2.dp, height = height)
            .alpha(alpha)
            .background(FaceColors.Caret, RoundedCornerShape(1.dp)),
    )
}

/**
 * CSS `.live-dot { animation: livePulse 1.8s ease-out infinite }`: a mint ring
 * (rgba(110,231,183,.6)) spreads from 0 to 7px and fades out by 70%, then rests until 100%.
 */
@Composable
fun LivePulseDot(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "livePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1800
                0f at 0 using EaseOut
                1f at 1260 using LinearEasing
                1f at 1800
            },
        ),
        label = "livePulseProgress",
    )
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    color = FaceColors.Mint.copy(alpha = 0.6f * (1f - t)),
                    radius = this.size.minDimension / 2f + 7.dp.toPx() * t,
                )
            }
            .background(color, CircleShape),
    )
}
