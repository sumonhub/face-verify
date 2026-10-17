package com.banglatrac.workopolo.face.harness

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banglatrac.workopolo.face.R
import com.banglatrac.workopolo.face.core.api.FaceDebugInfo
import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import com.banglatrac.workopolo.face.core.api.FaceMismatchSummary
import com.banglatrac.workopolo.face.core.ui.components.FaceButton
import com.banglatrac.workopolo.face.core.ui.components.FaceButtonStyle
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography
import java.util.Locale

@Composable
fun HomeRoute(
    controller: HarnessController,
    diagnostics: FaceDiagnostics,
    onEnroll: () -> Unit,
    onShowMismatch: () -> Unit,
) {
    val enrolled by controller.enrolled.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val result by controller.lastResult.collectAsStateWithLifecycle()
    val summary by controller.mismatchSummary.collectAsStateWithLifecycle()
    val debug by diagnostics.info.collectAsStateWithLifecycle()
    HomeScreen(
        enrolled = enrolled,
        busy = busy,
        result = result,
        summary = summary,
        debug = debug,
        onEnroll = onEnroll,
        onVerify = controller::verify,
        onReset = controller::reset,
        onShowMismatch = onShowMismatch,
        onAddPastMismatch = { controller.addPastMismatch(daysAgo = summary.recentMismatches.size + 1) },
    )
}

@Composable
fun HomeScreen(
    enrolled: Boolean?,
    busy: Boolean,
    result: FaceMatchResult?,
    summary: FaceMismatchSummary,
    debug: FaceDebugInfo,
    onEnroll: () -> Unit,
    onVerify: () -> Unit,
    onReset: () -> Unit,
    onShowMismatch: () -> Unit,
    onAddPastMismatch: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FaceColors.BgBase)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = FaceTypography.ScreenTitle, color = FaceColors.TextPrimary)
        Text(
            stringResource(
                when (enrolled) {
                    true -> R.string.harness_enrolled_yes
                    false -> R.string.harness_enrolled_no
                    null -> R.string.harness_enrolled_unknown
                },
            ),
            style = FaceTypography.Body,
            color = FaceColors.TextMuted,
        )
        FaceButton(stringResource(R.string.harness_enroll), onEnroll)
        FaceButton(stringResource(if (busy) R.string.harness_verifying else R.string.harness_verify), onVerify, enabled = !busy)
        FaceButton(stringResource(R.string.harness_reset), onReset, style = FaceButtonStyle.Outline)
        FaceButton(
            pluralStringResource(R.plurals.harness_show_mismatch, summary.windowDays, summary.mismatchDays, summary.windowDays),
            onShowMismatch,
            style = FaceButtonStyle.Outline,
        )
        FaceButton(stringResource(R.string.harness_add_past_mismatch), onAddPastMismatch, style = FaceButtonStyle.Outline)

        DebugPanel(result, debug)
    }
}

@Composable
private fun DebugPanel(result: FaceMatchResult?, debug: FaceDebugInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FaceColors.BgSurface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.harness_debug_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        DebugRow(R.string.harness_debug_threshold, fmt(debug.threshold))
        DebugRow(R.string.harness_debug_score, debug.lastScore?.let(::fmt))
        DebugRow(R.string.harness_debug_matched, debug.lastMatched?.toString())
        DebugRow(R.string.harness_debug_challenge, debug.livenessChallenge)
        DebugRow(R.string.harness_debug_liveness, debug.livenessPassed?.toString())
        DebugRow(R.string.harness_debug_failure, debug.lastFailure?.name)
        DebugRow(R.string.harness_debug_quality, debug.qualityIssue)
        DebugRow(R.string.harness_debug_angles, angles(debug))
        DebugRow(R.string.harness_debug_eyes, eyes(debug))
        DebugRow(R.string.harness_debug_detect, debug.detectMs?.let { "$it ms" })
        DebugRow(R.string.harness_debug_embed, debug.embedMs?.let { "$it ms" })
        DebugRow(R.string.harness_debug_total, debug.totalMs?.let { "$it ms" })
        DebugRow(R.string.harness_debug_selfie, result?.selfie?.let { "${it.size / 1024} KB" })

        val selfie = result?.selfie
        if (selfie != null) {
            val bitmap = remember(selfie) { BitmapFactory.decodeByteArray(selfie, 0, selfie.size)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.harness_debug_selfie),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DebugRow(label: Int, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = FaceTypography.Debug, color = FaceColors.TextMuted)
        Text(value ?: "—", style = FaceTypography.Debug, color = FaceColors.TextPrimary)
    }
}

private fun fmt(value: Float): String = String.format(Locale.US, "%.3f", value)

private fun angles(debug: FaceDebugInfo): String? {
    val yaw = debug.yaw ?: return null
    return String.format(Locale.US, "%.0f° / %.0f° / %.0f°", yaw, debug.pitch ?: 0f, debug.roll ?: 0f)
}

private fun eyes(debug: FaceDebugInfo): String? {
    val left = debug.leftEyeOpen ?: return null
    return String.format(Locale.US, "%.2f / %.2f", left, debug.rightEyeOpen ?: 0f)
}

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        enrolled = true,
        busy = false,
        result = null,
        summary = FaceMismatchSummary(1, 5, emptyList()),
        debug = FaceDebugInfo(threshold = 0.6f, lastScore = 0.72f, lastMatched = true, livenessChallenge = "Blink", livenessPassed = true),
        onEnroll = {}, onVerify = {}, onReset = {}, onShowMismatch = {}, onAddPastMismatch = {},
    )
}
