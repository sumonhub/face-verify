package com.banglatrac.workopolo.face

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.api.FaceEnrollmentGraph
import com.banglatrac.workopolo.face.core.api.FaceReenrollmentGraph
import com.banglatrac.workopolo.face.core.api.faceEnrollmentGraph
import com.banglatrac.workopolo.face.core.api.faceReenrollmentGraph
import com.banglatrac.workopolo.face.core.ui.theme.FaceTheme
import com.banglatrac.workopolo.face.harness.HarnessController
import com.banglatrac.workopolo.face.harness.HomeRoute
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject

@Serializable
private data object HomeDestination

class MainActivity : ComponentActivity() {

    private val controller: HarnessController by inject()
    private val diagnostics: FaceDiagnostics by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller.refresh()
        setContent {
            FaceTheme {
                val navController = rememberNavController()
                val enrolled by controller.enrolled.collectAsStateWithLifecycle()
                val userName = getString(R.string.harness_demo_user)

                LaunchedEffect(controller) {
                    controller.repeatedMismatch.collect { navController.navigate(FaceReenrollmentGraph) }
                }

                NavHost(navController = navController, startDestination = HomeDestination) {
                    composable<HomeDestination> {
                        HomeRoute(
                            controller = controller,
                            diagnostics = diagnostics,
                            onEnroll = { navController.navigate(FaceEnrollmentGraph) },
                            onShowMismatch = { navController.navigate(FaceReenrollmentGraph) },
                        )
                    }
                    faceEnrollmentGraph(
                        navController = navController,
                        userName = userName,
                        isReenrollment = enrolled == true,
                        onFinished = { done ->
                            navController.popBackStack(FaceEnrollmentGraph, inclusive = true)
                            controller.onEnrollmentFinished(done)
                        },
                    )
                    faceReenrollmentGraph(
                        navController = navController,
                        userName = userName,
                        summary = { controller.mismatchSummary.value },
                        onTryAgain = {
                            navController.popBackStack(FaceReenrollmentGraph, inclusive = true)
                            controller.verify()
                        },
                        onExit = { navController.popBackStack(FaceReenrollmentGraph, inclusive = true) },
                    )
                }
            }
        }
    }
}
