package com.banglatrac.workopolo.face.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglatrac.workopolo.face.R
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography

private const val AI_LOGO = "aiLogo"
private const val AI_CARET = "aiCaret"

/**
 * Text that starts with the inline "Workopolo AI" logo (26–28px, 8px gap), optionally ending with the
 * blinking AI caret, exactly like the boards' `<img> text <span class="ai-caret">`.
 */
@Composable
fun AiText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    logoSize: TextUnit = 26.sp,
    caretHeight: TextUnit? = null,
    textAlign: TextAlign? = null,
) {
    val density = LocalDensity.current
    val logoDp = with(density) { logoSize.toDp() }
    val logoDescription = stringResource(R.string.face_ai_logo)
    val content = buildAnnotatedString {
        appendInlineContent(AI_LOGO, "[AI]")
        append(text)
        if (caretHeight != null) appendInlineContent(AI_CARET, "|")
    }
    val inline = buildMap {
        put(
            AI_LOGO,
            InlineTextContent(Placeholder(logoSize * 1.31f, logoSize, PlaceholderVerticalAlign.TextCenter)) {
                Image(
                    painter = painterResource(R.drawable.ic_workopolo_ai),
                    contentDescription = logoDescription,
                    modifier = Modifier.padding(end = logoDp * 0.31f).size(logoDp),
                )
            },
        )
        if (caretHeight != null) {
            val caretDp = with(density) { caretHeight.toDp() }
            put(
                AI_CARET,
                InlineTextContent(Placeholder(5.sp, caretHeight, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { AiCaret(caretDp) }
                },
            )
        }
    }
    Text(text = content, style = style, color = color, inlineContent = inline, textAlign = textAlign, modifier = modifier)
}

/** [template] with %1$s filled by [highlight] in bold [highlightColor]. */
fun highlighted(template: String, highlight: String, highlightColor: Color): AnnotatedString {
    val marker = "%1\$s"
    val at = template.indexOf(marker)
    return buildAnnotatedString {
        if (at < 0) {
            append(template)
        } else {
            append(template.substring(0, at))
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) { append(highlight) }
            append(template.substring(at + marker.length))
        }
    }
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
