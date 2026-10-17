package com.banglatrac.workopolo.face.core.ui.enrollment

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banglatrac.workopolo.face.R
import com.banglatrac.workopolo.face.core.engine.EnrollFailure
import com.banglatrac.workopolo.face.core.engine.EnrollPose
import com.banglatrac.workopolo.face.core.ui.camera.FaceCameraPreview
import com.banglatrac.workopolo.face.core.ui.components.AiText
import com.banglatrac.workopolo.face.core.ui.components.DarkFaceBoard
import com.banglatrac.workopolo.face.core.ui.components.DarkHeader
import com.banglatrac.workopolo.face.core.ui.components.DashedFaceOval
import com.banglatrac.workopolo.face.core.ui.components.FaceButton
import com.banglatrac.workopolo.face.core.ui.components.FaceGlyph
import com.banglatrac.workopolo.face.core.ui.components.FaceIcon
import com.banglatrac.workopolo.face.core.ui.components.FaceOval
import com.banglatrac.workopolo.face.core.ui.components.FaceOvalPlaceholder
import com.banglatrac.workopolo.face.core.ui.components.FacePill
import com.banglatrac.workopolo.face.core.ui.components.LivePulseDot
import com.banglatrac.workopolo.face.core.ui.components.StepBar
import com.banglatrac.workopolo.face.core.ui.components.hintRes
import com.banglatrac.workopolo.face.core.ui.permission.CameraPermissionGate
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography
import org.koin.androidx.compose.koinViewModel

// ---------------------------------------------------------------- Intro

/** Board "Face enrollment · intro". */
@Composable
fun FaceEnrollIntroScreen(isReenrollment: Boolean, onStart: () -> Unit, onClose: () -> Unit) {
    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_enroll_title), stringResource(R.string.face_cd_close), onClose)
        DashedFaceOval(140.dp, 180.dp, Modifier.padding(top = 28.dp))
        Column(
            Modifier.padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isReenrollment) {
                FacePill(
                    text = stringResource(R.string.face_enroll_approved),
                    background = FaceColors.Mint.copy(alpha = 0.14f),
                    content = FaceColors.Mint,
                    glyph = FaceGlyph.Check,
                )
            }
            AiText(
                AnnotatedString(stringResource(R.string.face_enroll_intro_headline)),
                FaceTypography.Headline,
                FaceColors.White,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (isReenrollment) R.string.face_enroll_intro_body_reenroll else R.string.face_enroll_intro_body),
                style = FaceTypography.BodyRelaxed,
                color = FaceColors.white(0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
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
            FaceButton(stringResource(R.string.face_enroll_start), onStart, glyph = FaceGlyph.Person)
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
    onEnrolled: () -> Unit,
    onCancel: () -> Unit,
    onPermissionDismissed: () -> Unit,
    viewModel: EnrollmentCaptureViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnEnrolled by rememberUpdatedState(onEnrolled)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EnrollmentCaptureEffect.Enrolled -> currentOnEnrolled()
            }
        }
    }
    CameraPermissionGate(title = stringResource(R.string.face_enroll_title), onDismiss = onPermissionDismissed) {
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
    val failed = state.failure != null || state.cameraUnavailable
    val headline = when {
        state.cameraUnavailable -> R.string.face_camera_unavailable
        state.failure != null -> R.string.face_enroll_failed
        state.saving -> R.string.face_enroll_saving
        else -> state.pose.instructionRes()
    }
    val sub = when {
        state.cameraUnavailable -> R.string.face_enroll_failed_error
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
                if (!failed && !state.saving && state.pose == EnrollPose.Up) {
                    FaceIcon(FaceGlyph.Up, null, 44.dp, 2.2f, FaceColors.Sky, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
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
                    EnrollPose.Front, EnrollPose.Up -> Unit
                }
            }
        }
        Column(
            Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiText(AnnotatedString(stringResource(headline)), FaceTypography.HeadlineCompact, FaceColors.White, textAlign = TextAlign.Center)
            Text(stringResource(sub), style = FaceTypography.Body, color = FaceColors.white(0.7f), textAlign = TextAlign.Center)
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
    EnrollPose.Up -> R.string.face_enroll_pose_up
}

private fun EnrollPose.labelRes(): Int = when (this) {
    EnrollPose.Front -> R.string.face_pose_front
    EnrollPose.Left -> R.string.face_pose_left
    EnrollPose.Right -> R.string.face_pose_right
    EnrollPose.Up -> R.string.face_pose_up
}

// ---------------------------------------------------------------- Done

/** Board "Face enrollment · done". */
@Composable
fun FaceEnrollDoneScreen(userName: String?, onContinue: () -> Unit) {
    DarkFaceBoard {
        DarkHeader(stringResource(R.string.face_enroll_title), stringResource(R.string.face_cd_close), onContinue)
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
            val title = if (userName.isNullOrBlank()) {
                stringResource(R.string.face_enroll_done)
            } else {
                stringResource(R.string.face_enroll_done_named, userName)
            }
            AiText(AnnotatedString(title), FaceTypography.Headline, FaceColors.White, textAlign = TextAlign.Center)
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
        FaceButton(stringResource(R.string.face_continue_attendance), onContinue)
    }
}

// ---------------------------------------------------------------- Previews

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceEnrollIntroPreview() {
    FaceEnrollIntroScreen(isReenrollment = true, onStart = {}, onClose = {})
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
private fun FaceEnrollDonePreview() {
    FaceEnrollDoneScreen(userName = "Mazba", onContinue = {})
}
