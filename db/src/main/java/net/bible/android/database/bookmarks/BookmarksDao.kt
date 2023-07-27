/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database.bookmarks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import java.util.*

const val orderBy = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BibleBookmarkWithNotes.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BibleBookmarkWithNotes.startOffset END,
CASE WHEN :orderBy = 'CREATED_AT_DESC' THEN -BibleBookmarkWithNotes.createdAt END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN BibleBookmarkWithNotes.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN BibleBookmarkWithNotes.lastUpdatedOn
END"""

const val genericOrderBy = """bookInitials, `key`"""

const val orderBy2 = """
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BibleBookmarkWithNotes.kjvOrdinalStart END,
CASE WHEN :orderBy = 'BIBLE_ORDER' THEN BibleBookmarkWithNotes.startOffset END,
CASE
    WHEN :orderBy = 'CREATED_AT' THEN BibleBookmarkWithNotes.createdAt
    WHEN :orderBy = 'CREATED_AT_DESC' THEN -BibleBookmarkWithNotes.createdAt
    WHEN :orderBy = 'LAST_UPDATED' THEN BibleBookmarkWithNotes.lastUpdatedOn
    WHEN :orderBy = 'ORDER_NUMBER' THEN BibleBookmarkToLabel.orderNumber
END"""

@Dao
interface BookmarkDao {
    @Query("SELECT * from BibleBookmarkWithNotes ORDER BY $orderBy")
    fun allBookmarks(orderBy: String): List<BibleBookmarkWithNotes>

    @Query("SELECT * from GenericBookmarkWithNotes ORDER BY $genericOrderBy")
    fun allGenericBookmarks(): List<GenericBookmarkWithNotes>

    fun allBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BibleBookmarkWithNotes> =
        allBookmarks(orderBy.name)

    @Query("SELECT * from BibleBookmarkWithNotes WHERE notes IS NOT NULL ORDER BY $orderBy")
    fun allBookmarksWithNotes(orderBy: String): List<BibleBookmarkWithNotes>
    fun allBookmarksWithNotes(orderBy: BookmarkSortOrder): List<BibleBookmarkWithNotes> =
        allBookmarksWithNotes(orderBy.name)

    @Query("SELECT * from BibleBookmarkWithNotes where id = :bookmarkId")
    fun bibleBookmarkById(bookmarkId: IdType): BibleBookmarkWithNotes?

    @Query("SELECT * from GenericBookmarkWithNotes where id = :bookmarkId")
    fun genericBookmarkById(bookmarkId: IdType): GenericBookmarkWithNotes?

    @Query("SELECT * from BibleBookmarkWithNotes where id IN (:bookmarkIds)")
    fun bibleBookmarksByIds(bookmarkIds: List<IdType>): List<BibleBookmarkWithNotes>

    @Query("SELECT * from GenericBookmarkWithNotes where id IN (:bookmarkIds)")
    fun genericBookmarksByIds(bookmarkIds: List<IdType>): List<GenericBookmarkWithNotes>

    //https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap
    @Query(
        """SELECT * from BibleBookmarkWithNotes where
        kjvOrdinalStart <= :rangeEnd AND :rangeStart <= kjvOrdinalEnd
        ORDER BY kjvOrdinalStart, startOffset
        """
    )
    fun bookmarksForKjvOrdinalRange(rangeStart: Int, rangeEnd: Int): List<BibleBookmarkWithNotes>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<BibleBookmarkWithNotes> {
        val v = verseRange.toV11n(KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<BibleBookmarkWithNotes> = bookmarksForVerseRange(KJVA.allVerses)

    @Query("SELECT * from BibleBookmarkWithNotes where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<BibleBookmarkWithNotes>
    fun bookmarksForVerse(verse: Verse): List<BibleBookmarkWithNotes> =
        bookmarksForKjvOrdinal(verse.toV11n(KJVA).ordinal)

    @Query("""SELECT * from BibleBookmarkWithNotes where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<BibleBookmarkWithNotes>
    fun bookmarksStartingAtVerse(verse: Verse): List<BibleBookmarkWithNotes> =
        bookmarksForKjvOrdinalStart(verse.toV11n(KJVA).ordinal)

    @Query("SELECT count(*) > 0 from BibleBookmarkWithNotes where kjvOrdinalStart <= :verseOrdinal AND :verseOrdinal <= kjvOrdinalEnd LIMIT 1")
    fun hasBookmarksForVerse(verseOrdinal: Int): Boolean
    fun hasBookmarksForVerse(verse: Verse): Boolean = hasBookmarksForVerse(verse.toV11n(KJVA).ordinal)

    @Query(
        """
        SELECT BibleBookmarkWithNotes.* FROM BibleBookmarkWithNotes 
            JOIN BibleBookmarkToLabel ON BibleBookmarkWithNotes.id = BibleBookmarkToLabel.bookmarkId 
            JOIN Label ON BibleBookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId AND BibleBookmarkWithNotes.kjvOrdinalStart = :startOrdinal
        """
    )
    fun bookmarksForVerseStartWithLabel(labelId: IdType, startOrdinal: Int): List<BibleBookmarkWithNotes>
    fun bookmarksForVerseStartWithLabel(verse: Verse, label: Label): List<BibleBookmarkWithNotes> =
        bookmarksForVerseStartWithLabel(label.id, verse.toV11n(KJVA).ordinal)

    @Insert fun insert(entity: BibleBookmark)
    @Insert fun insert(entity: GenericBookmark)

    fun insert(entity: BaseBookmark) = when(entity) {
        is BibleBookmark -> insert(entity)
        is GenericBookmark -> insert(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Insert fun insert(entity: BibleBookmarkNotes)
    @Insert fun insert(entity: GenericBookmarkNotes)

    fun insert(entity: BaseBookmarkNotes)  = when(entity) {
        is BibleBookmarkNotes -> insert(entity)
        is GenericBookmarkNotes -> insert(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Update fun update(entity: BibleBookmark)
    @Update fun update(entity: GenericBookmark)

    fun update(entity: BaseBookmark) = when(entity) {
        is BibleBookmark -> update(entity)
        is GenericBookmark -> update(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Update fun update(entity: BibleBookmarkNotes)
    @Update fun update(entity: GenericBookmarkNotes)

    fun update(entity: BaseBookmarkNotes) = when(entity) {
        is BibleBookmarkNotes -> update(entity)
        is GenericBookmarkNotes -> update(entity)
        else -> throw RuntimeException("Wrong type")
    }

    @Query("DELETE FROM BibleBookmarkNotes WHERE bookmarkId=:id")
    fun deleteBookmarkNotes(id: IdType)
    @Query("DELETE FROM GenericBookmarkNotes WHERE bookmarkId=:id")
    fun deleteGenericBookmarkNotes(id: IdType)
    fun deleteBookmarkNotes(bookmark: BibleBookmarkWithNotes) = deleteBookmarkNotes(bookmark.id)
    fun deleteBookmarkNotes(bookmark: GenericBookmarkWithNotes) = deleteGenericBookmarkNotes(bookmark.id)
    fun deleteBookmarkNotes(bookmark: BaseBookmarkWithNotes) = when(bookmark) {
        is BibleBookmarkWithNotes -> deleteBookmarkNotes(bookmark.id)
        is GenericBookmarkWithNotes -> deleteGenericBookmarkNotes(bookmark.id)
        else -> throw RuntimeException("Wrong type")
    }

    @Query("UPDATE BibleBookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:id")
    fun updateBibleBookmarkDate(id: IdType, lastUpdatedOn: Date = Date(System.currentTimeMillis()))

    @Query("UPDATE GenericBookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:id")
    fun updateGenericBookmarkDate(id: IdType, lastUpdatedOn: Date = Date(System.currentTimeMillis()))

    fun deleteBookmarks(bs: List<BaseBookmarkWithNotes>) = when(bs.first()) {
        is BibleBookmarkWithNotes -> deleteBookmarksById(bs.map {it.id})
        is GenericBookmarkWithNotes -> deleteGenericBookmarksById(bs.map {it.id})
        else -> throw RuntimeException("Illegal type")
    }

    fun delete(bookmark: BaseBookmarkWithNotes) = when (bookmark) {
        is BibleBookmarkWithNotes -> deleteBookmarksById(listOf(bookmark.id))
        is GenericBookmarkWithNotes -> deleteGenericBookmarksById(listOf(bookmark.id))
        else -> throw RuntimeException("Illegal type")
    }

    @Query("DELETE FROM BibleBookmark WHERE id IN (:bs)")
    fun deleteBookmarksById(bs: List<IdType>)

    @Query("DELETE FROM GenericBookmark WHERE id IN (:bs)")
    fun deleteGenericBookmarksById(bs: List<IdType>)

    @Query(
        """
        SELECT * FROM BibleBookmarkWithNotes WHERE NOT EXISTS 
            (SELECT * FROM BibleBookmarkToLabel WHERE BibleBookmarkWithNotes.id = BibleBookmarkToLabel.bookmarkId)
            ORDER BY $orderBy
        """
    )
    fun unlabelledBookmarks(orderBy: String): List<BibleBookmarkWithNotes>

    @Query(
        """
        SELECT * FROM GenericBookmarkWithNotes WHERE NOT EXISTS 
            (SELECT * FROM GenericBookmarkToLabel WHERE GenericBookmarkWithNotes.id = GenericBookmarkToLabel.bookmarkId)
            ORDER BY $genericOrderBy
        """
    )
    fun unlabelledGenericBookmarks(): List<GenericBookmarkWithNotes>

    fun unlabelledBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BibleBookmarkWithNotes> =
        unlabelledBookmarks(orderBy.name)


    @Query(
        """
        SELECT BibleBookmarkWithNotes.* FROM BibleBookmarkWithNotes 
            JOIN BibleBookmarkToLabel ON BibleBookmarkWithNotes.id = BibleBookmarkToLabel.bookmarkId 
            JOIN Label ON BibleBookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY $orderBy2
        """
    )
    fun bookmarksWithLabel(labelId: IdType, orderBy: String): List<BibleBookmarkWithNotes>

    fun bookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BibleBookmarkWithNotes>
        = bookmarksWithLabel(label.id, orderBy.name)
    fun bookmarksWithLabel(labelId: IdType, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<BibleBookmarkWithNotes>
        = bookmarksWithLabel(labelId, orderBy.name)

    @Query(
        """
        SELECT GenericBookmarkWithNotes.* FROM GenericBookmarkWithNotes 
            JOIN GenericBookmarkToLabel ON GenericBookmarkWithNotes.id = GenericBookmarkToLabel.bookmarkId 
            JOIN Label ON GenericBookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY $genericOrderBy
        """
    )
    fun genericBookmarksWithLabel(labelId: IdType): List<GenericBookmarkWithNotes>
    fun genericBookmarksWithLabel(label: Label): List<GenericBookmarkWithNotes>
        = genericBookmarksWithLabel(label.id)

    @Query("""INSERT INTO BibleBookmarkNotes VALUES (:bookmarkId, :notes) ON CONFLICT DO UPDATE SET notes=:notes WHERE bookmarkId=:bookmarkId""")
    fun _saveBookmarkNote(bookmarkId: IdType, notes: String?)

    @Query("""INSERT INTO GenericBookmarkNotes VALUES (:bookmarkId, :notes) ON CONFLICT DO UPDATE SET notes=:notes WHERE bookmarkId=:bookmarkId""")
    fun _saveGenericBookmarkNote(bookmarkId: IdType, notes: String?)
     @Query("""UPDATE BibleBookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:bookmarkId""")
    fun saveBookmarkLastUpdatedOn(bookmarkId: IdType, lastUpdatedOn: Long)

    @Query("""UPDATE GenericBookmark SET lastUpdatedOn=:lastUpdatedOn WHERE id=:bookmarkId""")
    fun saveGenericBookmarkLastUpdatedOn(bookmarkId: IdType, lastUpdatedOn: Long)

    fun saveBookmarkNote(bookmarkId: IdType, notes: String?) {
        _saveBookmarkNote(bookmarkId, notes)
        saveBookmarkLastUpdatedOn(bookmarkId, System.currentTimeMillis())
    }

    fun saveGenericBookmarkNote(bookmarkId: IdType, notes: String?) {
        _saveGenericBookmarkNote(bookmarkId, notes)
        saveGenericBookmarkLastUpdatedOn(bookmarkId, System.currentTimeMillis())
    }

    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Query("SELECT * from Label WHERE id=:id")
    fun labelById(id: IdType): Label?

    @Query("SELECT * from Label WHERE id IN (:ids)")
    fun labelsById(ids: List<IdType>): List<Label>

    @Query("SELECT * from StudyPadTextEntryWithText WHERE labelId=:id ORDER BY orderNumber")
    fun studyPadTextEntriesByLabelId(id: IdType): List<BookmarkEntities.StudyPadTextEntryWithText>

    @Query("SELECT * from StudyPadTextEntryWithText WHERE id=:id")
    fun studyPadTextEntryById(id: IdType): BookmarkEntities.StudyPadTextEntryWithText?

    @Insert fun insert(entity: BookmarkEntities.StudyPadTextEntry)
    @Insert fun insert(entity: BookmarkEntities.StudyPadTextEntryText)

    @Update fun update(entity: BookmarkEntities.StudyPadTextEntry)
    @Update fun update(entity: BookmarkEntities.StudyPadTextEntryText)

    @Insert fun insert(entity: Label)
    @Insert fun insertLabels(entity: List<Label>): List<Long>

    @Update fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query(
        """
        SELECT Label.* FROM Label 
            JOIN BibleBookmarkToLabel ON Label.id = BibleBookmarkToLabel.labelId 
            JOIN BibleBookmarkWithNotes ON BibleBookmarkToLabel.bookmarkId = BibleBookmarkWithNotes.id 
            WHERE BibleBookmarkWithNotes.id = :bookmarkId
    """
    )
    fun labelsForBookmark(bookmarkId: IdType): List<Label>

    fun labelsForBookmark(bookmark: BaseBookmarkWithNotes): List<Label> = when(bookmark) {
        is BibleBookmarkWithNotes -> labelsForBookmark(bookmark.id)
        is GenericBookmarkWithNotes -> labelsForGenericBookmark(bookmark.id)
        else -> throw RuntimeException("Illegal type")
    }

    @Query(
        """
        SELECT Label.* FROM Label 
            JOIN GenericBookmarkToLabel ON Label.id = GenericBookmarkToLabel.labelId 
            JOIN GenericBookmarkWithNotes ON GenericBookmarkToLabel.bookmarkId = GenericBookmarkWithNotes.id 
            WHERE GenericBookmarkWithNotes.id = :bookmarkId
    """
    )
    fun labelsForGenericBookmark(bookmarkId: IdType): List<Label>

    @Query("""SELECT * FROM BibleBookmarkToLabel WHERE bookmarkId=:bookmarkId""")
    fun getBookmarkToLabelsForBookmark(bookmarkId: IdType): List<BibleBookmarkToLabel>

    @Query("""SELECT * FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId""")
    fun getGenericBookmarkToLabelsForBookmark(bookmarkId: IdType): List<GenericBookmarkToLabel>

    fun getBookmarkToLabelsForBookmark(bookmark: BaseBookmarkWithNotes): List<BaseBookmarkToLabel> =
        when(bookmark) {
            is BibleBookmarkWithNotes -> getBookmarkToLabelsForBookmark(bookmark.id)
            is GenericBookmarkWithNotes -> getGenericBookmarkToLabelsForBookmark(bookmark.id)
            else -> throw RuntimeException("Illegal type")
        }

    @Query("""SELECT * FROM BibleBookmarkToLabel WHERE labelId=:labelId ORDER BY orderNumber""")
    fun getBookmarkToLabelsForLabel(labelId: IdType): List<BibleBookmarkToLabel>

    @Query("""SELECT * FROM GenericBookmarkToLabel WHERE labelId=:labelId ORDER BY orderNumber""")
    fun getGenericBookmarkToLabelsForLabel(labelId: IdType): List<GenericBookmarkToLabel>

    @Query("""SELECT * FROM BibleBookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId=:labelId""")
    fun getBibleBookmarkToLabel(bookmarkId: IdType, labelId: IdType): BibleBookmarkToLabel?

    @Query("""SELECT * FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId=:labelId""")
    fun getGenericBookmarkToLabel(bookmarkId: IdType, labelId: IdType): GenericBookmarkToLabel?

    fun getBookmarkToLabel(bookmark: BaseBookmarkWithNotes, labelId: IdType): BaseBookmarkToLabel? = when(bookmark) {
        is BibleBookmarkWithNotes -> getBibleBookmarkToLabel(bookmark.id, labelId)
        is GenericBookmarkWithNotes -> getGenericBookmarkToLabel(bookmark.id, labelId)
        else -> throw RuntimeException("Illegal type")
    }

    @Insert fun insert(entity: BibleBookmarkToLabel)

    @Delete fun delete(entity: BibleBookmarkToLabel): Int

    @Update fun update(entity: BibleBookmarkToLabel)
    @Update fun update(entity: GenericBookmarkToLabel)
    fun update(entity: BaseBookmarkToLabel) = when(entity) {
        is BibleBookmarkToLabel -> update(entity)
        is GenericBookmarkToLabel -> update(entity)
        else -> throw RuntimeException("Illegal type")
    }

    @Query("DELETE FROM BibleBookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun clearLabels(bookmarkId: IdType)

    @Query("DELETE FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun clearLabelsGeneric(bookmarkId: IdType)
    fun clearLabels(bookmark: BaseBookmarkWithNotes) = when(bookmark) {
        is BibleBookmarkWithNotes -> clearLabels(bookmark.id)
        is GenericBookmarkWithNotes -> clearLabelsGeneric(bookmark.id)
        else -> throw RuntimeException("Illegal type")
    }
    fun clearLabels(bookmark: BibleBookmarkWithNotes) = clearLabels(bookmark.id)

    @Delete fun delete(entities: List<BibleBookmarkToLabel>): Int

    @Delete fun delete(e: BookmarkEntities.StudyPadTextEntry)

    @Query("DELETE FROM BibleBookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId IN (:labels)")
    fun _deleteLabelsFromBookmark(bookmarkId: IdType, labels: List<IdType>): Int
    @Query("DELETE FROM GenericBookmarkToLabel WHERE bookmarkId=:bookmarkId AND labelId IN (:labels)")
    fun _deleteLabelsFromGenericBookmark(bookmarkId: IdType, labels: List<IdType>): Int
    fun deleteLabelsFromBookmark(bookmarkId: IdType, labels: List<IdType>): Int {
        if (labels.isEmpty()) return 0
        return _deleteLabelsFromBookmark(bookmarkId, labels)
    }

    fun deleteLabelsFromBookmark(bookmark: BaseBookmarkWithNotes, labels: List<IdType>): Int {
        if (labels.isEmpty()) return 0
        return when(bookmark) {
            is BibleBookmarkWithNotes -> _deleteLabelsFromBookmark(bookmark.id, labels)
            is GenericBookmarkWithNotes -> _deleteLabelsFromGenericBookmark(bookmark.id, labels)
            else -> throw RuntimeException("Illegal type")
        }
    }

    fun deleteLabelsFromBookmark(bookmark: BibleBookmarkWithNotes, labels: List<Label>): Int = deleteLabelsFromBookmark(bookmark.id, labels.map { it.id })

    @Insert fun insertBookmarkToLabels(entities: List<BibleBookmarkToLabel>)
    @Insert fun insertGenericBookmarkToLabels(entities: List<GenericBookmarkToLabel>)

    @Query("SELECT * from Label WHERE name = '${SPEAK_LABEL_NAME}' LIMIT 1")
    fun speakLabelByName(): Label?

    @Query("SELECT * from Label WHERE name = '${UNLABELED_NAME}' LIMIT 1")
    fun unlabeledLabelByName(): Label?

    @Query("DELETE FROM BibleBookmarkToLabel WHERE bookmarkId=:bookmarkId")
    fun deleteLabels(bookmarkId: IdType)
    fun deleteLabels(bookmark: BibleBookmarkWithNotes) = deleteLabels(bookmark.id)

    @Query("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE labelId=:labelId")
    fun countBookmarkEntities(labelId: IdType): Int

    @Query("SELECT COUNT(*) FROM GenericBookmarkToLabel WHERE labelId=:labelId")
    fun countGenericBookmarkEntities(labelId: IdType): Int

    @Query("SELECT COUNT(*) FROM StudyPadTextEntryWithText WHERE labelId=:labelId")
    fun countStudyPadTextEntities(labelId: IdType): Int

    fun countStudyPadEntities(labelId: IdType) = countBookmarkEntities(labelId) + countGenericBookmarkEntities(labelId)+ countStudyPadTextEntities(labelId)

    @Query("DELETE FROM Label WHERE id IN (:toList)")
    fun deleteLabelsByIds(toList: List<IdType>)

    @Update fun updateBibleBookmarkToLabels(bookmarkToLabels: List<BibleBookmarkToLabel>)
    @Update fun updateGenericBookmarkToLabels(bookmarkToLabels: List<GenericBookmarkToLabel>)

    @Update fun updateStudyPadTextEntries(studyPadTextEntries: List<BookmarkEntities.StudyPadTextEntry>)

    @Query("SELECT * FROM GenericBookmarkWithNotes WHERE bookInitials=:document AND `key`=:key")
    fun genericBookmarksFor(document: String, key: String): List<GenericBookmarkWithNotes>
    fun genericBookmarksFor(document: Book, key: Key): List<GenericBookmarkWithNotes> =
        genericBookmarksFor(document.initials, key.osisRef)
}
