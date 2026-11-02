package com.sumonlogs.faceverify.harness

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.api.ActiveFaceModel
import com.sumonlogs.faceverify.core.api.AlignedPreview
import com.sumonlogs.faceverify.core.api.EnrolledFace
import com.sumonlogs.faceverify.core.api.FaceDebugInfo
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.api.StageTiming
import com.sumonlogs.faceverify.core.engine.FaceModels
import com.sumonlogs.faceverify.core.ui.components.FaceButton
import com.sumonlogs.faceverify.core.ui.components.FaceGlyph
import com.sumonlogs.faceverify.core.ui.components.FaceIcon
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography
import com.sumonlogs.faceverify.harness.telemetry.ConsentState
import com.sumonlogs.faceverify.harness.telemetry.Lighting
import com.sumonlogs.faceverify.harness.telemetry.TestScenario
import com.sumonlogs.faceverify.harness.telemetry.TesterSetup
import com.sumonlogs.faceverify.harness.telemetry.UploadStats
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
    val timings by diagnostics.stageTimings.collectAsStateWithLifecycle()
    val aligned by diagnostics.alignedFace.collectAsStateWithLifecycle()
    val bench by controller.bench.collectAsStateWithLifecycle()
    val setup by controller.setup.collectAsStateWithLifecycle()
    val consent by controller.consent.collectAsStateWithLifecycle()
    val uploads by controller.uploads.collectAsStateWithLifecycle()
    val installId by controller.installId.collectAsStateWithLifecycle()
    val cropConsent by controller.cropConsent.collectAsStateWithLifecycle()
    HomeScreen(
        HomeUi(
            faces = faces, active = active, busy = busy, lastCheck = lastCheck, stats = stats, debug = debug,
            timings = timings, aligned = aligned, bench = bench,
            sharingAvailable = controller.sharingAvailable, consent = consent, setup = setup,
            uploads = uploads, installId = installId, cropConsent = cropConsent,
        ),
        HomeActions(
            onEnroll = onEnroll,
            onVerify = controller::verify,
            onDelete = controller::delete,
            onSelectModel = controller::selectModel,
            onThreshold = controller::setThreshold,
            onLabel = controller::label,
            onClearCalibration = controller::clearCalibration,
            onBenchmark = controller::runBenchmark,
            onClearTimings = controller::clearTimings,
            onSetup = controller::updateSetup,
            onConsent = controller::setConsent,
            onCropConsent = controller::setCropConsent,
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
    val timings: List<StageTiming> = emptyList(),
    val aligned: AlignedPreview? = null,
    val bench: BenchState = BenchState.Idle,
    /** False when this build has no Firebase project: all the data-sharing UI is hidden. */
    val sharingAvailable: Boolean = false,
    val consent: ConsentState = ConsentState.Loading,
    val setup: TesterSetup = TesterSetup(),
    val uploads: UploadStats = UploadStats(),
    val installId: String? = null,
    /** Separate from [consent]: sending aligned face images, off until the tester turns it on. */
    val cropConsent: ConsentState = ConsentState.Declined,
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
    val onBenchmark: () -> Unit = {},
    val onClearTimings: () -> Unit = {},
    val onSetup: (TesterSetup) -> Unit = {},
    val onConsent: (Boolean) -> Unit = {},
    val onCropConsent: (Boolean) -> Unit = {},
)

@Composable
fun HomeScreen(ui: HomeUi, actions: HomeActions) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeId = ui.active?.model?.id
    val usable = ui.faces.orEmpty().filter { it.isUsableWith(activeId) }

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
        if (ui.sharingAvailable) {
            TestSetupCard(ui.setup, usable, actions)
            SharingCard(ui, actions)
        }
        FaceButton(stringResource(R.string.harness_enroll), actions.onEnroll, glyph = FaceGlyph.Person)
        FaceButton(
            stringResource(if (ui.busy) R.string.harness_verifying else R.string.harness_verify),
            actions.onVerify,
            enabled = !ui.busy && usable.isNotEmpty(),
        )
        ui.lastCheck?.let { ResultCard(it, usable, actions.onLabel) }
        if (ui.active != null) CalibrationCard(ui.active, ui.stats, actions)
        EnrolledList(ui.faces, activeId, onDeleteRequest = { pendingDeleteId = it.id })
        PerformanceCard(ui, actions)
        DebugPanel(ui.lastCheck?.result, ui.debug, ui.aligned)
    }

    if (ui.sharingAvailable && ui.consent == ConsentState.NotAsked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.harness_consent_title), style = FaceTypography.CardTitle) },
            text = { Text(stringResource(R.string.harness_consent_body), style = FaceTypography.Body) },
            confirmButton = {
                TextButton({ actions.onConsent(true) }) { Text(stringResource(R.string.harness_consent_agree), style = FaceTypography.BodySemiBold) }
            },
            dismissButton = {
                TextButton({ actions.onConsent(false) }) { Text(stringResource(R.string.harness_consent_decline), style = FaceTypography.BodySemiBold) }
            },
        )
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
            else -> faces.forEach { face ->
                EnrolledRow(face, usable = face.isUsableWith(activeModelId), sameModel = face.modelId == activeModelId, onDeleteRequest)
            }
        }
    }
}

