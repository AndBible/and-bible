package net.bible.service.llm.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/** Schema loading must not turn malformed input into a usable parameter schema. */
class YamlInputBoundaryTest {
    @Test
    fun rejectsNonMappingRootsIncludingTaggedSequences() {
        for (text in listOf("null", "42", "[]", "- name\n- value", "!!seq [name, value]")) {
            val error = assertFailsWith<IllegalArgumentException>(text) { yamlToJson(text) }
            assertEquals("YAML document must be a mapping", error.message)
        }
    }

    @Test
    fun rejectsBrokenMappingsAndDuplicateSchemaFields() {
        for (text in listOf("properties: [unterminated", "type: object\ntype: string", "properties:\n  verse: {type: string")) {
            assertFails(text) { yamlToJson(text) }
        }
    }
}
