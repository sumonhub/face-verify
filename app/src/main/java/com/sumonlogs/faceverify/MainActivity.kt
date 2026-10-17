package com.sumonlogs.faceverify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceEnrollmentGraph
import com.sumonlogs.faceverify.core.api.faceEnrollmentGraph
import com.sumonlogs.faceverify.core.ui.theme.FaceTheme
import com.sumonlogs.faceverify.harness.HarnessController
import com.sumonlogs.faceverify.harness.HomeRoute
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
        setContent {
            FaceTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = HomeDestination) {
                    composable<HomeDestination> {
                        HomeRoute(
                            controller = controller,
                            diagnostics = diagnostics,
                            onEnroll = { navController.navigate(FaceEnrollmentGraph) },
                        )
                    }
                    faceEnrollmentGraph(
                        navController = navController,
                        onFinished = { navController.popBackStack(FaceEnrollmentGraph, inclusive = true) },
                    )
                }
            }
        }
    }
}
