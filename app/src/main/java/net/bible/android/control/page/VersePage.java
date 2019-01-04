/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page;

import android.util.Log;

import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;


/** Common functionality for Bible and commentary document page types
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class VersePage extends CurrentPageBase {

	private CurrentBibleVerse currentBibleVerse;
	
	private BibleTraverser bibleTraverser;

	private static final String TAG = "CurrentPageBase";
	
	protected VersePage(boolean shareKeyBetweenDocs, CurrentBibleVerse currentVerse, BibleTraverser bibleTraverser, SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade) {
		super(shareKeyBetweenDocs, swordContentFacade, swordDocumentFacade);
		// share the verse holder between the CurrentBiblePage & CurrentCommentaryPage
		this.currentBibleVerse = currentVerse;
		this.bibleTraverser = bibleTraverser;
	}

	public Versification getVersification() {
		try {
			// Bibles must be a PassageBook
			return ((AbstractPassageBook)getCurrentDocument()).getVersification();
		} catch (Exception e) {
			Log.e(TAG, "Error getting versification for Book", e);
			return Versifications.instance().getVersification("KJV");
		}
	}

	public AbstractPassageBook getCurrentPassageBook() {
		return (AbstractPassageBook)getCurrentDocument();
	}
	
	public CurrentBibleVerse getCurrentBibleVerse() {
		return currentBibleVerse;
	}
	
	protected BibleTraverser getBibleTraverser() {
		return bibleTraverser;
	}

	@Override
	protected void localSetCurrentDocument(Book doc) {
		// update current verse possibly remapped to v11n of new bible 
		Versification newDocVersification = ((AbstractPassageBook)getCurrentDocument()).getVersification();
		Verse newVerse = getCurrentBibleVerse().getVerseSelected(newDocVersification);

		super.localSetCurrentDocument(doc);

		doSetKey(newVerse);
	}
	
	/** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
	 */
	protected void onVerseChange() {
		if (!isInhibitChangeNotifications()) {
			PassageChangeMediator.getInstance().onCurrentVerseChanged();
		}
	}
}
