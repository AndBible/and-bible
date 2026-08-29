package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultLabelNameTest {

    private val format = "Label %d"

    @Test fun startsAtOne() {
        assertEquals("Label 1", defaultLabelName(emptySet(), format))
    }

    @Test fun skipsTaken() {
        assertEquals("Label 2", defaultLabelName(setOf("Label 1"), format))
    }

    /** Fills a gap rather than continuing past the highest: the point is a free name, not a
     *  monotonic counter. */
    @Test fun fillsAGap() {
        assertEquals("Label 2", defaultLabelName(setOf("Label 1", "Label 3"), format))
    }

    /** A user reads "label 1" and "Label 1" as the same name, so the generated default must not
     *  produce the second when the first exists. */
    @Test fun collisionIsCaseInsensitive() {
        assertEquals("Label 2", defaultLabelName(setOf("label 1"), format))
    }

    /** Same argument for stray whitespace around a stored name. */
    @Test fun collisionIgnoresSurroundingWhitespace() {
        assertEquals("Label 2", defaultLabelName(setOf("  Label 1 "), format))
    }

    /** Unrelated names never block a number. */
    @Test fun unrelatedNamesDoNotBlock() {
        assertEquals("Label 1", defaultLabelName(setOf("Study", "Sermon notes"), format))
    }

    /** The format is the caller's translated string, so it must not be assumed to be English or to
     *  put the number last. */
    @Test fun honoursAnyPositionInTheFormat() {
        assertEquals("2. Etiketti", defaultLabelName(setOf("1. Etiketti"), "%d. Etiketti"))
    }
}
