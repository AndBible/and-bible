package net.bible.android.control.widget

import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ListeningListRedrawTest {
    private val id = IdType()

    @Test fun theFiveWidgetRelevantBookmarkChangesRedraw() {
        listOf(
            BookmarkChange.BookmarksUpserted(emptyList()),
            BookmarkChange.BookmarksDeleted(listOf(id)),
            BookmarkChange.LabelUpserted(Label(new = true)),
            BookmarkChange.LabelsDeleted(listOf(id)),
            BookmarkChange.NoteModified(id, "n", 0L),
        ).forEach { assertTrue(redrawsListeningList(it), "$it must redraw the listening list") }
    }

    @Test fun studyPadAndLinkChangesDoNotRedraw() {
        listOf(
            BookmarkChange.BookmarkToLabelUpserted(BibleBookmarkToLabel(id, id)),
            BookmarkChange.StudyPadOrder(id, null, emptyList(), emptyList(), emptyList()),
            BookmarkChange.StudyPadTextEntryDeleted(id),
        ).forEach { assertFalse(redrawsListeningList(it), "$it does not redraw the listening list") }
    }
}
