package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.content.pm.ActivityInfo
import net.bible.sharedui.app.ports.OrientationLock

/** Classic's manifest `screenOrientation="portrait"`, destination-scoped (spec §3.2). */
class AndroidOrientationLock(private val activity: Activity) : OrientationLock {
    override fun lockPortrait(): () -> Unit {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        return { activity.requestedOrientation = previous }
    }
}
