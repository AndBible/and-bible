package net.bible.stringsgen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FmtTest {
    @Test fun formattedMemberUsesSharedFormatterAndLiveSymbols() {
        val emitted = emitGeneratedStrings(
            listOf(InterfaceMember("size", true, "fun size(mb: Double): String")),
            mapOf("size" to StringMember("size", MemberKind.FORMAT_FUN, "size_mb", listOf("mb: Double"))),
        )
        assertContains(emitted, "import net.bible.sharedui.strings.keyed.formatTemplate")
        assertContains(emitted, "formatTemplate(lookup(tag, \"size_mb\"), arrayOf<Any?>(mb), numberSymbolsFor(localeTag()))")
        assertFalse(emitStringsData(emptyMap()).contains("fun fmt("))
    }
}