@Composable
private fun EnrolledRow(face: EnrolledFace, usable: Boolean, sameModel: Boolean, onDeleteRequest: (EnrolledFace) -> Unit) {
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
                // Same model but not usable means an older face pipeline made it: enrol again.
                Text(
                    stringResource(if (sameModel) R.string.harness_old_pipeline else R.string.harness_other_model),
                    style = FaceTypography.CaptionSmall,
                    color = FaceColors.MismatchRed,
                )
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
private fun DebugPanel(result: FaceMatchResult?, debug: FaceDebugInfo, aligned: AlignedPreview?) {
    Card {
        Text(stringResource(R.string.harness_debug_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        DebugRow(R.string.harness_debug_model, debug.modelId?.let { FaceModels.byId(it)?.displayName ?: it })
        DebugRow(R.string.harness_debug_threshold, if (debug.modelId == null) null else fmt(debug.threshold))
        DebugRow(R.string.harness_debug_score, debug.lastScore?.let(::fmt))
        DebugRow(R.string.harness_debug_runner_up, debug.runnerUpScore?.let(::fmt))
        DebugRow(R.string.harness_debug_matched, debug.lastMatched?.toString())
        DebugRow(R.string.harness_debug_challenge, debug.livenessChallenge)
        DebugRow(R.string.harness_debug_liveness, debug.livenessPassed?.toString())
        DebugRow(R.string.harness_debug_spoof, debug.spoofScore?.let(::fmt))
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

        if (aligned != null) {
            val bitmap = remember(aligned) {
                Bitmap.createBitmap(aligned.pixels, aligned.size, aligned.size, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            Text(stringResource(R.string.harness_debug_aligned), style = FaceTypography.Debug, color = FaceColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.harness_debug_aligned),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(168.dp),
            )
        }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestSetupCard(setup: TesterSetup, usable: List<EnrolledFace>, actions: HomeActions) {
    Card {
        Text(stringResource(R.string.harness_setup_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        Text(stringResource(R.string.harness_setup_hint), style = FaceTypography.Body, color = FaceColors.TextMuted)
        ChipGroup(R.string.harness_setup_scenario) {
            TestScenario.entries.forEach { s -> Chip(stringResource(s.labelRes()), setup.scenario == s) { actions.onSetup(setup.copy(scenario = s)) } }
        }
        if (setup.scenario == TestScenario.Me && usable.size > 1) {
            ChipGroup(R.string.harness_setup_person) {
                usable.forEach { p -> Chip(p.name, setup.expectedFaceId == p.id) { actions.onSetup(setup.copy(expectedFaceId = p.id)) } }
            }
        }
        ChipGroup(R.string.harness_setup_lighting) {
            Lighting.entries.forEach { l -> Chip(stringResource(l.labelRes()), setup.lighting == l) { actions.onSetup(setup.copy(lighting = l)) } }
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.harness_setup_glasses), setup.glasses) { actions.onSetup(setup.copy(glasses = !setup.glasses)) }
            Chip(stringResource(R.string.harness_setup_covered), setup.faceCovered) { actions.onSetup(setup.copy(faceCovered = !setup.faceCovered)) }
        }
    }
}

@Composable
private fun SharingCard(ui: HomeUi, actions: HomeActions) {
    val on = ui.consent == ConsentState.Granted
    var confirmCrops by rememberSaveable { mutableStateOf(false) }
    Card {
        Text(stringResource(R.string.harness_share_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        Text(stringResource(R.string.harness_share_body), style = FaceTypography.Body, color = FaceColors.TextMuted)
        Row(Modifier.padding(top = 6.dp)) {
            Chip(stringResource(if (on) R.string.harness_share_on else R.string.harness_share_off), selected = on) { actions.onConsent(!on) }
        }
        if (on) {
            Text(
                stringResource(R.string.harness_share_status, ui.uploads.delivered, ui.uploads.waiting, ui.uploads.failed),
                style = FaceTypography.CaptionSmall,
                color = FaceColors.TextMuted,
            )
            ui.installId?.let {
                Text(stringResource(R.string.harness_share_id, it.take(8)), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
            }
            val crops = ui.cropConsent == ConsentState.Granted
            Text(
                stringResource(R.string.harness_crops_body),
                style = FaceTypography.Body,
                color = FaceColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 6.dp)) {
                Chip(stringResource(if (crops) R.string.harness_crops_on else R.string.harness_crops_off), selected = crops) {
                    // Turning it off is immediate; turning it on asks first, in plain words.
                    if (crops) actions.onCropConsent(false) else confirmCrops = true
                }
            }
        }
    }
    if (confirmCrops) {
        AlertDialog(
            onDismissRequest = { confirmCrops = false },
            title = { Text(stringResource(R.string.harness_crops_title), style = FaceTypography.CardTitle) },
            text = { Text(stringResource(R.string.harness_crops_confirm), style = FaceTypography.Body) },
            confirmButton = {
                TextButton({
                    actions.onCropConsent(true)
                    confirmCrops = false
                }) { Text(stringResource(R.string.harness_consent_agree), style = FaceTypography.BodySemiBold) }
            },
            dismissButton = {
                TextButton({ confirmCrops = false }) { Text(stringResource(R.string.harness_consent_decline), style = FaceTypography.BodySemiBold) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: Int, content: @Composable () -> Unit) {
    Text(stringResource(label), style = FaceTypography.LabelSemiBold, color = FaceColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun TestScenario.labelRes(): Int = when (this) {
    TestScenario.Me -> R.string.harness_scenario_me
    TestScenario.SomeoneElse -> R.string.harness_scenario_other
    TestScenario.PhotoAttack -> R.string.harness_scenario_photo
    TestScenario.ScreenReplay -> R.string.harness_scenario_replay
}

private fun Lighting.labelRes(): Int = when (this) {
    Lighting.Bright -> R.string.harness_light_bright
    Lighting.Normal -> R.string.harness_light_normal
    Lighting.Dim -> R.string.harness_light_dim
    Lighting.Backlit -> R.string.harness_light_backlit
    Lighting.Outdoor -> R.string.harness_light_outdoor
}

@Composable
private fun PerformanceCard(ui: HomeUi, actions: HomeActions) {
    Card {
        Text(stringResource(R.string.harness_perf_title), style = FaceTypography.CardTitle, color = FaceColors.Navy)
        if (ui.timings.isEmpty()) {
            Text(stringResource(R.string.harness_perf_empty), style = FaceTypography.Body, color = FaceColors.TextMuted)
        } else {
            Text(stringResource(R.string.harness_perf_header), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
            ui.timings.forEach { PerfRow(it.stage, "${it.count} · ${ms(it.avgMs)} · ${ms(it.p95Ms)}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(actions.onClearTimings, enabled = ui.timings.isNotEmpty()) {
                Text(stringResource(R.string.harness_perf_clear), style = FaceTypography.LabelSemiBold)
            }
            TextButton(actions.onBenchmark, enabled = !ui.busy && ui.bench != BenchState.Running && ui.active != null) {
                Text(
                    stringResource(if (ui.bench == BenchState.Running) R.string.harness_perf_running else R.string.harness_perf_bench),
                    style = FaceTypography.LabelSemiBold,
                )
            }
        }
        (ui.bench as? BenchState.Done)?.let { done ->
            Text(stringResource(R.string.harness_perf_bench_header), style = FaceTypography.CaptionSmall, color = FaceColors.TextMuted)
            done.rows.forEach { PerfRow("${it.label} · ${it.threads}T", "${ms(it.timing.avgMs)} · ${ms(it.timing.p95Ms)}") }
        }
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = FaceTypography.Debug, color = FaceColors.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = FaceTypography.Debug, color = FaceColors.TextPrimary)
    }
}

private fun ms(value: Float): String = String.format(Locale.US, "%.1f", value)

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
            active = ActiveFaceModel(FaceModels.SFace, FaceModels.SFace.defaultThreshold),
            busy = false,
            lastCheck = LastCheck(FaceMatchResult(true, alex, 0.71f, true, null, null), FaceModels.SFace.id, "1", 0.71f),
            stats = CalibrationStats(genuine = listOf(0.71f, 0.66f), impostor = listOf(0.21f, 0.33f)),
            debug = FaceDebugInfo(modelId = FaceModels.SFace.id, threshold = FaceModels.SFace.defaultThreshold, lastScore = 0.71f, runnerUpScore = 0.18f),
        ),
        HomeActions({}, {}, {}, {}, {}, {}, {}),
    )
}
