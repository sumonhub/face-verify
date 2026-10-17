package com.banglatrac.workopolo.face.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography tokens. The first block mirrors the Workopolo tokens.json classes; the second
 * names the extra sizes the face boards use, so screens never declare inline text sizes.
 */
object FaceTypography {
    // Workopolo tokens
    val ScreenTitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val CardTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val Meta = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

    // Face boards
    val BarTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Headline = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val HeadlineCompact = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
    val AiMessage = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    val AiMessageCompact = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    val Button = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val CardHeading = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val BodyRelaxed = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val BodySemiBold = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val MetaRelaxed = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
    val ListItem = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    val CaptionSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val LabelSemiBold = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val Pill = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val DayName = TextStyle(fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)
    val DayNumber = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val Debug = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}
