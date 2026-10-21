@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter

@kotlin.native.HiddenFromObjC
actual fun numberSymbolsFor(tag: String): NumberSymbols = NSNumberFormatter().run {
    // Foundation defaults bare Arabic to Latin digits; match Android's resource formatting.
    val identifier = if (tag == "ar") "ar@numbers=arab" else tag
    locale = NSLocale(localeIdentifier = identifier)
    NumberSymbols(checkNotNull(decimalSeparator).first(), checkNotNull(stringFromNumber(NSNumber(int = 0))).first())
}
