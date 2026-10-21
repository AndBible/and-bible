package net.bible.sharedui.strings.keyed

import kotlin.test.Test
import kotlin.test.assertEquals

/** Independent outputs, including the controller's resource-backed Android fi/ar measurements. */
class FormatTemplateTest {
    private fun table(tag: String, template: String) = StringTable.of(
        tag = tag,
        lookup = { template },
        plural = { _, _ -> template },
        array = { emptyList() },
    )

    @Test fun explicitSymbolsAreIndependentOfPlatformLocale() {
        assertEquals("1,5 MB", formatTemplate("%.1f MB", arrayOf(1.5), NumberSymbols(',', '0')))
        assertEquals("١٥", formatTemplate("%d", arrayOf(15), NumberSymbols('٫', '٠')))
        assertEquals("٠١٫٥", formatTemplate("%04.1f", arrayOf(1.5), NumberSymbols('٫', '٠')))
        assertEquals("15 00FF\n", formatTemplate("%s %04X%n", arrayOf<Any?>("15", 255), NumberSymbols(',', '٠')))
    }

    @Test fun finnishDecimalComma() {
        assertEquals("1,5 MB", table("fi", "%.1f MB").fmt("size", 1.5))
    }

    @Test fun arabicDecimalAndTranslatedUnitMatchAndroidResources() {
        assertEquals("١٫٥ ميجابايت",
            table("ar", "%.1f ميجابايت").fmt("size", 1.5))
    }

    @Test fun localizedDigitsForIntegersAndPadding() {
        assertEquals("٠١٥", table("ar", "%03d").fmt("count", 15))
    }

    @Test fun pluralCountUsesLocaleSymbols() {
        assertEquals("١٥", table("ar", "%d").plural("count", 15))
    }

    @Test fun pluralFormatUsesLocaleSymbolsForAllArguments() {
        assertEquals("١٥: ١٫٥",
            table("ar", "%d: %.1f").pluralFormat("count", 15, 15, 1.5))
    }

    @Test fun asciiDefaultUnchanged() {
        assertEquals("15 1.5 01.5", formatTemplate("%d %.1f %04.1f", arrayOf<Any?>(15, 1.5, 1.5)))
        assertEquals("5 a 5", formatTemplate("%2\$s %s %d", arrayOf<Any?>("a", 5)))
    }

    @Test fun hexAndNewlineUnaffectedByLocale() {
        assertEquals("15: 00FF ff 12 100%\n",
            table("ar", "15: %04X %x %s 100%%%n").fmt("mixed", 255, 255, "12"))
    }
}
