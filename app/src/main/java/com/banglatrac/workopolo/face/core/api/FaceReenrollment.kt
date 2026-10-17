package com.banglatrac.workopolo.face.core.api

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.banglatrac.workopolo.face.core.ui.reenroll.FaceMismatchScreen
import com.banglatrac.workopolo.face.core.ui.reenroll.ReenrollRequestRoute
import com.banglatrac.workopolo.face.core.ui.reenroll.ReenrollRequestedScreen
import kotlinx.serialization.Serializable

/** The host's attendance history, summarised for the mismatch and request screens. */
data class FaceMismatchSummary(
    val mismatchDays: Int,
    val windowDays: Int,
    /** When each recent mismatching check happened (epoch millis), newest first. */
    val recentMismatches: List<Long>,
)

enum class ReenrollReason { LookChanged, PhotoOutdated, Other }

data class ReenrollRequest(val id: String, val sentAtMillis: Long)

/** Implemented by the host: sends the re-enrollment request to the admin. Null means it failed. */
interface FaceReenrollmentGateway {
    suspend fun sendRequest(reason: ReenrollReason, note: String?): ReenrollRequest?
}

@Serializable
data object FaceReenrollmentGraph

@Serializable
internal data object FaceMismatchRoute

@Serializable
internal data object FaceReenrollRequestRoute

@Serializable
internal data class FaceReenrollSentRoute(val id: String, val sentAtMillis: Long)

/**
 * "Face didn't match · repeated" → "Request face re-enrollment" → "Re-enrollment requested".
 * The host decides when to open it (e.g. after N mismatches) and supplies the summary.
 */
fun NavGraphBuilder.faceReenrollmentGraph(
    navController: NavController,
    userName: String?,
    summary: () -> FaceMismatchSummary,
    onTryAgain: () -> Unit,
    onExit: () -> Unit,
) {
    navigation<FaceReenrollmentGraph>(startDestination = FaceMismatchRoute) {
        composable<FaceMismatchRoute> {
            FaceMismatchScreen(
                userName = userName,
                summary = summary(),
                onRequestReenroll = { navController.navigate(FaceReenrollRequestRoute) },
                onTryAgain = onTryAgain,
                onClose = onExit,
            )
        }
        composable<FaceReenrollRequestRoute> {
            ReenrollRequestRoute(
                summary = summary(),
                onBack = { navController.popBackStack() },
                onSent = { request ->
                    navController.navigate(FaceReenrollSentRoute(request.id, request.sentAtMillis)) {
                        popUpTo<FaceMismatchRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<FaceReenrollSentRoute> { entry ->
            val route = entry.toRoute<FaceReenrollSentRoute>()
            ReenrollRequestedScreen(
                request = ReenrollRequest(route.id, route.sentAtMillis),
                onBack = onExit,
            )
        }
    }
}
