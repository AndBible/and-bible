package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import net.bible.sharedui.app.ports.SystemScreens

class AndroidSystemScreens(private val activity: Activity) : SystemScreens {
    /** The Android app-links system screen, gated to S+. */
    override fun openAppLinkSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.startActivity(
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${activity.packageName}"))
            )
        }
    }
}
