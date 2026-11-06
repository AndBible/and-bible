package net.bible.sharedui.strings.keyed

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberSymbolsTest {
    @Test fun localizedFormattingMatchesJvmOracleOutputs() {
        // Literals measured with String.format(Locale.forLanguageTag(tag), template, ...).
        val template = "%1\$d / %2\$.1f / %3\$s / %4\$x%n"
        val args = arrayOf<Any?>(15, 1.5, "1.5", 255)
        val expected = mapOf(
            "en" to "15 / 1.5 / 1.5 / ff\n",
            "fi" to "15 / 1,5 / 1.5 / ff\n",
            "de" to "15 / 1,5 / 1.5 / ff\n",
            "ar" to "١٥ / ١٫٥ / 1.5 / ff\n",
            "fa" to "۱۵ / ۱٫۵ / 1.5 / ff\n",
        )
        for ((tag, output) in expected) {
            assertEquals(output, formatTemplate(template, args, numberSymbolsFor(tag)), tag)
        }
    }

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
