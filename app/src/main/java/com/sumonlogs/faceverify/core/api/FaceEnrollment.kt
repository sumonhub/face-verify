package com.sumonlogs.faceverify.core.api

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sumonlogs.faceverify.core.engine.android.FaceWarmUp
import com.sumonlogs.faceverify.core.ui.enrollment.FaceEnrollCaptureRoute
import com.sumonlogs.faceverify.core.ui.enrollment.FaceEnrollDoneScreen
import com.sumonlogs.faceverify.core.ui.enrollment.FaceEnrollIntroScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Route of the whole enrollment flow; navigate here from the host's nav graph. */
@Serializable
data object FaceEnrollmentGraph

@Serializable
internal data object FaceEnrollIntroRoute

@Serializable
internal data class FaceEnrollCaptureRoute(val name: String)

@Serializable
internal data class FaceEnrollDoneRoute(val name: String)

/**
 * Adds intro (name) → capture → done to the host's nav graph. Each run enrolls one new person.
 * [onFinished] is called with true once the person is saved and Done is tapped, or false if the
 * flow is left before that. The host pops [FaceEnrollmentGraph] in both cases.
 */
fun NavGraphBuilder.faceEnrollmentGraph(
    navController: NavController,
    onFinished: (enrolled: Boolean) -> Unit,
) {
    navigation<FaceEnrollmentGraph>(startDestination = FaceEnrollIntroRoute) {
        composable<FaceEnrollIntroRoute> {
            // The person types a name meanwhile; the models load in the background.
            val warmUp: FaceWarmUp = koinInject()
            LaunchedEffect(Unit) { warmUp.run(includeSpoof = false) }
            FaceEnrollIntroScreen(
                onStart = { name -> navController.navigate(FaceEnrollCaptureRoute(name)) },
                onClose = { onFinished(false) },
            )
        }
        composable<FaceEnrollCaptureRoute> { entry ->
            FaceEnrollCaptureRoute(
                name = entry.toRoute<FaceEnrollCaptureRoute>().name,
                onEnrolled = { name ->
                    navController.navigate(FaceEnrollDoneRoute(name)) {
                        popUpTo<FaceEnrollIntroRoute> { inclusive = true }
                    }
                },
                onCancel = { onFinished(false) },
                onPermissionDismissed = { navController.popBackStack() },
            )
        }
        composable<FaceEnrollDoneRoute> { entry ->
            BackHandler { onFinished(true) }
            FaceEnrollDoneScreen(name = entry.toRoute<FaceEnrollDoneRoute>().name, onDone = { onFinished(true) })
        }
    }
}

/** Self-contained enrollment flow with its own NavHost, for hosts that don't use Navigation Compose. */
@Composable
fun FaceEnrollmentFlow(onFinished: (enrolled: Boolean) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = FaceEnrollmentGraph) {
        faceEnrollmentGraph(navController, onFinished)
    }
}
