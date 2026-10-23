package net.bible.stringsgen

import kotlin.test.Test
import kotlin.test.assertContains

class FormatterTest {
    @Test fun pluralsPreserveCountAndFormatArgumentOrder() {
        val emitted = emitGeneratedStrings(
            listOf(InterfaceMember("items", true, "fun items(count: Int, label: String): String")),
            mapOf("items" to StringMember("items", MemberKind.PLURAL_FUN, "items", listOf("count: Int", "label: String"), "count", listOf("label", "count"))),
        )
        assertContains(emitted, "lookupPlural(tag, \"items\", count).let { formatTemplate(it, arrayOf<Any?>(label, count), numberSymbolsFor(localeTag())) }")
    }

    @Test fun pluralWithoutFormatArgumentsUsesSharedFormatter() {
        val emitted = emitGeneratedStrings(
            listOf(InterfaceMember("items", true, "fun items(count: Int): String")),
            mapOf("items" to StringMember("items", MemberKind.PLURAL_FUN, "items", listOf("count: Int"), "count")),
        )
        assertContains(emitted, "formatTemplate(it, arrayOf<Any?>(), numberSymbolsFor(localeTag()))")
    }
}
