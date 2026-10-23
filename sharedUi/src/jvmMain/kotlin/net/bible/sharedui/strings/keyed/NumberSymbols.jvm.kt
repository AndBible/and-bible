package net.bible.sharedui.strings.keyed

import java.text.DecimalFormatSymbols
import java.util.Locale

actual fun numberSymbolsFor(tag: String): NumberSymbols =
    DecimalFormatSymbols.getInstance(Locale.forLanguageTag(tag)).let {
        NumberSymbols(it.decimalSeparator, it.zeroDigit)
    }
