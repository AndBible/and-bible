package net.bible.android.activity

import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkWidgetRedrawTest {
    private val id = IdType()

    @Test fun theFiveWidgetRelevantBookmarkChangesRedraw() {
        listOf(
            BookmarkChange.BookmarksUpserted(emptyList()),
            BookmarkChange.BookmarksDeleted(listOf(id)),
            BookmarkChange.LabelUpserted(Label(new = true)),
            BookmarkChange.LabelsDeleted(listOf(id)),
            BookmarkChange.NoteModified(id, "n", 0L),
        ).forEach { assertTrue("$it must redraw the bookmark widget", redrawsBookmarkWidget(it)) }
    }

    @Test fun studyPadAndLinkChangesDoNotRedraw() {
        listOf(
            BookmarkChange.BookmarkToLabelUpserted(BibleBookmarkToLabel(id, id)),
            BookmarkChange.StudyPadOrder(id, null, emptyList(), emptyList(), emptyList()),
            BookmarkChange.StudyPadTextEntryDeleted(id),
        ).forEach { assertFalse("$it does not redraw the widget", redrawsBookmarkWidget(it)) }
    }
}
