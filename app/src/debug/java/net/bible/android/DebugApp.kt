package net.bible.android

import com.facebook.stetho.Stetho

class DebugApp : BibleApplication() {
    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
    }
}
