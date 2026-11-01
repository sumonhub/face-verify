package com.sumonlogs.faceverify.core.ui.enrollment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.engine.EnrollFailure
import com.sumonlogs.faceverify.core.engine.EnrollPose
import com.sumonlogs.faceverify.core.ui.camera.FaceCameraPreview
import com.sumonlogs.faceverify.core.ui.components.DarkFaceBoard
import com.sumonlogs.faceverify.core.ui.components.DarkHeader
import com.sumonlogs.faceverify.core.ui.components.DashedFaceOval
import com.sumonlogs.faceverify.core.ui.components.FaceButton
import com.sumonlogs.faceverify.core.ui.components.FaceGlyph
import com.sumonlogs.faceverify.core.ui.components.FaceHeadline
import com.sumonlogs.faceverify.core.ui.components.FaceIcon
import com.sumonlogs.faceverify.core.ui.components.FaceOval
import com.sumonlogs.faceverify.core.ui.components.FaceOvalPlaceholder
import com.sumonlogs.faceverify.core.ui.components.FacePill
import com.sumonlogs.faceverify.core.ui.components.LivePulseDot
import com.sumonlogs.faceverify.core.ui.components.MaxScreenBrightness
import com.sumonlogs.faceverify.core.ui.components.StepBar
import com.sumonlogs.faceverify.core.ui.components.hintRes
import com.sumonlogs.faceverify.core.ui.permission.CameraPermissionGate
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ---------------------------------------------------------------- Intro

