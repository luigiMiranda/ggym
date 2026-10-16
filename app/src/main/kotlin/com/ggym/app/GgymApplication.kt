package com.ggym.app

import android.app.Application
import com.ggym.app.di.AppContainer

/**
 * Owns the manual DI container (see docs/ARCHITECTURE.md, "State, threading and DI").
 *
 * The container is created lazily-but-once on process start so the DataStore file exists
 * before the first screen renders.
 */
class GgymApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
