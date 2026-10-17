package com.banglatrac.workopolo.face.core.ui.facecheck

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banglatrac.workopolo.face.R
import com.banglatrac.workopolo.face.core.api.FaceMatchResult
import com.banglatrac.workopolo.face.core.ui.camera.FaceCameraPreview
import com.banglatrac.workopolo.face.core.ui.components.AiText
import com.banglatrac.workopolo.face.core.ui.components.DarkFaceBoard
import com.banglatrac.workopolo.face.core.ui.components.DarkHeader
import com.banglatrac.workopolo.face.core.ui.components.FaceOval
import com.banglatrac.workopolo.face.core.ui.components.FaceOvalPlaceholder
import com.banglatrac.workopolo.face.core.ui.components.StepBar
import com.banglatrac.workopolo.face.core.ui.permission.CameraPermissionGate
import com.banglatrac.workopolo.face.core.ui.theme.FaceColors
import com.banglatrac.workopolo.face.core.ui.theme.FaceTypography
import org.koin.androidx.compose.koinViewModel

@Composable
fun FaceCheckRoute(
    onResult: (FaceMatchResult) -> Unit,
    viewModel: FaceCheckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FaceCheckEffect.Finish -> currentOnResult(effect.result)
            }
        }
    }
    CameraPermissionGate(
        title = stringResource(R.string.face_check_title),
        onDismiss = { viewModel.onIntent(FaceCheckIntent.PermissionDenied) },
    ) {
        LaunchedEffect(Unit) { viewModel.onIntent(FaceCheckIntent.Start) }
        FaceCheckScreen(state = state, onClose = { viewModel.onIntent(FaceCheckIntent.Cancel) }) {
            FaceCameraPreview(
                onFrame = { viewModel.onIntent(FaceCheckIntent.FrameAnalyzed(it)) },
                onError = { viewModel.onIntent(FaceCheckIntent.CameraFailed) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun FaceCheckScreen(
    state: FaceCheckState,
    onClose: () -> Unit,
    camera: @Composable BoxScope.() -> Unit = {},
) {
    val (headline, sub) = state.prompt.texts()
    DarkFaceBoard(bottomPadding = 32.dp) {
        DarkHeader(
            title = stringResource(R.string.face_check_title),
            closeDescription = stringResource(R.string.face_cd_cancel_check),
            onClose = onClose,
        )
        FaceOval(
            width = 250.dp,
            height = 330.dp,
            progress = state.ringProgress,
            ringColor = if (state.ringTone == RingTone.Failure) FaceColors.MismatchRed else FaceColors.Mint,
            overlay = if (state.ringTone == RingTone.Failure) FaceColors.MismatchTint else null,
            modifier = Modifier.padding(top = 52.dp),
        ) {
            FaceOvalPlaceholder(iconSize = 96.dp)
            camera()
        }
        Column(
            Modifier.padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiText(
                text = AnnotatedString(stringResource(headline)),
                style = FaceTypography.Headline,
                color = FaceColors.White,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(sub), style = FaceTypography.Body, color = FaceColors.white(0.7f), textAlign = TextAlign.Center)
            StepBar(filled = state.step, total = state.totalSteps, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        Text(
            text = stringResource(R.string.face_privacy_evidence),
            style = FaceTypography.Caption,
            color = FaceColors.white(0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(FaceColors.white(0.06f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private fun FacePrompt.texts(): Pair<Int, Int> {
    @StringRes val keepInOval = R.string.face_sub_keep_in_oval
    @StringRes val approval = R.string.face_result_approval_sub
    return when (this) {
        FacePrompt.Look -> R.string.face_hint_look to keepInOval
        FacePrompt.NoFace -> R.string.face_hint_no_face to keepInOval
        FacePrompt.MultipleFaces -> R.string.face_hint_multiple to keepInOval
        FacePrompt.TooFar -> R.string.face_hint_too_far to keepInOval
        FacePrompt.TooClose -> R.string.face_hint_too_close to keepInOval
        FacePrompt.NotCentred -> R.string.face_hint_not_centred to keepInOval
        FacePrompt.TurnedAway -> R.string.face_hint_turned to keepInOval
        FacePrompt.Tilted -> R.string.face_hint_tilted to keepInOval
        FacePrompt.EyesClosed -> R.string.face_hint_eyes to keepInOval
        FacePrompt.Blink -> R.string.face_challenge_blink to R.string.face_sub_checking_live
        FacePrompt.TurnLeft -> R.string.face_challenge_left to R.string.face_sub_checking_live
        FacePrompt.TurnRight -> R.string.face_challenge_right to R.string.face_sub_checking_live
        FacePrompt.HoldStill -> R.string.face_capture_title to R.string.face_capture_sub
        FacePrompt.Matched -> R.string.face_result_matched to R.string.face_result_matched_sub
        FacePrompt.Mismatch -> R.string.face_result_mismatch to approval
        FacePrompt.LivenessFailed -> R.string.face_result_liveness to approval
        FacePrompt.NoFaceFailed -> R.string.face_result_no_face to approval
        FacePrompt.Failed -> R.string.face_result_failed to approval
        FacePrompt.CameraUnavailable -> R.string.face_camera_unavailable to approval
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceCheckBlinkPreview() {
    FaceCheckScreen(FaceCheckState(step = 2, prompt = FacePrompt.Blink, ringProgress = 0.66f), onClose = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FaceCheckMismatchPreview() {
    FaceCheckScreen(
        FaceCheckState(step = 3, prompt = FacePrompt.Mismatch, ringProgress = 1f, ringTone = RingTone.Failure, finished = true),
        onClose = {},
    )
}