/** Board "Face enrollment · intro", plus the name of the person being enrolled. */
@Composable
fun FaceEnrollIntroScreen(onStart: (name: String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_enroll_title), stringResource(R.string.face_cd_close), onClose)
        DashedFaceOval(140.dp, 180.dp, Modifier.padding(top = 28.dp))
        Column(
            Modifier.padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FaceHeadline(stringResource(R.string.face_enroll_intro_headline))
            Text(
                stringResource(R.string.face_enroll_intro_body),
                style = FaceTypography.BodyRelaxed,
                color = FaceColors.white(0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        NameField(name = name, onNameChange = { name = it.take(MAX_NAME) }, modifier = Modifier.padding(top = 16.dp))
        Column(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(FaceColors.white(0.05f), RoundedCornerShape(16.dp))
                .border(1.dp, FaceColors.white(0.10f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 2.dp),
        ) {
            TipRow(FaceGlyph.Sun, R.string.face_tip_light_title, R.string.face_tip_light_body)
            TipRow(FaceGlyph.Glasses, R.string.face_tip_face_title, R.string.face_tip_face_body)
            TipRow(FaceGlyph.Phone, R.string.face_tip_phone_title, R.string.face_tip_phone_body)
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FaceButton(
                stringResource(R.string.face_enroll_start),
                onClick = { onStart(name.trim()) },
                glyph = FaceGlyph.Person,
                enabled = name.isNotBlank(),
            )
            Text(
                stringResource(R.string.face_enroll_privacy),
                style = FaceTypography.Caption,
                color = FaceColors.white(0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MAX_NAME = 60

@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.face_enroll_name_label), style = FaceTypography.LabelSemiBold, color = FaceColors.white(0.7f))
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = FaceTypography.Body.copy(color = FaceColors.White),
            cursorBrush = SolidColor(FaceColors.Mint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .background(FaceColors.white(0.08f), shape)
                .border(1.dp, FaceColors.white(0.16f), shape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (name.isEmpty()) {
                        Text(stringResource(R.string.face_enroll_name_hint), style = FaceTypography.Body, color = FaceColors.white(0.45f))
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TipRow(glyph: FaceGlyph, title: Int, body: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(38.dp).background(FaceColors.white(0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { FaceIcon(glyph, null, 18.dp, 2f, FaceColors.Sky) }
        Column {
            Text(stringResource(title), style = FaceTypography.BodySemiBold, color = FaceColors.White)
            Text(stringResource(body), style = FaceTypography.CaptionSmall, color = FaceColors.white(0.6f))
        }
    }
}

// ---------------------------------------------------------------- Capture

@Composable
fun FaceEnrollCaptureRoute(
    name: String,
    onEnrolled: (name: String) -> Unit,
    onCancel: () -> Unit,
    onPermissionDismissed: () -> Unit,
    viewModel: EnrollmentCaptureViewModel = koinViewModel(parameters = { parametersOf(name) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnEnrolled by rememberUpdatedState(onEnrolled)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EnrollmentCaptureEffect.Enrolled -> currentOnEnrolled(effect.face.name)
            }
        }
    }
    CameraPermissionGate(title = stringResource(R.string.face_enroll_title), onDismiss = onPermissionDismissed) {
        MaxScreenBrightness()
        LaunchedEffect(Unit) { viewModel.onIntent(EnrollmentCaptureIntent.Start) }
        FaceEnrollCaptureScreen(
            state = state,
            onCancel = onCancel,
            onIntent = viewModel::onIntent,
        ) {
            key(state.attempt) {
                FaceCameraPreview(
                    onFrame = { viewModel.onIntent(EnrollmentCaptureIntent.FrameAnalyzed(it)) },
                    onError = { viewModel.onIntent(EnrollmentCaptureIntent.CameraFailed) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Board "Face enrollment · capture". */
@Composable
fun FaceEnrollCaptureScreen(
    state: EnrollmentCaptureState,
    onCancel: () -> Unit,
    onIntent: (EnrollmentCaptureIntent) -> Unit,
    camera: @Composable BoxScope.() -> Unit = {},
) {
    val failed = state.failure != null || state.cameraUnavailable || state.duplicateOf != null
    val headline = when {
        state.cameraUnavailable -> R.string.face_camera_unavailable
        state.duplicateOf != null -> R.string.face_enroll_duplicate_title
        state.failure != null -> R.string.face_enroll_failed
        state.saving -> R.string.face_enroll_saving
        else -> state.pose.instructionRes()
    }
    val duplicateOf = state.duplicateOf
    val sub = when {
        state.cameraUnavailable -> R.string.face_enroll_failed_error
        duplicateOf != null -> R.string.face_enroll_duplicate
        state.failure == EnrollFailure.Inconsistent -> R.string.face_enroll_failed_inconsistent
        state.failure == EnrollFailure.EngineError -> R.string.face_enroll_failed_error
        else -> state.issue?.hintRes() ?: R.string.face_sub_keep_in_oval
    }

    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_enroll_title), stringResource(R.string.face_cd_cancel_enrollment), onCancel)
        Box(Modifier.padding(top = 30.dp)) {
            FaceOval(
                width = 230.dp,
                height = 300.dp,
                progress = state.progress,
                ringColor = if (failed) FaceColors.MismatchRed else FaceColors.Mint,
            ) {
                FaceOvalPlaceholder(iconSize = 92.dp)
                if (!state.cameraUnavailable) camera()
            }
            if (!failed && !state.saving) {
                when (state.pose) {
                    EnrollPose.Left -> FaceIcon(
                        FaceGlyph.Back, null, 44.dp, 2.2f, FaceColors.Sky,
                        Modifier.align(Alignment.CenterStart).offset(x = (-54).dp),
                    )
                    EnrollPose.Right -> FaceIcon(
                        FaceGlyph.Forward, null, 44.dp, 2.2f, FaceColors.Sky,
                        Modifier.align(Alignment.CenterEnd).offset(x = 54.dp),
                    )
                    EnrollPose.Front -> Unit
                }
            }
        }
        Column(
            Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FaceHeadline(stringResource(headline), compact = true)
            Text(if (duplicateOf != null) stringResource(sub, duplicateOf) else stringResource(sub), style = FaceTypography.Body, color = FaceColors.white(0.7f), textAlign = TextAlign.Center)
            if (!failed) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnrollPose.entries.forEach { pose -> PoseChip(pose, state) }
                }
            }
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        if (failed) {
            FaceButton(stringResource(R.string.face_try_again), { onIntent(EnrollmentCaptureIntent.Retry) })
        } else {
            Text(
                text = stringResource(R.string.face_enroll_help),
                style = FaceTypography.BodySemiBold,
                color = FaceColors.white(0.75f),
                modifier = Modifier
                    .height(40.dp)
                    .clickable(role = Role.Button) { onIntent(EnrollmentCaptureIntent.ShowHelp) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }

    if (state.showHelp) {
        AlertDialog(
            onDismissRequest = { onIntent(EnrollmentCaptureIntent.HideHelp) },
            confirmButton = {
                TextButton({ onIntent(EnrollmentCaptureIntent.HideHelp) }) { Text(stringResource(R.string.face_ok), style = FaceTypography.BodySemiBold) }
            },
            title = { Text(stringResource(R.string.face_enroll_help_title), style = FaceTypography.CardTitle) },
            text = { Text(stringResource(R.string.face_enroll_help_body), style = FaceTypography.Body) },
        )
    }
}

@Composable
private fun PoseChip(pose: EnrollPose, state: EnrollmentCaptureState) {
    val label = stringResource(pose.labelRes())
    when {
        pose in state.captured -> FacePill(
            text = label, background = FaceColors.Mint.copy(alpha = 0.16f), content = FaceColors.Mint,
            glyph = FaceGlyph.Check, glyphSize = 12.dp, verticalPadding = 5.dp,
        )
        pose == state.pose -> FacePill(
            text = label, background = FaceColors.White, content = FaceColors.Navy, verticalPadding = 5.dp,
            leading = { LivePulseDot(7.dp, FaceColors.ButtonBlue) },
        )
        else -> FacePill(text = label, background = FaceColors.white(0.08f), content = FaceColors.white(0.6f), verticalPadding = 5.dp)
    }
}

private fun EnrollPose.instructionRes(): Int = when (this) {
    EnrollPose.Front -> R.string.face_enroll_pose_front
    EnrollPose.Left -> R.string.face_enroll_pose_left
    EnrollPose.Right -> R.string.face_enroll_pose_right
}

private fun EnrollPose.labelRes(): Int = when (this) {
    EnrollPose.Front -> R.string.face_pose_front
    EnrollPose.Left -> R.string.face_pose_left
    EnrollPose.Right -> R.string.face_pose_right
}

// ---------------------------------------------------------------- Done

/** Board "Face enrollment · done". */
@Composable
fun FaceEnrollDoneScreen(name: String, onDone: () -> Unit) {
    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_enroll_title), stringResource(R.string.face_cd_close), onDone)
        FaceOval(230.dp, 300.dp, progress = 1f, ringColor = FaceColors.Mint, modifier = Modifier.padding(top = 40.dp)) {
            FaceOvalPlaceholder(iconSize = 92.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 40.dp)
                    .size(52.dp)
                    .background(FaceColors.SuccessBadge, CircleShape)
                    .border(4.dp, FaceColors.Navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) { FaceIcon(FaceGlyph.Check, null, 24.dp, 3f, FaceColors.White) }
        }
        Column(
            Modifier.padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FaceHeadline(stringResource(R.string.face_enroll_done_named, name))
            Text(
                stringResource(R.string.face_enroll_done_body),
                style = FaceTypography.BodyRelaxed,
                color = FaceColors.white(0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            StepBar(filled = 4, total = 4, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        FaceButton(stringResource(R.string.face_done), onDone)
    }
}

// ---------------------------------------------------------------- Previews

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollIntroPreview() {
    FaceEnrollIntroScreen(onStart = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollCapturePreview() {
    FaceEnrollCaptureScreen(
        EnrollmentCaptureState(pose = EnrollPose.Left, captured = setOf(EnrollPose.Front)),
        onCancel = {},
        onIntent = {},
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollCaptureFailedPreview() {
    FaceEnrollCaptureScreen(EnrollmentCaptureState(failure = EnrollFailure.Inconsistent), onCancel = {}, onIntent = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollCaptureDuplicatePreview() {
    FaceEnrollCaptureScreen(EnrollmentCaptureState(duplicateOf = "Alex"), onCancel = {}, onIntent = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollDonePreview() {
    FaceEnrollDoneScreen(name = "Alex", onDone = {})
}
