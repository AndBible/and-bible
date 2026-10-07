package net.bible.android.control.page.window

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSyncScrollTest {
    private val verse = Verse(Versifications.instance().getVersification("KJVA"), BibleBook.JOHN, 3, 16)

    @Test fun aListeningViewScrolls() {
        val scrolled = mutableListOf<Verse>()
        scrollSecondaryWindow(listening = true, verse = verse) { scrolled += it }
        assertEquals(listOf(verse), scrolled)
    }

    @Test fun aNonListeningViewIsSkipped() {
        val scrolled = mutableListOf<Verse>()
        scrollSecondaryWindow(listening = false, verse = verse) { scrolled += it }
        assertEquals(emptyList<Verse>(), scrolled)
    }
}
