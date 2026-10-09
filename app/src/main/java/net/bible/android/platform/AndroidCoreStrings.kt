package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.sharedcore.platform.CoreStrings

/** Reads the resource on every access so locale changes take effect. */
class AndroidCoreStrings(private val context: Context) : CoreStrings {
    override val labelAll: String get() = context.getString(R.string.all)
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
    override fun somethingWithParenthesis(a: String, b: String): String = context.getString(R.string.something_with_parenthesis, a, b)
}
