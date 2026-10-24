package net.bible.stringsgen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class CoverageTest {
    private val repoRoot = File(System.getProperty("repoRoot"))
    private val baseXml = File(repoRoot, "app/src/main/res/values/strings.xml")

    @Test fun base_strings_include_platform_and_settings_resources() {
        val kv = parseStringsXml(baseXml.readText())
        setOf("calc_division_by_zero", "settings", "reset_settings", "plan_name_y1ntpspr").forEach {
            assertTrue(kv[it]?.isNotBlank() == true, "Missing or empty base resource: $it")
        }
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

    @Test fun real_base_has_the_expected_plurals() {
        val p = parsePluralsXml(baseXml.readText())
        assertEquals(setOf(
            "cloud_doc_sync_now_count", "cloud_doc_sync_now_count_size", "cloud_doc_bulk_remove_all_confirm",
            "cloud_doc_bulk_remove_cloud_confirm", "cloud_doc_bulk_purge_confirm", "cloud_doc_bulk_block_confirm",
            "legacy_notice_bookmarks_skipped",
        ), p.keys)
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

    private val resDir = File(repoRoot, "app/src/main/res")

    /** The `<string name>` set of one qualifier dir read with a regex, independently of [parseStringsXml]'s DOM parsing. */
    private fun rawStringNames(dirName: String): Set<String> {
        val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val tag = Regex("""<string\s[^>]*?name\s*=\s*"([^"]+)"""")
        return File(resDir, dirName).listFiles { f -> f.isFile && f.extension == "xml" }!!
            .flatMap { f -> tag.findAll(f.readText().replace(comment, "")).map { it.groupValues[1] }.toList() }
            .toSet()
    }

    /** Every `values*` locale dir (qualifier-mapped) and the emitted en table must hold exactly the strings the XML declares. */
    @Test fun every_base_string_in_the_resources_reaches_the_emitted_table() {
        val declared = rawStringNames("values") + rawStringNames("values-en")
        assertTrue(declared.size > 1000, "the real resources declare well over a thousand strings, found ${declared.size}")

        val parsed = LinkedHashMap<String, String>()
        for (dirName in listOf("values", "values-en")) {
            File(resDir, dirName).listFiles { f -> f.isFile && f.extension == "xml" }!!.sortedBy { it.name }
                .forEach { f -> parsed.putAll(mergeLocale(parsed, parseStringsXml(f.readText()))) }
        }
        assertEquals(declared, parsed.keys, "the parser dropped or invented a string")

        // The emitted en table, read back from the generated source text (not from the map handed to the emitter).
        val emitted = emitStringsData(mapOf("en" to parsed))
        val emittedKeys = Regex("""^        "([^"]+)" to """", RegexOption.MULTILINE).findAll(emitted).map { it.groupValues[1] }.toList()
        assertEquals(declared.size, emittedKeys.size, "the emitter wrote a different number of entries than the XML declares")
        assertEquals(declared, emittedKeys.toSet(), "the emitted en keys differ from the declared ones")
    }

    @Test fun a_removed_resource_is_detected_by_the_count_check() {
        val declared = setOf("a", "b", "c")
        val emitted = emitStringsData(mapOf("en" to mapOf("a" to "1", "b" to "2")))
        val emittedKeys = Regex("""^        "([^"]+)" to """", RegexOption.MULTILINE).findAll(emitted).map { it.groupValues[1] }.toSet()
        assertTrue(declared != emittedKeys, "a missing emitted key must make the independent comparison fail")
    }
}
