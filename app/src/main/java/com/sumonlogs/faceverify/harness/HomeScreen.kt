package com.sumonlogs.faceverify.harness

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDebugInfo
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.ui.components.FaceButton
import com.sumonlogs.faceverify.core.ui.components.FaceGlyph
import com.sumonlogs.faceverify.core.ui.components.FaceIcon
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(
    controller: HarnessController,
    diagnostics: FaceDiagnostics,
    onEnroll: () -> Unit,
) {
    val faces by controller.faces.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val result by controller.lastResult.collectAsStateWithLifecycle()
    val debug by diagnostics.info.collectAsStateWithLifecycle()
    HomeScreen(
        faces = faces,
        busy = busy,
        result = result,
        debug = debug,
        onEnroll = onEnroll,
        onVerify = controller::verify,
        onDelete = controller::delete,
    )
}

@Composable
fun HomeScreen(
    faces: List<EnrolledFace>?,
    busy: Boolean,
    result: FaceMatchResult?,
    debug: FaceDebugInfo,
    onEnroll: () -> Unit,
    onVerify: () -> Unit,
    onDelete: (EnrolledFace) -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

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
        FaceButton(stringResource(R.string.harness_enroll), onEnroll, glyph = FaceGlyph.Person)
        FaceButton(
            stringResource(if (busy) R.string.harness_verifying else R.string.harness_verify),
            onVerify,
            enabled = !busy && !faces.isNullOrEmpty(),
        )
        if (result != null) ResultCard(result)
        EnrolledList(faces, onDeleteRequest = { pendingDeleteId = it.id })
        DebugPanel(result, debug)
    }

    val pending = faces?.firstOrNull { it.id == pendingDeleteId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.harness_delete_title, pending.name), style = FaceTypography.CardTitle) },
            text = { Text(stringResource(R.string.harness_delete_body), style = FaceTypography.Body) },
            confirmButton = {
                TextButton({
                    onDelete(pending)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.harness_delete), style = FaceTypography.BodySemiBold, color = FaceColors.MismatchRed) }
            },
            dismissButton = {
                TextButton({ pendingDeleteId = null }) { Text(stringResource(R.string.harness_cancel), style = FaceTypography.BodySemiBold) }
            },
        )
    }
}

@Composable
private fun ResultCard(result: FaceMatchResult) {
    val face = result.face
    val text = when {
        result.matched && face != null -> stringResource(R.string.harness_result_matched, face.name)
        result.failure == null -> stringResource(R.string.harness_result_no_match)
        else -> stringResource(R.string.harness_result_failed, result.failure.name)
    }
    Card {
        Text(stringResource(R.string.harness_last_result), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        Text(text, style = FaceTypography.BodyStrong, color = if (result.matched) FaceColors.SuccessBadge else FaceColors.MismatchRed)
    }
}

@Composable
private fun EnrolledList(faces: List<EnrolledFace>?, onDeleteRequest: (EnrolledFace) -> Unit) {
    Card {
        Text(stringResource(R.string.harness_enrolled_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        when {
            faces == null -> Text(stringResource(R.string.harness_loading), style = FaceTypography.Body, color = FaceColors.TextMuted)
            faces.isEmpty() -> Text(stringResource(R.string.harness_enrolled_empty), style = FaceTypography.Body, color = FaceColors.TextMuted)
            else -> faces.forEach { face -> EnrolledRow(face, onDeleteRequest) }
        }
    }
}

@Composable
private fun EnrolledRow(face: EnrolledFace, onDeleteRequest: (EnrolledFace) -> Unit) {
    val date = remember(face.enrolledAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(face.enrolledAtMillis))
    }
    val deleteLabel = stringResource(R.string.harness_delete_title, face.name)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(face.name, style = FaceTypography.BodySemiBold, color = FaceColors.TextPrimary)
            Text(stringResource(R.string.harness_enrolled_at, date), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
        }
        Box(
            Modifier
                .size(40.dp)
                .clickable(role = Role.Button) { onDeleteRequest(face) },
            contentAlignment = Alignment.Center,
        ) {
            FaceIcon(FaceGlyph.Trash, deleteLabel, 20.dp, 2f, FaceColors.MismatchRed)
        }
    }
}

@Composable
private fun DebugPanel(result: FaceMatchResult?, debug: FaceDebugInfo) {
    Card {
        Text(stringResource(R.string.harness_debug_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        DebugRow(R.string.harness_debug_threshold, fmt(debug.threshold))
        DebugRow(R.string.harness_debug_score, debug.lastScore?.let(::fmt))
        DebugRow(R.string.harness_debug_runner_up, debug.runnerUpScore?.let(::fmt))
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
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FaceColors.BgSurface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
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

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        faces = listOf(EnrolledFace("1", "Alex", 1_791_437_640_000L), EnrolledFace("2", "Sam", 1_791_524_040_000L)),
        busy = false,
        result = FaceMatchResult(true, EnrolledFace("1", "Alex", 0L), 0.78f, true, null, null),
        debug = FaceDebugInfo(threshold = 0.6f, lastScore = 0.78f, runnerUpScore = 0.31f, lastMatched = true, livenessChallenge = "Blink", livenessPassed = true),
        onEnroll = {}, onVerify = {}, onDelete = {},
    )
}
