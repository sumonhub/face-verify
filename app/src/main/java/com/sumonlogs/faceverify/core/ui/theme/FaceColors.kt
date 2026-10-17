package com.sumonlogs.faceverify.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Colour tokens from the design (tokens.json + face boards). */
object FaceColors {
    // Design tokens
    val BgBase = Color(0xFFF3F2F7)
    val BgSurface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF141925)
    val TextMuted = Color(0xFF5C6B80)
    val BorderDefault = Color(0xFFD9DDE5)
    val BorderSubtle = Color(0xFFEEF0F4)
    val AccentStrong = Color(0xFF123A6B)

    // Face boards
    val Night = Color(0xFF0B2445)
    val Navy = Color(0xFF123A6B)
    val ButtonBlue = Color(0xFF3A7FD0)
    val Mint = Color(0xFF6EE7B7)
    val MismatchRed = Color(0xFFF87171)
    val MismatchTint = Color(0x1FD92D20)
    val Sky = Color(0xFF9CC4F0)
    val SuccessBadge = Color(0xFF16A36A)
    val OutlineButtonBorder = Color(0xFFC9D6E8)
    val White = Color(0xFFFFFFFF)

    /** White at the given opacity, as used throughout the dark boards (rgba(255,255,255,a)). */
    fun white(alpha: Float): Color = White.copy(alpha = alpha)
}
