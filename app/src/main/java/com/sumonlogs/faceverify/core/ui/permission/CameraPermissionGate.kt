package com.sumonlogs.faceverify.core.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sumonlogs.faceverify.R
import com.sumonlogs.faceverify.core.ui.components.DarkFaceBoard
import com.sumonlogs.faceverify.core.ui.components.DarkHeader
import com.sumonlogs.faceverify.core.ui.components.FaceButton
import com.sumonlogs.faceverify.core.ui.components.FaceButtonStyle
import com.sumonlogs.faceverify.core.ui.components.FaceGlyph
import com.sumonlogs.faceverify.core.ui.components.FaceHeadline
import com.sumonlogs.faceverify.core.ui.components.FaceIcon
import com.sumonlogs.faceverify.core.ui.components.FaceOval
import com.sumonlogs.faceverify.core.ui.theme.FaceColors
import com.sumonlogs.faceverify.core.ui.theme.FaceTypography

/**
 * Shows [content] only once the camera permission is granted. Before that it shows a rationale
 * screen; after a "don't ask again" denial it offers to open the app's settings instead.
 */
@Composable
fun CameraPermissionGate(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(context.hasCameraPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        val activity = context.findActivity()
        permanentlyDenied = !ok && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasCameraPermission()
        if (granted) permanentlyDenied = false
    }

    if (granted) {
        content()
    } else {
        CameraPermissionScreen(
            title = title,
            permanentlyDenied = permanentlyDenied,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun CameraPermissionScreen(
    title: String,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    DarkFaceBoard {
        DarkHeader(title = title, closeDescription = stringResource(R.string.face_cd_close), onClose = onDismiss)
        FaceOval(
            width = 140.dp,
            height = 180.dp,
            progress = 0f,
            ringColor = FaceColors.Mint,
            modifier = Modifier.padding(top = 28.dp),
        ) {
            FaceIcon(FaceGlyph.Camera, null, 64.dp, 1.4f, FaceColors.Sky.copy(alpha = 0.8f))
        }
        Column(
            Modifier.padding(top = 22.dp).widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FaceHeadline(
                stringResource(if (permanentlyDenied) R.string.face_perm_denied_headline else R.string.face_perm_headline),
            )
            Text(
                text = stringResource(if (permanentlyDenied) R.string.face_perm_denied_body else R.string.face_perm_body),
                style = FaceTypography.BodyRelaxed,
                color = FaceColors.white(0.72f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (permanentlyDenied) {
                FaceButton(stringResource(R.string.face_perm_open_settings), onOpenSettings)
            } else {
                FaceButton(stringResource(R.string.face_perm_allow), onAllow, glyph = FaceGlyph.Camera)
            }
            FaceButton(stringResource(R.string.face_perm_not_now), onDismiss, style = FaceButtonStyle.Ghost)
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun CameraPermissionScreenPreview() {
    CameraPermissionScreen("Face check", permanentlyDenied = false, onAllow = {}, onOpenSettings = {}, onDismiss = {})
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun CameraPermissionDeniedPreview() {
    CameraPermissionScreen("Face check", permanentlyDenied = true, onAllow = {}, onOpenSettings = {}, onDismiss = {})
}
