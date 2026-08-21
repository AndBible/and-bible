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
package net.bible.android.control.page

import android.util.Log
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.window.Window
import net.bible.android.control.versification.BibleTraverser
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/** Common functionality for Bible and commentary document page types
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

// share the verse holder between the CurrentBiblePage & CurrentCommentaryPage
abstract class VersePage protected constructor(
	shareKeyBetweenDocs: Boolean,
	val currentBibleVerse: CurrentBibleVerse,
	protected val bibleTraverser: BibleTraverser,
    pageManager: CurrentPageManager
) : CurrentPageBase(shareKeyBetweenDocs, pageManager) {

	override var _key: Key? = null

    // Bibles must be a PassageBook
    val versification: Versification
        get() {
            val kjv = Versifications.instance().getVersification("KJV")
            return try { // Bibles must be a PassageBook
                (currentDocument as AbstractPassageBook?)?.versification?: kjv
            } catch (e: Exception) {
                Log.e(TAG, "Error getting versification for Book", e)
                kjv
            }
        }

    val currentPassageBook get() = currentDocument as AbstractPassageBook

    /**
     * A verse page keeps only a single verse (see their doSetKey) of a document that may cover a
     * whole range - a commentary entry spanning several verses - so the location it is at is the
     * range *containing* that verse, not only the range starting at it.
     *
     * The difference matters because window sync moves this page to any verse of the entry on
     * screen: syncing to Heb 11:7 while the entry covering 11:5-8 is displayed must not make the
     * next scroll report of that entry look like a move back to 11:5 (#3866).
     */
    override fun isAtSameLocationAs(key: Key): Boolean {
        val current = this.key ?: return false
        return key.contains(KeyUtil.getVerse(current).toV11n(versification))
    }

    override fun localSetCurrentDocument(doc: Book?) { // update current verse possibly remapped to v11n of new bible
        doc ?: return
        val newDocVersification = (doc as AbstractPassageBook).versification
        val newVerse = currentBibleVerse.getVerseSelected(newDocVersification)
        super.localSetCurrentDocument(doc)
        doSetKey(newVerse)
    }

    /** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
     */
    protected fun onVerseChange(window: Window) {
        if (!isInhibitChangeNotifications) {
            PassageChangeMediator.onCurrentVerseChanged(window)
        }
    }

    companion object {
        private const val TAG = "CurrentPageBase"
    }

}
