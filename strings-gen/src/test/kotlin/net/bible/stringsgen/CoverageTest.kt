package net.bible.stringsgen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class CoverageTest {
    private val repoRoot = File(System.getProperty("repoRoot"))
    private val baseXml = File(repoRoot, "app/src/main/res/values/strings.xml")

    @Test fun base_strings_parse_and_have_expected_volume() {
        val kv = parseStringsXml(baseXml.readText())
        // Verified 2026-07-09: 1507 <string> entries in the base file.
        assertTrue(kv.size in 1400..1600, "expected ~1507 base strings, got ${kv.size}")
        // Spot-check a known pilot string exists.
        assertTrue("calc_division_by_zero" in kv, "calc_division_by_zero missing")
    }

    @Test fun known_qualifier_mapping() {
        assertEquals("en", qualifierToTag("values"))
        assertEquals("fi", qualifierToTag("values-fi"))
        assertEquals("he", qualifierToTag("values-iw"))
        assertEquals("pt-BR", qualifierToTag("values-pt-rBR"))
        assertEquals("zh-Hans", qualifierToTag("values-zh-rCN"))
    }

    @Test fun android_unescape_strips_wrapping_quotes_and_decodes() {
        val kv = parseStringsXml("""<resources><string name="x">"  spaced  "</string><string name="y">a\nb</string></resources>""")
        assertEquals("  spaced  ", kv["x"])
        assertEquals("a\nb", kv["y"])
    }
}
