package net.bible.sharedui.strings.keyed

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberSymbolsTest {
    @Test fun bareArabicMatchesAndroidResourceSymbols() {
        assertEquals(NumberSymbols('٫', '٠'), numberSymbolsFor("ar"))
    }

    @Test fun explicitLatinNumberingIsNotOverridden() {
        assertEquals(NumberSymbols.ASCII, numberSymbolsFor("ar@numbers=latn"))
    }

    @Test fun regionalLocalesAndExplicitNumberingKeepTheirSymbols() {
        assertEquals(NumberSymbols.ASCII, numberSymbolsFor("en-US"))
        assertEquals(NumberSymbols(',', '0'), numberSymbolsFor("fi-FI"))
        assertEquals(NumberSymbols('٫', '٠'), numberSymbolsFor("ar_EG"))
        assertEquals(NumberSymbols('٫', '٠'), numberSymbolsFor("ar@numbers=arab"))
    }
}
