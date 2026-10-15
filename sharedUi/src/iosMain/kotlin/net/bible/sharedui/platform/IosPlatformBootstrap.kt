package net.bible.sharedui.platform

import kotlinx.coroutines.MainScope
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.IosBackgroundRefresh

object IosPlatformBootstrap {
    val backgroundRefresh: BackgroundRefresh by lazy { IosBackgroundRefresh(MainScope()) }

    /** Called before the Swift host finishes launching; I2 will wire the real sync handler. */
    fun registerBackgroundTasks() = backgroundRefresh.register {
        Log.i("I1", "background refresh fired")
        true
    }
}
