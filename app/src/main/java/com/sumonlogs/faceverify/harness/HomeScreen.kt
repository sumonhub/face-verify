package com.sumonlogs.faceverify.harness

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.sumonlogs.faceverify.core.api.ActiveFaceModel
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDebugInfo
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.engine.FaceModels
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
    val active by controller.active.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val lastCheck by controller.lastCheck.collectAsStateWithLifecycle()
    val stats by controller.stats.collectAsStateWithLifecycle()
    val debug by diagnostics.info.collectAsStateWithLifecycle()
    HomeScreen(
        HomeUi(faces = faces, active = active, busy = busy, lastCheck = lastCheck, stats = stats, debug = debug),
        HomeActions(
            onEnroll = onEnroll,
            onVerify = controller::verify,
            onDelete = controller::delete,
            onSelectModel = controller::selectModel,
            onThreshold = controller::setThreshold,
            onLabel = controller::label,
            onClearCalibration = controller::clearCalibration,
        ),
    )
}

data class HomeUi(
    val faces: List<EnrolledFace>?,
    val active: ActiveFaceModel?,
    val busy: Boolean,
    val lastCheck: LastCheck?,
    val stats: CalibrationStats,
    val debug: FaceDebugInfo,
)

class HomeActions(
    val onEnroll: () -> Unit,
    val onVerify: () -> Unit,
    val onDelete: (EnrolledFace) -> Unit,
    val onSelectModel: (String) -> Unit,
    /** null resets to the model's default. */
    val onThreshold: (Float?) -> Unit,
    val onLabel: (CheckLabel) -> Unit,
    val onClearCalibration: () -> Unit,
)

