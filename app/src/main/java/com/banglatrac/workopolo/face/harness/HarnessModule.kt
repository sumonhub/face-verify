package com.banglatrac.workopolo.face.harness

import com.banglatrac.workopolo.face.core.api.FaceReenrollmentGateway
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Test-app stand-ins for what the main app provides. */
val harnessModule = module {
    single { MismatchLog(androidContext()) }
    single<FaceReenrollmentGateway> { FakeReenrollmentGateway() }
    single { HarnessController(get(), get()) }
}
