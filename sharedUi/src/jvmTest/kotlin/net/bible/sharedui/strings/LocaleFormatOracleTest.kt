package net.bible.sharedui.strings

import java.util.Locale
import net.bible.sharedui.strings.keyed.NumberSymbols
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.strings.keyed.formatTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Android's resource formatter uses java.util.Formatter; use it as an independent locale oracle. */
class LocaleFormatOracleTest {
    @Test fun englishMatchesAndroidFormatter() = checkLocale("en", NumberSymbols.ASCII)
    @Test fun finnishMatchesAndroidFormatter() = checkLocale("fi", NumberSymbols(',', '0'))
    @Test fun germanMatchesAndroidFormatter() = checkLocale("de", NumberSymbols(',', '0'))
    @Test fun arabicMatchesAndroidFormatter() = checkLocale("ar", NumberSymbols('٫', '٠'))
    @Test fun persianMatchesAndroidFormatter() = checkLocale("fa", NumberSymbols('٫', '۰'))

    private fun checkLocale(tag: String, symbols: NumberSymbols) {
        val locale = Locale.forLanguageTag(tag)
        val cases = listOf(
            "%d" to arrayOf<Any?>(1520),
            "%d" to arrayOf<Any?>(-15),
            "%04d" to arrayOf<Any?>(15),
            "%.1f MB" to arrayOf<Any?>(1.5),
            "%.1f / %.1f" to arrayOf<Any?>(1.25, -1.25),
            "%04.1f" to arrayOf<Any?>(1.5),
            "%s" to arrayOf<Any?>("ASCII 1.5 / 15"),
            "%n%s%n%d" to arrayOf<Any?>("15", 7),
            "%x / %04X" to arrayOf<Any?>(255, 255),
            "%2\$s / %1\$d / %3\$.1f / %1\$d" to arrayOf<Any?>(15, "1.5", 1.5),
            "%2\$s / %s / %d / %% / %n%x" to arrayOf<Any?>("15", 7, 255),
        )
        for ((template, args) in cases) {
            // Android and iOS use LF even when this shared JVM test runs on Windows.
            val expected = String.format(locale, template, *args).replace("\r\n", "\n")
            val table = StringTable.of(tag, { template }, { _, _ -> template }, { emptyList() })
            assertEquals(expected, formatTemplate(template, args, symbols), "$tag core: $template")
            assertEquals(expected, table.fmt("value", *args), "$tag keyed: $template")
        }

        val plural = "%1\$d / %2\$.1f / %3\$s%n%x"
        val table = StringTable.of(tag, { plural }, { _, _ -> plural }, { emptyList() })
        assertEquals(String.format(locale, plural, 15, 1.5, "1.5").replace("\r\n", "\n"),
            table.pluralFormat("count", 15, 15, 1.5, "1.5"), "$tag plural arguments")

        // Exercise the real emitted holder, not just two callers of the same formatting core.
        val generated = iosStrings(tag)
        assertEquals(String.format(locale, lookup(tag, "module_size_megabytes"), 1.5), generated.moduleSizeMb(1.5), "$tag generated size")
        assertEquals(String.format(locale, lookup(tag, "document_filter_results"), 1520), generated.docFilterResults(1520), "$tag generated count")
        assertEquals(String.format(locale, lookup(tag, "multi_search_results"), 7, 2), generated.multiSearchResults(7, 2), "$tag generated positions")
    }
}
