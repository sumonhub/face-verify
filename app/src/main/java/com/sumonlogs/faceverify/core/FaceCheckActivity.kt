package com.sumonlogs.faceverify.core

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sumonlogs.faceverify.core.api.FaceFailure
import com.sumonlogs.faceverify.core.api.FaceMatchResult
import com.sumonlogs.faceverify.core.ui.facecheck.FaceCheckRoute
import com.sumonlogs.faceverify.core.ui.theme.FaceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Hosts the face-check camera flow for [com.sumonlogs.faceverify.core.api.FaceVerifier.verify]. */
class FaceCheckActivity : ComponentActivity() {

    private val broker: FaceFlowBroker by inject()
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (!broker.waiting.value) {
            // Nobody is waiting (e.g. restored after process death): nothing to verify for.
            finish()
            return
        }
        setContent {
            FaceTheme {
                FaceCheckRoute(onResult = ::deliver)
            }
        }
        lifecycleScope.launch {
            broker.waiting.first { !it }
            if (!delivered) finish()
        }
    }

    private fun deliver(result: FaceMatchResult) {
        if (!delivered) {
            delivered = true
            broker.deliver(result)
        }
        finish()
    }

    override fun onDestroy() {
        if (isFinishing && !delivered) {
            delivered = true
            broker.deliver(FaceMatchResult.failed(FaceFailure.Cancelled))
        }
        super.onDestroy()
    }
}
