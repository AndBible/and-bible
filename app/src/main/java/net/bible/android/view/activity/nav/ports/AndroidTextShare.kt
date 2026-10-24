package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.content.Intent
import net.bible.sharedui.app.ports.TextShare

class AndroidTextShare(private val activity: Activity) : TextShare {
    override fun shareText(text: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        activity.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }
}
