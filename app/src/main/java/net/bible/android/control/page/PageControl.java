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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.ClipboardManager;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.versification.Scripture;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.TitleSplitter;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.util.List;

import javax.inject.Inject;

/**
 * SesionFacade for CurrentPage used by View classes
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class PageControl {
	
	private static final String TAG = "PageControl";
	
	private final  SwordDocumentFacade swordDocumentFacade;
	private final SwordContentFacade swordContentFacade;

	private final DocumentControl documentControl;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	@Inject
	public PageControl(SwordDocumentFacade swordDocumentFacade, SwordContentFacade swordContentFacade, DocumentControl documentControl, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.swordDocumentFacade = swordDocumentFacade;
		this.swordContentFacade = swordContentFacade;
		this.documentControl = documentControl;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}

	/** Paste the current verse to the system clipboard
	 */
	public void copyToClipboard(VerseRange verseRange) {
		try {
			Book book = getCurrentPageManager().getCurrentPage().getCurrentDocument();

			String text = verseRange.getName()+"\n"+swordContentFacade.getTextWithVerseNumbers(book, verseRange);
			ClipboardManager clipboard = (ClipboardManager)BibleApplication.Companion.getApplication().getSystemService(Activity.CLIPBOARD_SERVICE);
			clipboard.setText(text);
		} catch (Exception e) {
			Log.e(TAG, "Error pasting to clipboard", e);
			Dialogs.getInstance().showErrorMsg("Error copying to clipboard");
		}
	}

	/** send the current verse via social applications installed on user's device
	 */
	public void shareVerse(VerseRange verseRange) {
		try {
			Book book = getCurrentPageManager().getCurrentPage().getCurrentDocument();

			String text = verseRange.getName()+"\n"+swordContentFacade.getTextWithVerseNumbers(book, verseRange);
			
			Intent sendIntent  = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("text/plain");

			sendIntent.putExtra(Intent.EXTRA_TEXT, text);
			// subject is used when user chooses to send verse via e-mail
			sendIntent.putExtra(Intent.EXTRA_SUBJECT, BibleApplication.Companion.getApplication().getText(R.string.share_verse_subject));

			Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
			activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share_verse))); 
			
		} catch (Exception e) {
			Log.e(TAG, "Error sharing verse", e);
			Dialogs.getInstance().showErrorMsg("Error sharing verse");
		}
	}
	
	/** This is only called after the very first bible download to attempt to ensure the first page is not 'Verse not found' 
	 * go through a list of default verses until one is found in the first/only book installed
	 */
	public void setFirstUseDefaultVerse() {
		try {
			Versification versification = getCurrentPageManager().getCurrentBible().getVersification();
			Verse[] defaultVerses = new Verse[] {
					new Verse(versification, BibleBook.JOHN,3,16),
					new Verse(versification, BibleBook.GEN,1,1),
					new Verse(versification, BibleBook.PS,1,1)};
	    	List<Book> bibles = swordDocumentFacade.getBibles();
	        if (bibles.size()==1) {
	        	Book bible = bibles.get(0);
	        	for (Verse verse : defaultVerses) {
		        	if (bible.contains(verse)) {
		        		getCurrentPageManager().getCurrentBible().setKey(verse);
		        		return;
		        	}
	        	}
	        }
			
		} catch (Exception e) {
			Log.e(TAG, "Verse error");
		}
	}

	public Verse getCurrentBibleVerse() {
		return getCurrentPageManager().getCurrentBible().getSingleKey();
	}
	
	/** font size may be adjusted for certain fonts e.g. SBLGNT
	 */
	public int getDocumentFontSize(Window window) {
		// get base font size
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		int fontSize = preferences.getInt("text_size_pref", 16);

		// if book has a special font it may require an adjusted font size
		Book book = window.getPageManager().getCurrentPage().getCurrentDocument();
		String font = FontControl.getInstance().getFontForBook(book);
		int fontSizeAdjustment = FontControl.getInstance().getFontSizeAdjustment(font, book);
		
		return fontSize+fontSizeAdjustment;		
	}
	
	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return isStrongsRelevant() &&
			   CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	}

	/** return true if Strongs are relevant to this doc & screen */
	public boolean isStrongsRelevant() {

		return documentControl.isStrongsInBook();
	}

	/**
	 * Return false if current page is not scripture, but only if the page is valid
	 */
	public boolean isCurrentPageScripture() {
		VersePage currentVersePage = getCurrentPageManager().getCurrentVersePage();
		Versification currentVersification = currentVersePage.getVersification();
		BibleBook currentBibleBook = currentVersePage.getCurrentBibleVerse().getCurrentBibleBook();
		boolean isCurrentBibleBookScripture = Scripture.isScripture(currentBibleBook);

		// Non-scriptural pages are not so safe.  They may be synched with the other screen but not support the current dc book 
		return isCurrentBibleBookScripture ||
				!currentVersification.containsBook(currentBibleBook);
	}
	
	public CurrentPageManager getCurrentPageManager() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager();
	}
}
