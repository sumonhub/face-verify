package com.sumonlogs.faceverify.core.di

import com.sumonlogs.faceverify.core.ActivityTracker
import com.sumonlogs.faceverify.core.DefaultFaceVerifier
import com.sumonlogs.faceverify.core.FaceFlowBroker
import com.sumonlogs.faceverify.core.api.DataStoreFaceModelSettings
import com.sumonlogs.faceverify.core.api.FaceDiagnostics
import com.sumonlogs.faceverify.core.api.FaceModelSettings
import com.sumonlogs.faceverify.core.api.FaceVerifier
import com.sumonlogs.faceverify.core.engine.FaceConfig
import com.sumonlogs.faceverify.core.engine.FaceRecordStore
import com.sumonlogs.faceverify.core.engine.android.EncryptedFaceRecordStore
import com.sumonlogs.faceverify.core.engine.android.FaceCropper
import com.sumonlogs.faceverify.core.engine.android.KeystoreCipher
import com.sumonlogs.faceverify.core.engine.android.FaceEmbedders
import com.sumonlogs.faceverify.core.engine.android.MlKitFaceDetector
import com.sumonlogs.faceverify.core.engine.android.faceTemplateDataStore
import com.sumonlogs.faceverify.core.ui.enrollment.EnrollmentCaptureViewModel
import com.sumonlogs.faceverify.core.ui.facecheck.FaceCheckViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Everything the face feature needs. The host app only has to load this module. */
val faceModule = module {
    single { FaceConfig() }
    single { FaceDiagnostics() }
    single<FaceModelSettings> { DataStoreFaceModelSettings(androidContext()) }

    // Engine
    single { MlKitFaceDetector() }
    single { FaceCropper() }
    single { FaceEmbedders(androidContext()) }
    single<FaceRecordStore> { EncryptedFaceRecordStore(androidContext().faceTemplateDataStore, KeystoreCipher()) }

    // Public API
    single(createdAtStart = true) { ActivityTracker(androidApplication()) }
    single { FaceFlowBroker() }
    single<FaceVerifier> { DefaultFaceVerifier(get(), get(), get(), get(), get()) }

    // Screens
    viewModel { FaceCheckViewModel(get(), get(), get(), get(), get()) }
    viewModel { (name: String) -> EnrollmentCaptureViewModel(name, get(), get(), get(), get(), get()) }
}
