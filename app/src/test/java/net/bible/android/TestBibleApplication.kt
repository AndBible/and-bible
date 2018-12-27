package net.bible.android

import android.content.res.Resources

/**
 * Override settings if required
 */
class TestBibleApplication : BibleApplication() {
    init {
        println("TestBibleApplication BibleApplication subclass being used.")
    }

    override fun getLocalizedResources(language: String): Resources {
        return application.getResources()
    }
}
