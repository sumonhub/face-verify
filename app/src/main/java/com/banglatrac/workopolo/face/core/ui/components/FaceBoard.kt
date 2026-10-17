package com.banglatrac.workopolo.face.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography

/**
 * Dark full-screen board used by face check and enrollment: #0B2445 with two faint rings,
 * content centred, padding 20/24/[bottomPadding]. Scrolls only if the screen is shorter than the content;
 * a `Spacer(Modifier.weight(1f))` in [content] pushes the footer down like CSS `margin-top: auto`.
 */
@Composable
fun DarkFaceBoard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(FaceColors.Night)) {
        DecorRings(Modifier.offset(x = (-15).dp, y = 90.dp))
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = bottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun DecorRings(modifier: Modifier = Modifier) {
    Canvas(modifier.size(420.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        drawCircle(FaceColors.white(0.04f), radius = 208.dp.toPx(), style = stroke)
        drawCircle(FaceColors.white(0.05f), radius = 170.dp.toPx(), style = stroke)
    }
}

/** Three rings in the top-right corner of the navy hero cards. */
@Composable
fun HeroRings(modifier: Modifier = Modifier) {
    Canvas(modifier.size(220.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        drawCircle(FaceColors.white(0.07f), radius = 108.dp.toPx(), style = stroke)
        drawCircle(FaceColors.white(0.08f), radius = 80.dp.toPx(), style = stroke)
        drawCircle(FaceColors.white(0.10f), radius = 52.dp.toPx(), style = stroke)
    }
}

/** Header of the dark boards: 40dp × button and a centred 17sp title. */
@Composable
fun DarkHeader(title: String, closeDescription: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SquareIconButton(
            glyph = FaceGlyph.Close,
            contentDescription = closeDescription,
            onClick = onClose,
            background = FaceColors.white(0.08f),
            border = FaceColors.white(0.16f),
            tint = FaceColors.White,
        )
        Text(
            text = title,
            style = FaceTypography.BarTitle,
            color = FaceColors.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(end = 52.dp),
        )
    }
}

/** Header of the light boards: white back button and a 20sp screen title. */
@Composable
fun LightHeader(title: String, backDescription: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SquareIconButton(
            glyph = FaceGlyph.Back,
            contentDescription = backDescription,
            onClick = onBack,
            background = FaceColors.BgSurface,
            border = FaceColors.BorderSubtle,
            tint = FaceColors.TextPrimary,
            elevation = 1.dp,
        )
        Text(text = title, style = FaceTypography.ScreenTitle, color = FaceColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SquareIconButton(
    glyph: FaceGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    background: Color,
    border: Color,
    tint: Color,
    elevation: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(40.dp)
            .shadow(elevation, shape)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaceIcon(glyph, contentDescription, 20.dp, 2f, tint)
    }
}
