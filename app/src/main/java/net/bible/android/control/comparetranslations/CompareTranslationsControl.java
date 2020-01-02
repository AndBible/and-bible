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

package net.bible.android.control.comparetranslations;

import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.service.common.CommonUtils;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BookName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** Support the Compare Translations screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class CompareTranslationsControl {

	private final BibleTraverser bibleTraverser;

	private final SwordDocumentFacade swordDocumentFacade;
	private final SwordContentFacade swordContentFacade;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String TAG = "CompareTranslationsCtrl";

	@Inject
	public CompareTranslationsControl(BibleTraverser bibleTraverser, SwordDocumentFacade swordDocumentFacade, SwordContentFacade swordContentFacade, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.bibleTraverser = bibleTraverser;
		this.swordDocumentFacade = swordDocumentFacade;
		this.swordContentFacade = swordContentFacade;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}

	public String getTitle(VerseRange verseRange) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean wasFullBookname = BookName.isFullBookName();
		BookName.setFullBookName(false);

		stringBuilder.append(BibleApplication.Companion.getApplication().getString(R.string.compare_translations))
					 .append(": ")
					 .append(CommonUtils.INSTANCE.getKeyDescription(verseRange));

		BookName.setFullBookName(wasFullBookname);
		return stringBuilder.toString();
	}
	
	public void setVerse(Verse verse) {
		getCurrentPageManager().getCurrentBible().doSetKey(verse);
	}

	/** Calculate next verse
	 */
	public VerseRange getNextVerseRange(VerseRange verseRange) {
		 return bibleTraverser.getNextVerseRange(getCurrentPageManager().getCurrentPassageDocument(), verseRange);
	}

	/** Calculate next verse
	 */
	public VerseRange getPreviousVerseRange(VerseRange verseRange) {
		return bibleTraverser.getPreviousVerseRange(getCurrentPageManager().getCurrentPassageDocument(), verseRange);
	}

	/** return the list of verses to be displayed
	 */
	public List<TranslationDto> getAllTranslations(VerseRange verseRange) {
		List<TranslationDto> retval = new ArrayList<>();
		List<Book> books = swordDocumentFacade.getBibles();
		FontControl fontControl = FontControl.getInstance();
		
		ConvertibleVerseRange convertibleVerseRange = new ConvertibleVerseRange(verseRange);
		
		for (Book book : books) {
			try {
				String text = swordContentFacade.getPlainText(book, convertibleVerseRange.getVerseRange(((AbstractPassageBook)book).getVersification()));
				if (text.length()>0) {

					// does this book require a custom font to display it
					File fontFile = null;
					String fontForBook = fontControl.getFontForBook(book);
					if (StringUtils.isNotEmpty(fontForBook)) {
						fontFile = fontControl.getFontFile(fontForBook);
					}
					
					// create DTO with all required info to display this Translation text
					retval.add(new TranslationDto(book, text, fontFile));
				}
			} catch (Exception nske) {
				Log.d(TAG, verseRange+" not in "+book);
			}
		}

		return retval;		
	}
	
	public void showTranslationForVerseRange(TranslationDto translationDto, VerseRange verseRange) {
		getCurrentPageManager().setCurrentDocumentAndKey(translationDto.getBook(), verseRange.getStart());
	}

	public CurrentPageManager getCurrentPageManager() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager();
	}
}
