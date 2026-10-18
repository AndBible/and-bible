package net.bible.android.control.bookmark

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryWithText

/**
 * One change to bookmarks, labels or StudyPads, emitted by [BookmarkControl.changes]. One sealed
 * stream, not one per kind: subscribers rely on
 * the relative order across kinds (a label before the bookmark that refers to it; bookmarks
 * re-sent without a label before the label is deleted).
 */
sealed interface BookmarkChange {
    data class BookmarksUpserted(val bookmarks: List<BaseBookmarkWithNotes>) : BookmarkChange
    data class BookmarksDeleted(val bookmarkIds: List<IdType>) : BookmarkChange
    data class LabelUpserted(val label: Label) : BookmarkChange
    data class LabelsDeleted(val labelIds: List<IdType>) : BookmarkChange
    data class NoteModified(val bookmarkId: IdType, val notes: String?, val lastUpdatedOn: Long) : BookmarkChange
    data class BookmarkToLabelUpserted(val bookmarkToLabel: BaseBookmarkToLabel) : BookmarkChange
    data class StudyPadOrder(
        val labelId: IdType,
        val newStudyPadTextEntry: StudyPadTextEntryWithText? = null,
        val bookmarkToLabelsOrderChanged: List<BibleBookmarkToLabel>,
        val genericBookmarkToLabelsOrderChanged: List<GenericBookmarkToLabel>,
        val studyPadOrderChanged: List<StudyPadTextEntryWithText>,
    ) : BookmarkChange
    data class StudyPadTextEntryDeleted(val studyPadTextEntryId: IdType) : BookmarkChange
}

/**
 * [current] favourite label ids after a label [change]: an upserted label joins or leaves the
 * favourites by its own `favourite` flag, deleted labels leave. Other changes leave it as is.
 */
fun favouriteIdsAfter(current: List<IdType>, change: BookmarkChange): List<IdType> = when (change) {
    is BookmarkChange.LabelUpserted -> {
        val id = change.label.id
        if (change.label.favourite) { if (current.contains(id)) current else current + id } else current - id
    }
    is BookmarkChange.LabelsDeleted -> current.filterNot { change.labelIds.contains(it) }
    else -> current
}
