package net.bible.stringsgen

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralsTest {
    private val xml = """
        <resources>
          <plurals name="doc_count">
            <item quantity="one">%1${'$'}d document</item>
            <item quantity="other">%1${'$'}d documents</item>
          </plurals>
          <string-array name="colors">
            <item>Red</item>
            <item>Green</item>
          </string-array>
        </resources>
    """.trimIndent()

    @Test fun parses_plural_quantities() {
        val p = parsePluralsXml(xml)
        assertEquals("%1\$d document", p["doc_count"]?.get("one"))
        assertEquals("%1\$d documents", p["doc_count"]?.get("other"))
    }

    @Test fun parses_string_array_in_order() {
        val a = parseStringArraysXml(xml)
        assertEquals(listOf("Red", "Green"), a["colors"])
    }

    @Test fun plural_selector_one_vs_other() {
        // The emitted iOS selector must pick 'one' for count==1, 'other' otherwise (minimal CLDR).
        assertEquals("one", selectQuantity(1))
        assertEquals("other", selectQuantity(0))
        assertEquals("other", selectQuantity(2))
    }
}
