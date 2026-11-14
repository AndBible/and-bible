package net.bible.android.control.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** #4092: the commentary preview memo recognises an entry by a digest instead of holding its text as the key. */
class EntryDigestTest {
    @Test fun sameTextHasTheSameDigest() {
        assertEquals(entryDigest("I am the vine"), entryDigest(String("I am the vine".toCharArray())))
    }

    @Test fun textsOfEqualLengthDiffer() {
        assertNotEquals(entryDigest("I am the vine"), entryDigest("I am the vinf"))
        assertNotEquals(entryDigest("ab"), entryDigest("ba"))
    }

    @Test fun aChangeFarInsideALongEntryChangesTheDigest() {
        val long = "x".repeat(100_000)
        assertNotEquals(entryDigest(long), entryDigest(long.replaceRange(50_000, 50_001, "y")))
    }
}
