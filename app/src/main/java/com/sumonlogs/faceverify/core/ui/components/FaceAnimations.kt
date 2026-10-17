package com.sumonlogs.faceverify.core.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumonlogs.faceverify.core.ui.theme.FaceColors

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
