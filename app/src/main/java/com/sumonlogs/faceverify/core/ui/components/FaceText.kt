package com.sumonlogs.faceverify.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography

/** The big centred white headline of the dark boards. */
@Composable
fun FaceHeadline(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Text(
        text = text,
        style = if (compact) FaceTypography.HeadlineCompact else FaceTypography.Headline,
        color = FaceColors.White,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** The 28×5 pill segments under the headline. */
@Composable
fun StepBar(filled: Int, total: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.face_step_of, filled.coerceAtMost(total), total)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(width = 28.dp, height = 5.dp)
                    .background(if (index < filled) FaceColors.Mint else FaceColors.white(0.18f), RoundedCornerShape(50)),
            )
        }
    }
}

/** Rounded status pill with an optional leading icon. */
@Composable
fun FacePill(
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    glyph: FaceGlyph? = null,
    glyphSize: Dp = 13.dp,
    glyphStroke: Float = 3f,
    horizontalPadding: Dp = 11.dp,
    verticalPadding: Dp = 4.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) FaceIcon(glyph, null, glyphSize, glyphStroke, content)
        leading?.invoke()
        Text(text, style = FaceTypography.Pill, color = content)
    }
}
