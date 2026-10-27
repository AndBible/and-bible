package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.content.pm.ActivityInfo
import net.bible.sharedui.app.ports.OrientationLock

/** Keeps InstallZip in its starting orientation until the destination leaves. */
class AndroidOrientationLock(private val activity: Activity) : OrientationLock {
    override fun lockCurrentOrientation(): () -> Unit {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        return { activity.requestedOrientation = previous }
    }
}
