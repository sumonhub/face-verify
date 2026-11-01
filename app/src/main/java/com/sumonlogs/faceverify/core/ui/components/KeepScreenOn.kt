package com.sumonlogs.faceverify.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Stops the screen from dimming or turning off while this is in the composition, and gives the
 * previous setting back afterwards. Capture takes a while and no touches happen during it, so the
 * normal screen timeout would otherwise end the camera in the middle of a check.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}
