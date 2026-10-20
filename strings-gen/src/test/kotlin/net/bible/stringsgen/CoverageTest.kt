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
        // Keep a bounded volume check without pinning the count to a stale snapshot.
        assertTrue(kv.size in 1500..1800, "expected 1500..1800 base strings, got ${kv.size}")
        // Spot-check a known pilot string exists.
        assertTrue("calc_division_by_zero" in kv, "calc_division_by_zero missing")
    }

    @Test fun known_qualifier_mapping() {
        assertEquals("en", qualifierToTag("values"))
        assertEquals("fi", qualifierToTag("values-fi"))
        assertEquals("he", qualifierToTag("values-iw"))
        assertEquals("pt-BR", qualifierToTag("values-pt-rBR"))
        assertEquals("zh-Hans", qualifierToTag("values-zh-rCN"))
        // AndBible-specific BCP-47 (`b+`) + legacy qualifiers.
        assertEquals("sr", qualifierToTag("values-b+sr"))
        assertEquals("sr-Latn", qualifierToTag("values-b+sr+Latn"))
        assertEquals("id", qualifierToTag("values-in"))
        assertEquals("yue", qualifierToTag("values-yue"))
    }

    @Test fun merge_keeps_override_and_retains_values_only_keys() {
        // `values` (base): only key A. `values-en`: A overridden + new key B. Both → "en".
        val base = mapOf("A" to "base-A")
        val en = mapOf("A" to "en-A", "B" to "en-B")
        val merged = mergeLocale(base, en)
        assertEquals("en-A", merged["A"], "values-en must override values for shared key A")
        assertEquals("en-B", merged["B"], "values-en-only key B must be present")
        assertEquals(2, merged.size, "merge must not drop the values-only key set")
        // null existing (first dir seen for a tag) → just the parsed map.
        assertEquals(mapOf("A" to "base-A"), mergeLocale(null, base))
    }

    @Test fun real_base_has_the_six_plurals() {
        val p = parsePluralsXml(baseXml.readText())
        assertEquals(
            setOf(
                "cloud_doc_sync_now_count",
                "cloud_doc_sync_now_count_size",
                "cloud_doc_bulk_remove_all_confirm",
                "cloud_doc_bulk_remove_cloud_confirm",
                "cloud_doc_bulk_purge_confirm",
                "cloud_doc_bulk_block_confirm",
            ),
            p.keys,
            "unexpected base plural keys",
        )
        assertTrue(p.values.all { "one" in it && "other" in it }, "each plural must have one+other")
    }

    @Test fun real_base_has_the_two_string_arrays() {
        val a = parseStringArraysXml(baseXml.readText())
        assertEquals(2, a.size, "expected 2 string-arrays, got ${a.keys}")
        assertTrue("speak_divinename_original" in a && "speak_divinename_replace" in a)
    }

    @Test fun android_unescape_strips_wrapping_quotes_and_decodes() {
        val kv = parseStringsXml("""<resources><string name="x">"  spaced  "</string><string name="y">a\nb</string></resources>""")
        assertEquals("  spaced  ", kv["x"])
        assertEquals("a\nb", kv["y"])
    }
}
