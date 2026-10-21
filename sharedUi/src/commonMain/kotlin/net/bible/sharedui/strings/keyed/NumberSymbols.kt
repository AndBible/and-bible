@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

/** Decimal symbols used only for numeric conversions, never for literal or string content. */
@kotlin.native.HiddenFromObjC
data class NumberSymbols(val decimalSeparator: Char, val zeroDigit: Char) {
    companion object {
        val ASCII = NumberSymbols('.', '0')
    }
}

@kotlin.native.HiddenFromObjC
expect fun numberSymbolsFor(tag: String): NumberSymbols
