package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingOverlayExclusionTest {

    @Test fun openingAnOverlayClosesEveryOtherModalOverlay() {
        assertEquals(
            setOf(ReadingOverlay.SpeakSheet, ReadingOverlay.TextSettingsEditor),
            ReadingOverlayExclusion.closedBy(ReadingOverlay.Llm),
        )
        assertEquals(
            setOf(ReadingOverlay.Llm, ReadingOverlay.TextSettingsEditor),
            ReadingOverlayExclusion.closedBy(ReadingOverlay.SpeakSheet),
        )
        assertEquals(
            setOf(ReadingOverlay.Llm, ReadingOverlay.SpeakSheet),
            ReadingOverlayExclusion.closedBy(ReadingOverlay.TextSettingsEditor),
        )
    }

    @Test fun anOverlayNeverClosesItself() {
        ReadingOverlay.entries.forEach { opening ->
            assertFalse(
                opening in ReadingOverlayExclusion.closedBy(opening),
                "$opening must not be told to close itself as it opens",
            )
        }
    }

    @Test fun theRuleIsTotalSoANewOverlayCannotBeSilentlyIgnored() {
        ReadingOverlay.entries.forEach { opening ->
            assertEquals(
                ReadingOverlay.entries.size - 1,
                ReadingOverlayExclusion.closedBy(opening).size,
                "every other overlay must be accounted for when $opening opens",
            )
        }
        assertTrue(ReadingOverlay.entries.size >= 3)
    }
}
