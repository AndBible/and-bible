package net.bible.android.control.page

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

class ClientBookmarkLabelsTest {
    private fun bible(labels: List<IdType>) = BookmarkEntities.BibleBookmarkWithNotes(
        kjvOrdinalStart = 1, kjvOrdinalEnd = 1, ordinalStart = 1, ordinalEnd = 1,
        v11n = KJVA, playbackSettings = null, startOffset = null, endOffset = null,
    ).apply { labelIds = labels }

    private fun generic(labels: List<IdType>) = BookmarkEntities.GenericBookmarkWithNotes(
        key = "chapter", bookInitials = "missing", ordinalStart = null, ordinalEnd = null,
        startOffset = null, endOffset = null, playbackSettings = null,
    ).apply { labelIds = labels }

    @Test fun `empty labels serialize the canonical unlabelled id without Koin`() {
        assertNull(GlobalContext.getOrNull())
        val expected = "[\"$UNLABELED_LABEL_ID\"]"
        assertEquals(expected, ClientBibleBookmark(bible(emptyList())).asHashMap["labels"])
        assertEquals(expected, ClientGenericBookmark(generic(emptyList())).asHashMap["labels"])
    }

    @Test fun `explicit labels keep their ids and order`() {
        val labels = listOf(IdType(), IdType())
        val expected = "[\"${labels[0]}\",\"${labels[1]}\"]"
        assertEquals(expected, ClientBibleBookmark(bible(labels)).asHashMap["labels"])
        assertEquals(expected, ClientGenericBookmark(generic(labels)).asHashMap["labels"])
    }

    @Test fun `complete DTO JSON needs neither Koin nor a database`() {
        assertNull(GlobalContext.getOrNull())
        assertTrue(ClientBibleBookmark(bible(emptyList())).asJson.contains("'labels': [\"$UNLABELED_LABEL_ID\"]"))
        assertTrue(ClientGenericBookmark(generic(emptyList())).asJson.contains("'labels': [\"$UNLABELED_LABEL_ID\"]"))
    }

    /**
     * "Open in My Notes" builds `my-notes://?ordinal=<originalOrdinalRange[0]>&v11n=<v11n>`: the ordinal is in the
     * bookmark's own versification, so `v11n` must be that one even when no Bible is attached (a collection bookmark's
     * copy, or an uninstalled module). With KJVA the KJV ordinal of John 3:16 opened Sirach 19:8.
     */
    @Test fun `v11n names the versification of originalOrdinalRange when the book is absent`() {
        val kjv = Versifications.instance().getVersification("KJV")!!
        val b = BookmarkEntities.BibleBookmarkWithNotes(
            kjvOrdinalStart = 33095, kjvOrdinalEnd = 33095, ordinalStart = 27182, ordinalEnd = 27182,
            v11n = kjv, playbackSettings = null, book = null, startOffset = null, endOffset = null,
        ).apply { labelIds = emptyList() }
        val map = ClientBibleBookmark(b).asHashMap
        assertEquals("[27182,27182]", map["originalOrdinalRange"])
        assertEquals("`KJV`", map["v11n"])
    }
}
