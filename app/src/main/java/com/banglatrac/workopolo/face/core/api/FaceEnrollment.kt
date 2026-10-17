package com.banglatrac.workopolo.face.core.api

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.banglatrac.workopolo.face.core.ui.enrollment.FaceEnrollCaptureRoute
import com.banglatrac.workopolo.face.core.ui.enrollment.FaceEnrollDoneScreen
import com.banglatrac.workopolo.face.core.ui.enrollment.FaceEnrollIntroScreen
import kotlinx.serialization.Serializable

/** Route of the whole enrollment flow; navigate here from the host's nav graph. */
@Serializable
data object FaceEnrollmentGraph

@Serializable
internal data object FaceEnrollIntroRoute

@Serializable
internal data object FaceEnrollCaptureRoute

@Serializable
internal data object FaceEnrollDoneRoute

/**
 * Adds intro → capture → done to the host's nav graph.
 * [onFinished] is called with true once templates are saved and the person taps Continue,
 * or false if they leave before that. The host pops [FaceEnrollmentGraph] in both cases.
 */
fun NavGraphBuilder.faceEnrollmentGraph(
    navController: NavController,
    userName: String?,
    isReenrollment: Boolean,
    onFinished: (enrolled: Boolean) -> Unit,
) {
    navigation<FaceEnrollmentGraph>(startDestination = FaceEnrollIntroRoute) {
        composable<FaceEnrollIntroRoute> {
            FaceEnrollIntroScreen(
                isReenrollment = isReenrollment,
                onStart = { navController.navigate(FaceEnrollCaptureRoute) },
                onClose = { onFinished(false) },
            )
        }
        composable<FaceEnrollCaptureRoute> {
            FaceEnrollCaptureRoute(
                onEnrolled = {
                    navController.navigate(FaceEnrollDoneRoute) {
                        popUpTo<FaceEnrollIntroRoute> { inclusive = true }
                    }
                },
                onCancel = { onFinished(false) },
                onPermissionDismissed = { navController.popBackStack() },
            )
        }
        composable<FaceEnrollDoneRoute> {
            BackHandler { onFinished(true) }
            FaceEnrollDoneScreen(userName = userName, onContinue = { onFinished(true) })
        }
    }
}

/** Self-contained enrollment flow with its own NavHost, for hosts that don't use Navigation Compose. */
@Composable
fun FaceEnrollmentFlow(userName: String?, isReenrollment: Boolean, onFinished: (enrolled: Boolean) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = FaceEnrollmentGraph) {
        faceEnrollmentGraph(navController, userName, isReenrollment, onFinished)
    }
}
