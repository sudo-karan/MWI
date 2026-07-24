package com.ismartcoding.plain.platform

import android.app.Application
import android.content.Context

/**
 * Holds the process-wide [Application] so androidMain `actual`s (DB builder, DataStore factory,
 * system services) can reach a [Context] without threading it through every common signature.
 * Set once in `MainApp.onCreate`.
 */
object AndroidApp {
    lateinit var instance: Application
        private set

    val context: Context get() = instance

    fun init(application: Application) {
        instance = application
    }
}
