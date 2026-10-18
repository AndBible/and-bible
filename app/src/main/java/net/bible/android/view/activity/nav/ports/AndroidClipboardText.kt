package net.bible.android.view.activity.nav.ports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import net.bible.sharedui.app.ports.ClipboardText

class AndroidClipboardText(private val context: Context) : ClipboardText {
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun read(): String? = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    override fun write(label: String, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
