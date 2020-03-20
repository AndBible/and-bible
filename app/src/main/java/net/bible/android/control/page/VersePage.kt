/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.control.page

import android.util.Log
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
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
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CurrentPageBase(shareKeyBetweenDocs, swordContentFacade, swordDocumentFacade, pageManager) {

	override var _key: Key? = null

    // Bibles must be a PassageBook
    val versification: Versification
        get() = try { // Bibles must be a PassageBook
            (currentDocument as AbstractPassageBook).versification
        } catch (e: Exception) {
            Log.e(TAG, "Error getting versification for Book", e)
            Versifications.instance().getVersification("KJV")
        }

    val currentPassageBook get() = currentDocument as AbstractPassageBook

    override fun localSetCurrentDocument(doc: Book?) { // update current verse possibly remapped to v11n of new bible
        val newDocVersification = (currentDocument as AbstractPassageBook).versification
        val newVerse = currentBibleVerse.getVerseSelected(newDocVersification)
        super.localSetCurrentDocument(doc)
        doSetKey(newVerse)
    }

    /** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
     */
    protected fun onVerseChange() {
        if (!isInhibitChangeNotifications) {
            PassageChangeMediator.getInstance().onCurrentVerseChanged()
        }
    }

    companion object {
        private const val TAG = "CurrentPageBase"
    }

}