@Composable
fun HomeScreen(ui: HomeUi, actions: HomeActions) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeId = ui.active?.model?.id
    val usable = ui.faces.orEmpty().filter { it.modelId == activeId }

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
        if (ui.active != null) ModelCard(ui.active, actions)
        FaceButton(stringResource(R.string.harness_enroll), actions.onEnroll, glyph = FaceGlyph.Person)
        FaceButton(
            stringResource(if (ui.busy) R.string.harness_verifying else R.string.harness_verify),
            actions.onVerify,
            enabled = !ui.busy && usable.isNotEmpty(),
        )
        ui.lastCheck?.let { ResultCard(it, usable, actions.onLabel) }
        if (ui.active != null) CalibrationCard(ui.active, ui.stats, actions)
        EnrolledList(ui.faces, activeId, onDeleteRequest = { pendingDeleteId = it.id })
        DebugPanel(ui.lastCheck?.result, ui.debug)
    }

    val pending = ui.faces?.firstOrNull { it.id == pendingDeleteId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.harness_delete_title, pending.name), style = FaceTypography.CardTitle) },
            text = { Text(stringResource(R.string.harness_delete_body), style = FaceTypography.Body) },
            confirmButton = {
                TextButton({
                    actions.onDelete(pending)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.harness_delete), style = FaceTypography.BodySemiBold, color = FaceColors.MismatchRed) }
            },
            dismissButton = {
                TextButton({ pendingDeleteId = null }) { Text(stringResource(R.string.harness_cancel), style = FaceTypography.BodySemiBold) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(active: ActiveFaceModel, actions: HomeActions) {
    // Local while dragging; committed when the finger lifts.
    var dragging by remember(active) { mutableFloatStateOf(active.threshold) }
    Card {
        Text(stringResource(R.string.harness_model_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FaceModels.all.forEach { model ->
                Chip(model.displayName, selected = model.id == active.model.id) { actions.onSelectModel(model.id) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.harness_threshold, fmt(dragging)),
                style = FaceTypography.BodySemiBold,
                color = FaceColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.harness_threshold_default, fmt(active.model.defaultThreshold)),
                style = FaceTypography.CaptionSmall,
                color = FaceColors.TextMuted,
            )
            if (!active.isDefaultThreshold) {
                TextButton({ actions.onThreshold(null) }) {
                    Text(stringResource(R.string.harness_reset_threshold), style = FaceTypography.LabelSemiBold)
                }
            }
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { actions.onThreshold(Math.round(dragging * 100) / 100f) },
            valueRange = 0.2f..0.9f,
            steps = 69,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(check: LastCheck, usable: List<EnrolledFace>, onLabel: (CheckLabel) -> Unit) {
    val result = check.result
    val face = result.face
    val failure = result.failure
    val text = when {
        result.matched && face != null -> stringResource(R.string.harness_result_matched, face.name, fmt(result.score))
        failure == null -> stringResource(R.string.harness_result_no_match, fmt(result.score))
        else -> stringResource(R.string.harness_result_failed, failure.name)
    }
    Card {
        Text(stringResource(R.string.harness_last_result), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        Text(text, style = FaceTypography.BodyStrong, color = if (result.matched) FaceColors.SuccessBadge else FaceColors.MismatchRed)
        when {
            check.labelled -> Text(stringResource(R.string.harness_label_saved), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
            check.labellable -> {
                Text(
                    stringResource(R.string.harness_label_question),
                    style = FaceTypography.LabelSemiBold,
                    color = FaceColors.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    usable.forEach { person -> Chip(person.name, selected = false) { onLabel(CheckLabel.Person(person.id)) } }
                    Chip(stringResource(R.string.harness_label_not_enrolled), selected = false) { onLabel(CheckLabel.NotEnrolled) }
                }
            }
        }
    }
}

@Composable
private fun CalibrationCard(active: ActiveFaceModel, stats: CalibrationStats, actions: HomeActions) {
    val threshold = active.threshold
    Card {
        Text(stringResource(R.string.harness_calib_title, active.model.displayName), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        if (stats.genuine.isEmpty() && stats.impostor.isEmpty()) {
            Text(stringResource(R.string.harness_calib_empty), style = FaceTypography.Body, color = FaceColors.TextMuted)
            return@Card
        }
        DebugRow(R.string.harness_calib_genuine, "${stats.genuine.size} · ${stats.genuine.minOrNull()?.let(::fmt) ?: "—"}")
        DebugRow(R.string.harness_calib_impostor, "${stats.impostor.size} · ${stats.impostor.maxOrNull()?.let(::fmt) ?: "—"}")
        DebugRow(R.string.harness_calib_far, "${stats.falseAccepts(threshold)}/${stats.impostor.size}")
        DebugRow(R.string.harness_calib_frr, "${stats.falseRejects(threshold)}/${stats.genuine.size}")
        if (stats.overlaps) {
            Text(stringResource(R.string.harness_calib_overlap), style = FaceTypography.CaptionSmall, color = FaceColors.MismatchRed)
        }
        val suggested = stats.suggestedThreshold
        if (suggested != null) {
            DebugRow(R.string.harness_calib_suggest, fmt(suggested))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggested != null) {
                TextButton({ actions.onThreshold(Math.round(suggested * 100) / 100f) }) {
                    Text(stringResource(R.string.harness_calib_use), style = FaceTypography.LabelSemiBold)
                }
            }
            TextButton(actions.onClearCalibration) {
                Text(stringResource(R.string.harness_calib_clear), style = FaceTypography.LabelSemiBold, color = FaceColors.MismatchRed)
            }
        }
    }
}

@Composable
private fun EnrolledList(faces: List<EnrolledFace>?, activeModelId: String?, onDeleteRequest: (EnrolledFace) -> Unit) {
    Card {
        Text(stringResource(R.string.harness_enrolled_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        when {
            faces == null -> Text(stringResource(R.string.harness_loading), style = FaceTypography.Body, color = FaceColors.TextMuted)
            faces.isEmpty() -> Text(stringResource(R.string.harness_enrolled_empty), style = FaceTypography.Body, color = FaceColors.TextMuted)
            else -> faces.forEach { face -> EnrolledRow(face, usable = face.modelId == activeModelId, onDeleteRequest) }
        }
    }
}

@Composable
private fun EnrolledRow(face: EnrolledFace, usable: Boolean, onDeleteRequest: (EnrolledFace) -> Unit) {
    val date = remember(face.enrolledAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(face.enrolledAtMillis))
    }
    val modelName = FaceModels.byId(face.modelId)?.displayName ?: face.modelId
    val deleteLabel = stringResource(R.string.harness_delete_title, face.name)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(face.name, style = FaceTypography.BodySemiBold, color = if (usable) FaceColors.TextPrimary else FaceColors.TextMuted)
            Text(stringResource(R.string.harness_enrolled_at, date, modelName), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
            if (!usable) {
                Text(stringResource(R.string.harness_other_model), style = FaceTypography.CaptionSmall, color = FaceColors.MismatchRed)
            }
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
        DebugRow(R.string.harness_debug_model, debug.modelId?.let { FaceModels.byId(it)?.displayName ?: it })
        DebugRow(R.string.harness_debug_threshold, if (debug.modelId == null) null else fmt(debug.threshold))
        DebugRow(R.string.harness_debug_score, debug.lastScore?.let(::fmt))
        DebugRow(R.string.harness_debug_runner_up, debug.runnerUpScore?.let(::fmt))
        DebugRow(R.string.harness_debug_matched, debug.lastMatched?.toString())
        DebugRow(R.string.harness_debug_challenge, debug.livenessChallenge)
        DebugRow(R.string.harness_debug_liveness, debug.livenessPassed?.toString())
        DebugRow(R.string.harness_debug_failure, debug.lastFailure?.name)
        DebugRow(R.string.harness_debug_quality, debug.qualityIssue)
        DebugRow(R.string.harness_debug_brightness, debug.cropBrightness?.let { String.format(Locale.US, "%.0f", it) })
        DebugRow(R.string.harness_debug_sharpness, debug.cropSharpness?.let { String.format(Locale.US, "%.0f", it) })
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
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = FaceTypography.LabelSemiBold,
        color = if (selected) FaceColors.White else FaceColors.Navy,
        modifier = Modifier
            .background(if (selected) FaceColors.ButtonBlue else FaceColors.BgSurface, shape)
            .border(1.dp, if (selected) FaceColors.ButtonBlue else FaceColors.BorderDefault, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
        Text(stringResource(label), style = FaceTypography.Debug, color = FaceColors.TextMuted, modifier = Modifier.weight(1f))
        Text(value ?: "—", style = FaceTypography.Debug, color = FaceColors.TextPrimary)
    }
}

private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun angles(debug: FaceDebugInfo): String? {
    val yaw = debug.yaw ?: return null
    return String.format(Locale.US, "%.0f° / %.0f° / %.0f°", yaw, debug.pitch ?: 0f, debug.roll ?: 0f)
}

private fun eyes(debug: FaceDebugInfo): String? {
    val left = debug.leftEyeOpen ?: return null
    return String.format(Locale.US, "%.2f / %.2f", left, debug.rightEyeOpen ?: 0f)
}

@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun HomeScreenPreview() {
    val alex = EnrolledFace("1", "Alex", 1_791_437_640_000L, FaceModels.SFace.id)
    HomeScreen(
        HomeUi(
            faces = listOf(alex, EnrolledFace("2", "Sam", 1_791_524_040_000L, FaceModels.MobileFaceNet.id)),
            active = ActiveFaceModel(FaceModels.SFace, 0.42f),
            busy = false,
            lastCheck = LastCheck(FaceMatchResult(true, alex, 0.71f, true, null, null), FaceModels.SFace.id, "1", 0.71f),
            stats = CalibrationStats(genuine = listOf(0.71f, 0.66f), impostor = listOf(0.21f, 0.33f)),
            debug = FaceDebugInfo(modelId = FaceModels.SFace.id, threshold = 0.42f, lastScore = 0.71f, runnerUpScore = 0.18f),
        ),
        HomeActions({}, {}, {}, {}, {}, {}, {}),
    )
}
