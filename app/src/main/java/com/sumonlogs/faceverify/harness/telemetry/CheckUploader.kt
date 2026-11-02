package com.sumonlogs.faceverify.harness.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How many records were handed over, confirmed by the server, or refused. */
data class UploadStats(val queued: Int = 0, val delivered: Int = 0, val failed: Int = 0) {
    /** Saved on the phone and sent as soon as there is a connection. */
    val waiting: Int get() = (queued - delivered - failed).coerceAtLeast(0)
}

/** Where check records go. */
interface CheckUploader {
    /** False when this build has no Firebase project (no google-services.json): nothing is sent. */
    val available: Boolean
    val stats: StateFlow<UploadStats>
    fun upload(fields: Map<String, Any?>)

    /** One face crop, to its own collection. Only called after the tester agreed to share crops. */
    fun uploadCrop(fields: Map<String, Any?>)
    fun setCrashReporting(enabled: Boolean)
}

/**
 * Writes each record to the `checks` collection of the Firestore database. Records made offline wait
 * in Firestore's own on-device queue and are sent later, so the tester doesn't have to do anything.
 */
class FirestoreCheckUploader(context: Context) : CheckUploader {
    override val available: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val _stats = MutableStateFlow(UploadStats())
    override val stats: StateFlow<UploadStats> = _stats.asStateFlow()

    override fun upload(fields: Map<String, Any?>) = send(COLLECTION, fields)

    override fun uploadCrop(fields: Map<String, Any?>) = send(CROPS_COLLECTION, fields)

    private fun send(collection: String, fields: Map<String, Any?>) {
        if (!available) return
        _stats.update { it.copy(queued = it.queued + 1) }
        FirebaseFirestore.getInstance().collection(collection)
            .add(fields + (RECEIVED_AT to FieldValue.serverTimestamp()))
            .addOnSuccessListener { _stats.update { it.copy(delivered = it.delivered + 1) } }
            .addOnFailureListener { _stats.update { it.copy(failed = it.failed + 1) } }
    }

    override fun setCrashReporting(enabled: Boolean) {
        if (available) FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    private companion object {
        const val COLLECTION = "checks"
        const val CROPS_COLLECTION = "crops"
        const val RECEIVED_AT = "receivedAt"
    }
}
