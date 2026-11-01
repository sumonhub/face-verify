package com.sumonlogs.faceverify.harness

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Test-app only. */
val harnessModule = module {
    single { CalibrationLog(androidContext()) }
    single { PerformanceBenchmark(androidContext()) }
    single { HarnessController(get(), get(), get(), get(), get()) }
}
