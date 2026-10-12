package net.bible.android.control.page

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.KJVA
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
}
