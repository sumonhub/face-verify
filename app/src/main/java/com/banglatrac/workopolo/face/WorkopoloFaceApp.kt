package com.banglatrac.workopolo.face

import android.app.Application
import com.banglatrac.workopolo.face.core.di.faceModule
import com.banglatrac.workopolo.face.harness.harnessModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WorkopoloFaceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WorkopoloFaceApp)
            modules(faceModule, harnessModule)
        }
    }
}
