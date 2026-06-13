/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.page

import android.content.Intent
import android.util.Log
import net.bible.android.common.toV11n
import net.bible.android.control.progress.ProgressControl
import net.bible.service.common.ReadingProgressSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.database.WorkspaceEntities
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.service.common.shortName
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.doesNotExist
import net.bible.service.download.isSpecial
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeySerialized
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookFilters
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

open class CurrentCommentaryPage internal constructor(
    currentBibleVerse: CurrentBibleVerse,
    bibleTraverser: BibleTraverser,
    pageManager: CurrentPageManager
) : VersePage(true, currentBibleVerse, bibleTraverser, pageManager), CurrentPage
{

    override val documentCategory = DocumentCategory.COMMENTARY
    var sourceBookAndKey: BookAndKey? = null

    override fun startKeyChooser(context: ActivityBase) =
        context.startActivityForResult(Intent(context, GridChoosePassageBook::class.java).apply { putExtra("isScripture", true) }, STD_REQUEST_CODE)

    private val isSpecialDoc: Boolean get() = currentDocument?.isSpecial == true

    override val currentPageContent: Document
        get() {
            return if(currentDocument == FakeBookFactory.compareDocument) {
                val key: VerseRange = when(val origKey = originalVerseRange ?: singleKey) {
                    is VerseRange -> origKey
                    is Verse -> VerseRange(origKey.versification, origKey, origKey)
                    else -> throw RuntimeException("Invalid type")
                }

                val frags = Books.installed().getBooks(BookFilters.getBibles()).map {
                    try {
                        OsisFragment(SwordContentFacade.readOsisFragment(it, key.toV11n((it as SwordBook).versification)), key, it)
                    } catch (e: OsisError) {
                        null
                    }
                }.filterNotNull()
                MultiFragmentDocument(frags, compare=true)
            } else if (currentDocument == FakeBookFactory.memorizeDocument) {
                val bookAndKey = sourceBookAndKey
                    ?: return ErrorDocument("Memorize: sourceBookAndKey.key should be of type VerseRange", ErrorSeverity.ERROR)
                val doc = bookAndKey.document
                val verseRange = bookAndKey.key as? VerseRange
                    ?: return ErrorDocument("Memorize: sourceBookAndKey.key should be of type VerseRange", ErrorSeverity.ERROR)
                val texts = ArrayList<Pair<String, String>>()
                for (verse in verseRange) {
                    val text = SwordContentFacade.getCanonicalText(doc, verse)
                    texts.add(Pair(verse.shortName, text))
                }
                val kjvRange = verseRange.toV11n(KJVA)
                val v11n = verseRange.versification
                val memorizedOrdinals = ProgressControl.getMemorizedOrdinalsInRange(kjvRange.start.ordinal, kjvRange.end.ordinal)
                    .map { Verse(KJVA, it).toV11n(v11n).ordinal }
                val targetOrdinals = ProgressControl.getTargetOrdinalsInRange(kjvRange.start.ordinal, kjvRange.end.ordinal)
                    .map { Verse(KJVA, it).toV11n(v11n).ordinal }
                MemorizeDocument(
                    verseRange.name, texts, pageManager.jsState,
                    bookInitials = doc?.initials,
                    v11nName = v11n.name,
                    osisRef = verseRange.osisRef,
                    startOrdinal = verseRange.start.ordinal,
                    endOrdinal = verseRange.end.ordinal,
                    memorizedOrdinals = memorizedOrdinals,
                    targetOrdinals = targetOrdinals,
                    readingProgressSettingsJson = ReadingProgressSettings.getBundleAsJson(),
                )
            } else super.currentPageContent
        }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
    override fun next() {
        Log.i(TAG, "Next")
        if (!navigateByBlock(forward = true)) nextVerse()
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
    override fun previous() {
        Log.i(TAG, "Previous")
        if (!navigateByBlock(forward = false)) previousVerse()
    }

    private fun nextVerse() {
        originalVerseRange = null
        setKey(getKeyPlus(1))
    }

    private fun previousVerse() {
        originalVerseRange = null
        setKey(getKeyPlus(-1))
    }

    /** A resolver for the current commentary document, or null for non-commentary / special docs. */
    private fun blockResolver(): CommentaryBlockResolver? {
        val doc = currentDocument
        if (doc == null || doc.isSpecial || doc.doesNotExist) return null
        val book = doc as? AbstractPassageBook ?: return null
        if (book.bookCategory != BookCategory.COMMENTARY) return null
        return CommentaryBlockResolver(SwordCommentaryWalker(book, bibleTraverser))
    }

    /**
     * Navigates to the next/previous content block. Returns false when block navigation does not
     * apply (special document or non-commentary), so the caller falls back to verse navigation.
     * Returns true (handled) even at a boundary, where it stays put.
     */
    private fun navigateByBlock(forward: Boolean): Boolean {
        val resolver = blockResolver() ?: return false
        val currentVerse = currentBibleVerse.getVerseSelected(versification)
        val block = resolver.resolveBlock(currentVerse)
        val target = if (forward) resolver.nextBlockStart(block.end) else resolver.prevBlockStart(block.start)
        if (target != null) {
            originalVerseRange = null
            setKey(target)
        }
        return true
    }

    override fun commentaryRangeFor(key: Key): CommentaryRangeInfo? {
        val resolver = blockResolver() ?: return null
        val verse = KeyUtil.getVerse(key)
        val block = resolver.resolveBlock(verse)
        if (block.content == null) return null
        val name = if (block.start == block.end) block.start.name
            else VerseRange(versification, block.start, block.end).name
        return CommentaryRangeInfo(block.start.osisRef, block.end.osisRef, name)
    }

    /** Start verse of the next block after the block containing [verse], or null at the end. */
    fun nextBlockStart(verse: Verse): Verse? {
        val resolver = blockResolver() ?: return null
        return resolver.nextBlockStart(resolver.resolveBlock(verse).end)
    }

    /** Start verse of the previous block before the block containing [verse], or null at the start. */
    fun prevBlockStart(verse: Verse): Verse? {
        val resolver = blockResolver() ?: return null
        return resolver.prevBlockStart(resolver.resolveBlock(verse).start)
    }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Verse {
        var num = num
        val v11n = versification
        val currVer = currentBibleVerse.getVerseSelected(v11n)
        return try {
            var nextVer = currVer
            if (num >= 0) { // move to next book or chapter if required
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getNextVerse(currentPassageBook, nextVer)
                }
            } else { // move to next book if required
                     // allow standard loop structure by changing num to positive
                num = -num
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getPrevVerse(currentPassageBook, nextVer)
                }
            }
            nextVer
        } catch (nsve: Exception) {
            Log.e(TAG, "Incorrect verse", nsve)
            currVer
        }
    }
    override val isSpeakable: Boolean get() = !isSpecialDoc

    override val isSyncable: Boolean get() = currentDocument != FakeBookFactory.memorizeDocument

    // If a passage (that is not just a single verse) is displayed, it is stored here.
    var originalVerseRange: VerseRange? = null

    override fun doSetKey(key: Key?) {
        if(key is VerseRange) {
            originalVerseRange = key
        }
        if(key != null) {
            val verse = KeyUtil.getVerse(key)
            currentBibleVerse.setVerseSelected(versification, verse)
        }
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
    override val key: Key get() = currentBibleVerse.getVerseSelected(versification)

    override val isSingleKey = true

    /** can we enable the main menu search button
     */
    override val isSearchable get() = !isSpecialDoc

    val entity get() =
        WorkspaceEntities.CommentaryPage(currentDocument?.initials, anchorOrdinal?.start, sourceBookAndKey?.serialized)

    fun restoreFrom(entity: WorkspaceEntities.CommentaryPage?) {
        if(entity == null) return
        val document = entity.document
        val book = when(document) {
            FakeBookFactory.compareDocument.initials -> FakeBookFactory.compareDocument
            FakeBookFactory.memorizeDocument.initials -> FakeBookFactory.memorizeDocument
            else -> SwordDocumentFacade.getDocumentByInitials(document) ?: if(document != null) FakeBookFactory.giveDoesNotExist(document) else null
        }
        if(book != null) {
            Log.i(TAG, "Restored document:" + book.name)
            // bypass setter to avoid automatic notifications.
            // Also let's not use localSetCurrentDocument, because we don't want to set the verse.
            // It is already set correctly when CurrentBiblePage is restored.
            // Otherwise versification will be messed up!
            onlySetCurrentDocument(book)
            anchorOrdinal = entity.anchorOrdinal?.let { OrdinalRange(it) }
            sourceBookAndKey = entity.sourceBookAndKey?.let { BookAndKeySerialized.fromJSON(it).bookAndKey }
        }
    }

    companion object {
        private const val TAG = "CurrentCommentaryPage"
    }
}
