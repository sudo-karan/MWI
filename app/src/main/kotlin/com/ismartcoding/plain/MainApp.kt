package com.ismartcoding.plain

import android.app.Application
import com.ismartcoding.plain.platform.AndroidApp

/**
 * The Application entry point — intentionally the only Kotlin file in the thin `:app` module
 * (spec §4). It hands the process-wide [Application] to the shared module and nothing more; all
 * logic lives in `:shared`.
 */
class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.init(this)
    }
}
