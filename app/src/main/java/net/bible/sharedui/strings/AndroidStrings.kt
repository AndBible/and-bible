package net.bible.sharedui.strings

import android.content.Context
import net.bible.android.activity.R

/** R.string-backed [Strings]. Grows alongside the commonMain interface as leaves move. */
class AndroidStrings(private val context: Context) : Strings {
    override val calcWrongFormat: String get() = context.getString(R.string.calc_wrong_format)
    override val calcWrongFormatOperand: String get() = context.getString(R.string.calc_wrong_format_operand)
    override val calcDivisionByZero: String get() = context.getString(R.string.calc_division_by_zero)
}
