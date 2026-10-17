package com.sumonlogs.faceverify.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography

enum class FaceButtonStyle {
    /** #3A7FD0 with a blue glow. */
    Primary,

    /** Translucent white on the dark boards. */
    Ghost,

    /** White with navy text on the light boards. */
    Outline,
}

/** The boards' 54dp, radius-18 block button. */
@Composable
fun FaceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FaceButtonStyle = FaceButtonStyle.Primary,
    glyph: FaceGlyph? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    val (background, border, content) = when (style) {
        FaceButtonStyle.Primary -> Triple(FaceColors.ButtonBlue, FaceColors.white(0.22f), FaceColors.White)
        FaceButtonStyle.Ghost -> Triple(FaceColors.white(0.08f), FaceColors.white(0.18f), FaceColors.White)
        FaceButtonStyle.Outline -> Triple(FaceColors.White, FaceColors.OutlineButtonBorder, FaceColors.Navy)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .shadow(
                elevation = if (style == FaceButtonStyle.Primary && enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = FaceColors.ButtonBlue,
                spotColor = FaceColors.ButtonBlue,
            )
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) FaceIcon(glyph, null, 20.dp, 2.2f, content)
        Text(text, style = FaceTypography.Button, color = content)
    }
}
