package com.sumonlogs.faceverify.harness

import org.koin.dsl.module

/** Test-app only. */
val harnessModule = module {
    single { HarnessController(get()) }
}
