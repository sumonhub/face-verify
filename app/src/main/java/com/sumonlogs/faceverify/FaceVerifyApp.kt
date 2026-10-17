package com.sumonlogs.faceverify

import android.app.Application
import com.sumonlogs.faceverify.core.di.faceModule
import com.sumonlogs.faceverify.harness.harnessModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FaceVerifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FaceVerifyApp)
            modules(faceModule, harnessModule)
        }
    }
}
