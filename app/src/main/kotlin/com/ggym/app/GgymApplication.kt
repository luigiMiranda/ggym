package com.ggym.app

import android.app.Application
import com.ggym.app.di.AppContainer

/**
 * Owns the manual DI container (docs/specs/00-spine.md §4).
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
