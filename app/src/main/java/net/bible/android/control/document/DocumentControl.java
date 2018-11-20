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

package net.bible.android.control.document;

import android.util.Log;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.versification.ConvertibleVerse;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordDocumentFacade;
import net.bible.service.sword.SwordEnvironmentInitialisation;

import org.crosswire.common.util.Filter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;

import java.util.List;

import javax.inject.Inject;

/** Control use of different documents/books/modules - used by front end
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class DocumentControl {

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;
	private SwordDocumentFacade swordDocumentFacade;

	private static final String TAG = "DocumentControl";

	@Inject
	public DocumentControl(ActiveWindowPageManagerProvider activeWindowPageManagerProvider, SwordDocumentFacade swordDocumentFacade) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		this.swordDocumentFacade = swordDocumentFacade;
	}

	/**
	 * user wants to change to a different document/module
	 */
	public void changeDocument(Book newDocument) {
		activeWindowPageManagerProvider.getActiveWindowPageManager().setCurrentDocument( newDocument );
	}

	public void enableManualInstallFolder()
	{
		try
		{
			SwordEnvironmentInitialisation.enableDefaultAndManualInstallFolder();
		} catch (BookException e)
		{
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
		}
	}

	public void turnOffManualInstallFolderSetting() {
		CommonUtils.getSharedPreferences().edit().putBoolean("request_sdcard_permission_pref", false).commit();
	}

	/**
	 * Book is deletable according to the driver if it is in the download dir i.e. not sdcard\jsword
	 * and according to And Bible if it is not currently selected
	 */
	public boolean canDelete(Book document) {
		if (document==null) {
			return false;
		}

		boolean lastBible = BookCategory.BIBLE.equals(document.getBookCategory()) &&
							swordDocumentFacade.getBibles().size()==1;

		return !lastBible &&
				document.getDriver().isDeletable(document);
	}
	
	/** delete selected document, even of current doc (Map and Gen Book only currently) and tidy up CurrentPage
	 */
	public void deleteDocument(Book document) throws BookException {
		swordDocumentFacade.deleteDocument(document);
				
		CurrentPage currentPage = activeWindowPageManagerProvider.getActiveWindowPageManager().getBookPage(document);
		if (currentPage!=null) {
			currentPage.checkCurrentDocumentStillInstalled();
		}
	}
	
	/**
	 * Suggest an alternative dictionary to view or return null
	 */
	public boolean isStrongsInBook() {
		try {
			Book currentBook = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getCurrentDocument();
			// very occasionally the below has thrown an Exception and I don't know why, so I wrap all this in a try/catch
			return currentBook.getBookMetaData().hasFeature(FeatureType.STRONGS_NUMBERS);
		} catch (Exception e) {
			Log.e(TAG, "Error checking for strongs Numbers in book", e);
			return false;
		}
	}

	/**
	 * Are we currently in Bible, Commentary, Dict, or Gen Book mode
	 */
	public BookCategory getCurrentCategory() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getBookCategory();
	}
	
	/**
	 * Show split book/chap/verse buttons in toolbar for Bibles and Commentaries
	 */
	public boolean showSplitPassageSelectorButtons() {
		BookCategory currentCategory = getCurrentCategory();
		return	(BookCategory.BIBLE.equals(currentCategory) ||
				BookCategory.COMMENTARY.equals(currentCategory) ||
				BookCategory.OTHER.equals(currentCategory));
	}
	
	/**
	 * Suggest an alternative bible to view or return null
	 */
	public Book getSuggestedBible() {
		CurrentPageManager currentPageManager = activeWindowPageManagerProvider.getActiveWindowPageManager();
		Book currentBible = currentPageManager.getCurrentBible().getCurrentDocument();
		final ConvertibleVerse requiredVerseConverter = getRequiredVerseForSuggestions();
		
		// only show bibles that contain verse
		Filter<Book> bookFilter = new Filter<Book>() {
			@Override
			public boolean test(Book book) {
				return book.contains(requiredVerseConverter.getVerse(((AbstractPassageBook)book).getVersification()));
			}
		};
		
		return getSuggestedBook(swordDocumentFacade.getBibles(), currentBible, bookFilter, currentPageManager.isBibleShown());
	}

	/** Suggest an alternative commentary to view or return null
	 */
	public Book getSuggestedCommentary() {
		CurrentPageManager currentPageManager = activeWindowPageManagerProvider.getActiveWindowPageManager();
		Book currentCommentary = currentPageManager.getCurrentCommentary().getCurrentDocument();
		final ConvertibleVerse requiredVerseConverter = getRequiredVerseForSuggestions();
		
		// only show commentaries that contain verse - extra checks for TDavid because it always returns true
		Filter<Book> bookFilter = new Filter<Book>() {
			@Override
			public boolean test(Book book) {
				Verse verse = requiredVerseConverter.getVerse(((AbstractPassageBook)book).getVersification());
				if (!book.contains(verse)) {
					return false;
				}

				// book claims to contain the verse but 
				// TDavid has a flawed index and incorrectly claims to contain contents for all books of the bible so only return true if !TDavid or is Psalms
				return !book.getInitials().equals("TDavid") || 
						verse.getBook().equals(BibleBook.PS);
			}
		};

		
		return getSuggestedBook(swordDocumentFacade.getBooks(BookCategory.COMMENTARY), currentCommentary, bookFilter, currentPageManager.isCommentaryShown());
	}

	/** Suggest an alternative dictionary to view or return null
	 */
	public Book getSuggestedDictionary() {
		CurrentPageManager currentPageManager = activeWindowPageManagerProvider.getActiveWindowPageManager();
		Book currentDictionary = currentPageManager.getCurrentDictionary().getCurrentDocument();
		return getSuggestedBook(swordDocumentFacade.getBooks(BookCategory.DICTIONARY), currentDictionary, null, currentPageManager.isDictionaryShown());
	}
	
	/**
	 * Possible books will often not include the current verse but most will include chap 1 verse 1
	 */
	private ConvertibleVerse getRequiredVerseForSuggestions() {
		Verse currentVerse = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible().getSingleKey();
		return new ConvertibleVerse(currentVerse.getBook(), 1, 1);
	}

	/**
	 * Suggest an alternative document to view or return null
	 */
	private Book getSuggestedBook(List<Book> books, Book currentDocument, Filter<Book> filter, boolean isBookTypeShownNow) {
		Book suggestion = null;
		if (!isBookTypeShownNow) {
			// allow easy switch back to current doc
			suggestion = currentDocument;
		} else {
			// only suggest alternative if more than 1
			if (books.size()>1) {
				// find index of current document
				int currentDocIndex = -1;
				for (int i=0; i<books.size(); i++) {
					if (books.get(i).equals(currentDocument)) {
						currentDocIndex = i;
					}
				}
				
				// find the next doc containing related content e.g. if in NT then don't show TDavid
				for (int i=0; i<books.size()-1 && suggestion==null; i++) {
					Book possibleDoc = books.get((currentDocIndex+i+1)%books.size());
					
					if (filter==null || filter.test(possibleDoc)) {
						 suggestion = possibleDoc;
					}
				}
			}
		}
		
		return suggestion;
	}
}
