package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.sharedlogic.platform.LabelNameStrings

/** Reads the resource on every access so locale changes take effect. */
class AndroidLabelNameStrings(private val context: Context) : LabelNameStrings {
    override val speak: String get() = context.getString(R.string.speak)
    override val unlabelled: String get() = context.getString(R.string.label_unlabelled)
    override val paragraphBreak: String get() = context.getString(R.string.add_paragraph_break)
    override val ai: String get() = context.getString(R.string.ai_label)
}
