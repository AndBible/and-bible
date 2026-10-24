package net.bible.sharedui.strings

import net.bible.sharedui.strings.keyed.formatTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real generated iOS `Strings` holder: its resources use Android's unpositional `%d`/`%s`/`%.1f` specs. */
class GeneratedStringsFormatTest {
    private val strings = iosStrings("en")

    @Test fun documentCountSubstitutesTheUnpositionalDecimal() {
        assertEquals("5 documents", strings.docFilterResults(5))
        assertEquals("1520 documents", strings.docFilterResults(1520))
    }

    @Test fun unpositionalStringsAndNumbersSubstituteInOrder() {
        assertEquals("Delete KJV?", strings.deleteDoc("KJV"))
        assertEquals("Workspace 3", strings.workspaceNumber(3))
        assertEquals("Show 4 more", strings.memorizeShowMore(4))
        assertEquals("1.5 MB", strings.moduleSizeMb(1.5))
    }

    @Test fun generatedFormattingFollowsLiveLocaleProvider() {
        var tag = "en"
        val live = iosStrings { tag }
        assertEquals("1.5 MB", live.moduleSizeMb(1.5))
        tag = "fi"
        assertEquals("1,5 MB", live.moduleSizeMb(1.5))
        tag = "en"
        assertEquals("1.5 MB", live.moduleSizeMb(1.5))
    }

    @Test fun positionalAndUnpositionalResourcesBothSubstitute() {
        assertEquals("7 verses in 2 translations", strings.multiSearchResults(7, 2))
        assertEquals("Find in X", strings.searchIn("X"))
    }

    @Test fun mixedTemplatesKeepIndependentCountersInTheEmittedFunction() {
        // The shared runtime formatter preserves independent positional and ordinary counters.
        assertEquals("5 a 5", formatTemplate("%2\$s %s %d", arrayOf<Any?>("a", 5)))
        assertEquals("Found 7 of 2 in 2: 7", formatTemplate("Found %d of %2\$d in %s: %1\$d", arrayOf<Any?>(7, 2, "KJV")))
        assertEquals("\n %b 1 %e %,d", formatTemplate("%n %b %x %e %,d", arrayOf<Any?>(1, 2, 3, 4, 5)))
    }
}
