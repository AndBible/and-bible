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

import android.content.Context;
import android.content.Intent;

import net.bible.android.SharedConstants;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.mynote.MyNoteDAO;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.Logger;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Key;
import org.json.JSONObject;

import javax.inject.Inject;

/** Control instances of the different current document page types
 * Each Window has its own instance of CurrentPageManager, so it is not a singleton.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CurrentPageManager {
	// use the same verse in the commentary and bible to keep them in sync
	private CurrentBibleVerse currentBibleVerse;
	private CurrentBiblePage currentBiblePage;
	private CurrentCommentaryPage currentCommentaryPage;
	private CurrentDictionaryPage currentDictionaryPage;
	private CurrentGeneralBookPage currentGeneralBookPage;
	private CurrentMapPage currentMapPage;
	private CurrentMyNotePage currentMyNotePage;
	private Window window;
	
	private CurrentPage currentDisplayedPage;
	
	private final Logger logger = new Logger(this.getClass().getName());

	@Inject
	public CurrentPageManager(SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade, BibleTraverser bibleTraverser, MyNoteDAO myNoteDAO) {
		currentBibleVerse = new CurrentBibleVerse();
		currentBiblePage = new CurrentBiblePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade);
		currentCommentaryPage = new CurrentCommentaryPage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade);
		currentMyNotePage = new CurrentMyNotePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, myNoteDAO);
		
		currentDictionaryPage = new CurrentDictionaryPage(swordContentFacade, swordDocumentFacade);
		currentGeneralBookPage = new CurrentGeneralBookPage(swordContentFacade, swordDocumentFacade);
		currentMapPage = new CurrentMapPage(swordContentFacade, swordDocumentFacade);
		
		currentDisplayedPage = currentBiblePage;
	}

	public Window getWindow() {
		return window;
	}

	public void setWindow(Window window) {
		this.window = window;
	}
	
	public CurrentPage getCurrentPage() {
		return currentDisplayedPage;
	}
	public CurrentBiblePage getCurrentBible() {
		return currentBiblePage;
	}
	public CurrentCommentaryPage getCurrentCommentary() {
		return currentCommentaryPage;
	}
	public CurrentDictionaryPage getCurrentDictionary() {
		return currentDictionaryPage;
	}
	public CurrentGeneralBookPage getCurrentGeneralBook() {
		return currentGeneralBookPage;
	}
	public CurrentMapPage getCurrentMap() {
		return currentMapPage;
	}
	public CurrentMyNotePage getCurrentMyNotePage() {
		return currentMyNotePage;
	}

	/** 
	 * When navigating books and chapters there should always be a current Passage based book
	 */
	public AbstractPassageBook getCurrentPassageDocument() {
		return getCurrentVersePage().getCurrentPassageBook();
	}
	
	/** 
	 * Get current Passage based page or just return the Bible page
	 */
	public VersePage getCurrentVersePage() {
		VersePage page;
		if (isBibleShown() || isCommentaryShown()) {
			page = (VersePage)getCurrentPage();
		} else {
			page = getCurrentBible();
		}
		return page;
	}

	/** display a new Document and return the new Page
	 */
	public CurrentPage setCurrentDocument(Book nextDocument) {
		CurrentPage nextPage = null;
		if (nextDocument!=null) {
			PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
			
			nextPage = getBookPage(nextDocument);
	
			// is the next doc the same as the prev doc
			Book prevDocInPage = nextPage.getCurrentDocument();
			boolean sameDoc = nextDocument.equals(prevDocInPage);
			
			// must be in this order because History needs to grab the current doc before change
			nextPage.setCurrentDocument(nextDocument);
			currentDisplayedPage = nextPage;
			
			// page will change due to above
			// if there is a valid share key or the doc (hence the key) in the next page is the same then show the page straight away
			if (nextPage.getKey()!=null && (nextPage.isShareKeyBetweenDocs() || sameDoc || nextDocument.contains(nextPage.getKey()))) {
				PassageChangeMediator.getInstance().onCurrentPageChanged(this.window);
			} else {
				Context context = CurrentActivityHolder.getInstance().getCurrentActivity();
				// pop up a key selection screen
		    	Intent intent = new Intent(context, nextPage.getKeyChooserActivity());
		    	context.startActivity(intent);
			}
		} else {
			// should never get here because a doc should always be passed in but I have seen errors lie this once or twice
			nextPage = currentDisplayedPage;
		}
	
		return nextPage;
	}

	/** My Note is different to all other pages.  It has no documents etc but I attempt to make it look a bit like a Commentary page
	 * 
	 * @param verseRange VerseRange to add note to, start verse is the significant key searched for but range is stored
	 */
	public void showMyNote(Key verseRange) {
		setCurrentDocumentAndKey(currentMyNotePage.getCurrentDocument(), verseRange);
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key) {
		return setCurrentDocumentAndKey(currentBook, key, true);
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key, boolean updateHistory) {
		return setCurrentDocumentAndKeyAndOffset(currentBook, key, SharedConstants.NO_VALUE, updateHistory);
	}

	public CurrentPage setCurrentDocumentAndKeyAndOffset(Book currentBook, Key key, float yOffsetRatio) {
		return setCurrentDocumentAndKeyAndOffset(currentBook, key, yOffsetRatio, true);
	}

	private CurrentPage setCurrentDocumentAndKeyAndOffset(Book currentBook, Key key, float yOffsetRatio, boolean updateHistory) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged(updateHistory);

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			try {
				nextPage.setInhibitChangeNotifications(true);
				nextPage.setCurrentDocument(currentBook);
				nextPage.setKey(key);
				nextPage.setCurrentYOffsetRatio(yOffsetRatio);
				currentDisplayedPage = nextPage;
			} finally {
				nextPage.setInhibitChangeNotifications(false);
			}
		}
		// valid key has been set so do not need to show a key chooser therefore just update main view
		PassageChangeMediator.getInstance().onCurrentPageChanged(this.window);

		return nextPage;
	}
	
	public CurrentPage getBookPage(Book book) {
		// book should never be null but it happened on one user's phone
		if (book==null) {
			return null;
		} else if (book.equals(currentMyNotePage.getCurrentDocument())) {
			return currentMyNotePage;
		} else {
			return getBookPage(book.getBookCategory());
		}
		
	}		
	private CurrentPage getBookPage(BookCategory bookCategory) {

		CurrentPage bookPage = null;
		if (bookCategory.equals(BookCategory.BIBLE)) {
			bookPage = currentBiblePage;
		} else if (bookCategory.equals(BookCategory.COMMENTARY)) {
			bookPage = currentCommentaryPage;
		} else if (bookCategory.equals(BookCategory.DICTIONARY)) {
			bookPage = currentDictionaryPage;
		} else if (bookCategory.equals(BookCategory.GENERAL_BOOK)) {
			bookPage = currentGeneralBookPage;
		} else if (bookCategory.equals(BookCategory.MAPS)) {
			bookPage = currentMapPage;
		} else if (bookCategory.equals(BookCategory.OTHER)) {
			bookPage = currentMyNotePage;
		}
		return bookPage;
	}

	public boolean isCommentaryShown() {
		return currentCommentaryPage == currentDisplayedPage;
	}
	public boolean isBibleShown() {
		return currentBiblePage == currentDisplayedPage;
	}
	public boolean isDictionaryShown() {
		return currentDictionaryPage == currentDisplayedPage;
	}
	public boolean isGenBookShown() {
		return currentGeneralBookPage == currentDisplayedPage;
	}
	public boolean isMyNoteShown() {
		return currentMyNotePage == currentDisplayedPage;
	}
	public boolean isMapShown() {
		return currentMapPage == currentDisplayedPage;
	}
	public void showBible() {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged(this.window);
	}

	public JSONObject getStateJson() {
		JSONObject object = new JSONObject();
		try {
			object.put("biblePage", currentBiblePage.getStateJson())
				.put("commentaryPage", currentCommentaryPage.getStateJson())
				.put("dictionaryPage", currentDictionaryPage.getStateJson())
				.put("generalBookPage", currentGeneralBookPage.getStateJson())
				.put("mapPage", currentMapPage.getStateJson())
				.put("currentPageCategory", currentDisplayedPage.getBookCategory().getName());
		} catch (Exception e) {
			logger.warn("Page manager get state error");
		}
		return object;
	}

	public void restoreState(JSONObject jsonObject) {
		try {
			currentBiblePage.restoreState(jsonObject.getJSONObject("biblePage"));
			currentCommentaryPage.restoreState(jsonObject.getJSONObject("commentaryPage"));
			currentDictionaryPage.restoreState(jsonObject.getJSONObject("dictionaryPage"));
			currentGeneralBookPage.restoreState(jsonObject.getJSONObject("generalBookPage"));
			currentMapPage.restoreState(jsonObject.getJSONObject("mapPage"));

			String restoredPageCategoryName = jsonObject.getString("currentPageCategory");
			if (StringUtils.isNotEmpty(restoredPageCategoryName)) {
				BookCategory restoredBookCategory = BookCategory.fromString(restoredPageCategoryName);
				currentDisplayedPage = getBookPage(restoredBookCategory);
			}
		} catch (Exception e) {
			logger.warn("Page manager state restore error");
		}
	}
}
