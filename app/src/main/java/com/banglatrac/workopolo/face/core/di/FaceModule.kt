package com.banglatrac.workopolo.face.core.di

import com.banglatrac.workopolo.face.core.ActivityTracker
import com.banglatrac.workopolo.face.core.DefaultFaceVerifier
import com.banglatrac.workopolo.face.core.FaceFlowBroker
import com.banglatrac.workopolo.face.core.api.FaceDiagnostics
import com.banglatrac.workopolo.face.core.api.FaceVerifier
import com.banglatrac.workopolo.face.core.engine.FaceConfig
import com.banglatrac.workopolo.face.core.engine.FaceEmbedder
import com.banglatrac.workopolo.face.core.engine.FaceTemplateStore
import com.banglatrac.workopolo.face.core.engine.android.EncryptedFaceTemplateStore
import com.banglatrac.workopolo.face.core.engine.android.FaceCropper
import com.banglatrac.workopolo.face.core.engine.android.KeystoreCipher
import com.banglatrac.workopolo.face.core.engine.android.LiteRtFaceEmbedder
import com.banglatrac.workopolo.face.core.engine.android.MlKitFaceDetector
import com.banglatrac.workopolo.face.core.engine.android.faceTemplateDataStore
import com.banglatrac.workopolo.face.core.ui.facecheck.FaceCheckViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Everything the face feature needs. The host app only has to load this module. */
val faceModule = module {
    single { FaceConfig() }
    single { FaceDiagnostics(threshold = get<FaceConfig>().matchThreshold) }

    // Engine
    single { MlKitFaceDetector() }
    single { FaceCropper() }
    single<FaceEmbedder> { LiteRtFaceEmbedder(androidContext()) }
    single<FaceTemplateStore> { EncryptedFaceTemplateStore(androidContext().faceTemplateDataStore, KeystoreCipher()) }

    // Public API
    single(createdAtStart = true) { ActivityTracker(androidApplication()) }
    single { FaceFlowBroker() }
    single<FaceVerifier> { DefaultFaceVerifier(get(), get(), get(), get()) }

    // Screens
    viewModel { FaceCheckViewModel(get(), get(), get(), get()) }
}
