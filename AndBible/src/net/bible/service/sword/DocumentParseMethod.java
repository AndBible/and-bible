package net.bible.service.sword;

import java.util.HashMap;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

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
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentParseMethod {
	
	private enum FailPosition {	NONE, 
//								FIRST_BIBLE_CHAPTER, LAST_BIBLE_CHAPTER, FIRST_AND_LAST_BIBLE_CHAPTER, 
//								FIRST_BOOK_CHAPTER,  LAST_BOOK_CHAPTER,  
								FIRST_AND_LAST_BOOK_CHAPTER, 
								ALL}
	
	private class DocumentFailInfo {
		String documentInitials;
		FailPosition failPosition;
	}
	
	private Map<String, DocumentFailInfo> failureInfoMap = new HashMap<String, DocumentFailInfo>();
	
	/*
	 */
	
	/** return true if this book's chapter is believed to have a good xml structure and not require recovery fallback
	 */
	public boolean isFastParseOkay(Book document, Key key) {
		boolean isFastParseOkay = false;
		DocumentFailInfo documentFailInfo = failureInfoMap.get(document.getInitials());
		if (documentFailInfo==null) {
			isFastParseOkay = true;
		} else {
			
			switch (documentFailInfo.failPosition) {
			case NONE:
				// should never come here
				isFastParseOkay = true;
				break;
			case ALL:
				isFastParseOkay = false;
				break;
			case FIRST_AND_LAST_BOOK_CHAPTER:
				Verse verse = KeyUtil.getVerse(key);
				isFastParseOkay = !(verse.isStartOfBook() || verse.isEndOfBook());
				break;
			}
		}
		return isFastParseOkay;
	}

	/** a document has bad xml structure so record the fact so the default fault tolerant parser isd used in the future
	 * many books have extra tags in first and/or last chapters hence the graded level of failures
	 */
	public void failedToParse(Book document, Key key) {
		DocumentFailInfo documentFailInfo = failureInfoMap.get(document.getInitials());
		if (documentFailInfo==null) {
			documentFailInfo = new DocumentFailInfo();
		}
		
		documentFailInfo.documentInitials = document.getInitials();

		Verse verse = KeyUtil.getVerse(key);
		if (verse.isStartOfBook() || verse.isEndOfBook()) {
			documentFailInfo.failPosition = FailPosition.FIRST_AND_LAST_BOOK_CHAPTER;
		} else {
			documentFailInfo.failPosition = FailPosition.ALL;
		}
		
		failureInfoMap.put(documentFailInfo.documentInitials, documentFailInfo);
	}
}
