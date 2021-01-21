package net.bible.android

import com.facebook.stetho.Stetho
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import com.facebook.soloader.SoLoader

class DebugApp : BibleApplication() {
    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)

        SoLoader.init(this, false)

        AndroidFlipperClient.getInstance(this).apply {
            addPlugin(InspectorFlipperPlugin(this@DebugApp, DescriptorMapping.withDefaults()))
            addPlugin(DatabasesFlipperPlugin(this@DebugApp))
            addPlugin(SharedPreferencesFlipperPlugin(this@DebugApp))
            start()
        }
    }
}
