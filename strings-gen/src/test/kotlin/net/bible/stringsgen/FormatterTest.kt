package net.bible.stringsgen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FormatterTest {
    private val repoRoot = File(System.getProperty("repoRoot"))

    @Test fun real_install_confirmation_substitutes_ordinary_string() {
        val strings = parseStringsXml(File(repoRoot, "app/src/main/res/values/strings.xml").readText())
        val template = assertNotNull(strings["install_do_you_want"])
        assertEquals("Do you want to install module (f-rawtext.zip)?", fmt(template, "f-rawtext.zip"))
    }

    @Test fun ordinary_placeholders_consume_arguments_in_order() {
        assertEquals("first / second / 3", fmt("%s / %s / %d", "first", "second", 3))
    }

    @Test fun indexed_placeholders_keep_explicit_order_and_reuse() {
        assertEquals("second / first / second", fmt("%2\$s / %1\$s / %2\$s", "first", "second"))
    }

    @Test fun mixed_placeholders_use_independent_ordinary_argument_index() {
        // Matches Android's Formatter: explicit indexes do not advance ordinary argument indexing.
        assertEquals("second / first / first / second", fmt("%2\$s / %s / %1\$s / %s", "first", "second"))
    }

    @Test fun escaped_percent_does_not_consume_an_argument() {
        assertEquals("% first % second", fmt("%% %s %% %s", "first", "second"))
    }

    @Test fun indexed_hex_retains_case_and_zero_padding() {
        assertEquals("0x09 / F8 / f8", fmt("0x%1\$02X / %2\$X / %2\$x", 9, 248))
    }

    @Test fun emitted_ios_formatter_matches_executed_reference_source() {
        // Execute the real reference above, then pin the emitted runtime to that same implementation.
        val source = File(repoRoot, "strings-gen/src/main/kotlin/net/bible/stringsgen/IosStringsEmitter.kt").readText()
        val reference = source.substringAfter("// FMT-BODY-START\n")
            .substringBefore("// FMT-BODY-END").trim()
            .removePrefix("internal ")
        assertEquals(reference, FMT_FUNCTION_SOURCE.trim())
    }
}
