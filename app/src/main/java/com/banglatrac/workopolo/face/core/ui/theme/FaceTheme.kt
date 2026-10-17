package com.banglatrac.workopolo.face.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FaceColorScheme = lightColorScheme(
    primary = FaceColors.ButtonBlue,
    onPrimary = FaceColors.White,
    background = FaceColors.BgBase,
    onBackground = FaceColors.TextPrimary,
    surface = FaceColors.BgSurface,
    onSurface = FaceColors.TextPrimary,
    outline = FaceColors.BorderDefault,
)

@Composable
fun FaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FaceColorScheme, content = content)
}
