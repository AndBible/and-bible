package net.bible.android

import android.content.res.Resources
import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils

/**
 * Override settings if required
 */
class TestBibleApplication : BibleApplication() {
    init {
        println("TestBibleApplication BibleApplication subclass being used.")
    }

    override val isRunningTests: Boolean = true

    override fun getLocalizedResources(language: String): Resources {
        return application.getResources()
    }

    override fun onCreate() {
        super.onCreate()
        CommonUtils.initializeApp()
    }

    /**
     * This is never called in real system (only in tests). See parent documentation.
     */
    override fun onTerminate() {
        CommonUtils.destroy()
        super.onTerminate()
        ABEventBus.getDefault().unregisterAll()
    }
}
