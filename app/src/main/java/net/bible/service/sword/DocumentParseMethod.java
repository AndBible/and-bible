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

package net.bible.service.sword;

import java.util.HashMap;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseKey;
import org.crosswire.jsword.versification.BibleBook;

import android.util.Log;

/** Record which documents have bad xml, normally in first or last chapters and use slightly slower JSword parser with error recovery
 *		we have a fast way of handling OSIS zText docs but the following need the superior JSword error recovery for mismatching tags 
 *			FreCrampon
 *			AB
 *			FarsiOPV
 *			Afr1953
 *			UKJV
 *			WEB
 *			HNV
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DocumentParseMethod {
	
	private enum FailPosition {	NONE, 
//								FIRST_BIBLE_CHAPTER, LAST_BIBLE_CHAPTER, FIRST_AND_LAST_BIBLE_CHAPTER, 
//								FIRST_BOOK_CHAPTER,  LAST_BOOK_CHAPTER,  
								FIRST_AND_LAST_BOOK_CHAPTER, 
								ALL}
	
	private Map<String, FailPosition> failureInfoMap = new HashMap<String, FailPosition>();
	
	private static final String TAG = "DocumentParseMethod";
	
	public DocumentParseMethod() {
		failureInfoMap.put("FreCrampon", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);		
		failureInfoMap.put("AB", FailPosition.ALL);	
		failureInfoMap.put("FarsiOPV", FailPosition.ALL);	
		//Afr1953 only has trouble with Gen 1 and Rev 22
		failureInfoMap.put("Afr1953", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);
		failureInfoMap.put("UKJV", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);
		//WEB has trouble with Gen 1 and Rev 22, is okay for much of NT but books like Hosea are also misformed
		failureInfoMap.put("WEB", FailPosition.ALL);
		//HNV only has trouble with Rev 22
		failureInfoMap.put("HNV", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);
		failureInfoMap.put("BulVeren", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);
		failureInfoMap.put("BulCarigradNT", FailPosition.FIRST_AND_LAST_BOOK_CHAPTER);
	}
	
	/** return true if this book's chapter is believed to have a good xml structure and not require recovery fallback
	 */
	public boolean isFastParseOkay(Book document, Key key) {
		boolean isFastParseOkay = false;
		FailPosition documentFailPosition = failureInfoMap.get(document.getInitials());
		if (documentFailPosition==null) {
			isFastParseOkay = true;
		} else {
			
			switch (documentFailPosition) {
			case NONE:
				// should never come here
				isFastParseOkay = true;
				break;
			case ALL:
				isFastParseOkay = false;
				break;
			case FIRST_AND_LAST_BOOK_CHAPTER:
				isFastParseOkay = !isStartOrEndOfBook(key);
				break;
			}
		}
		return isFastParseOkay;
	}

	/** a document has bad xml structure so record the fact so the default fault tolerant parser is used in the future
	 * many books have extra tags in first and/or last chapters hence the graded level of failures
	 */
	public void failedToParse(Book document, Key key) {
		String initials = document.getInitials();
		FailPosition documentFailPosition = failureInfoMap.get(initials);

		if (isStartOrEndOfBook(key)) {
			documentFailPosition = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER;
		} else {
			documentFailPosition = FailPosition.ALL;
		}
		
		failureInfoMap.put(initials, documentFailPosition);
	}
	
	private boolean isStartOrEndOfBook(Key key) {
		boolean isStartOrEnd = false;
		try {
			if (key instanceof VerseKey) {
				Verse verse = KeyUtil.getVerse(key);
				
				int chapter = verse.getChapter();
				BibleBook book = verse.getBook();
				isStartOrEnd = 	chapter == 1 || chapter == verse.getVersification().getLastChapter(book);
			}
		} catch (Exception e) {
			Log.e(TAG, "Verse error", e);
			isStartOrEnd = false;
		}
		return isStartOrEnd;
	}
}
