package com.sumonlogs.faceverify.harness

import android.os.Build
import com.sumonlogs.faceverify.BuildConfig
import com.sumonlogs.faceverify.harness.telemetry.CheckUploader
import com.sumonlogs.faceverify.harness.telemetry.FirestoreCheckUploader
import com.sumonlogs.faceverify.harness.telemetry.TesterPrefs
import com.sumonlogs.faceverify.harness.telemetry.TesterTelemetry
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Test-app only. */
val harnessModule = module {
    single { CalibrationLog(androidContext()) }
    single { PerformanceBenchmark(androidContext()) }
    single { TesterPrefs(androidContext()) }
    single<CheckUploader> { FirestoreCheckUploader(androidContext()) }
    // Created at start so the tester's crash-report choice is applied before anything can crash.
    single(createdAtStart = true) {
        TesterTelemetry(get(), get(), BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.SDK_INT)
    }
    single { HarnessController(get(), get(), get(), get(), get(), get()) }
}
