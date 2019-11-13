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

import android.content.SharedPreferences;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.control.speak.SpeakSettings;
import net.bible.android.control.versification.VersificationConverter;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Constants;
import net.bible.service.common.Logger;
import net.bible.service.common.ParseException;
import net.bible.service.css.CssControl;
import net.bible.service.device.speak.SpeakCommand;
import net.bible.service.device.speak.SpeakCommandArray;
import net.bible.service.font.FontControl;
import net.bible.service.format.HtmlMessageFormatter;
import net.bible.service.format.Note;
import net.bible.service.format.OSISInputStream;
import net.bible.service.format.SaxParserPool;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.*;
import net.bible.service.format.usermarks.BookmarkFormatSupport;
import net.bible.service.format.usermarks.MyNoteFormatSupport;

import org.crosswire.common.xml.JDOMSAXEventProvider;
import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.jdom2.Document;
import org.jdom2.Element;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.xml.parsers.SAXParser;

/** JSword facade
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class SwordContentFacade {

	private DocumentParseMethod documentParseMethod = new DocumentParseMethod();

	private SaxParserPool saxParserPool = new SaxParserPool();

	private final BookmarkFormatSupport bookmarkFormatSupport;

	private final MyNoteFormatSupport myNoteFormatSupport;

	private CssControl cssControl = new CssControl();

	private static final String TAG = "SwordContentFacade";

	// set to false for testing
	private static boolean isAndroid = true; //CommonUtils.isAndroid();
	
    private static final Logger log = new Logger(SwordContentFacade.class.getName());

	@Inject
	public SwordContentFacade(BookmarkFormatSupport bookmarkFormatSupport, MyNoteFormatSupport myNoteFormatSupport) {
		this.bookmarkFormatSupport = bookmarkFormatSupport;
		this.myNoteFormatSupport = myNoteFormatSupport;
	}
	
	/** top level method to fetch html from the raw document data
	 */
	public String readHtmlText(Book book, Key key, boolean asFragment) throws ParseException
	{
		String retVal = "";
		if (book==null || key==null) {
			retVal = "";
		} else if (Books.installed().getBook(book.getInitials())==null) {
			Log.w(TAG, "Book may have been uninstalled:"+book);
			String errorMsg = BibleApplication.Companion.getApplication().getString(R.string.document_not_installed, book.getInitials());
			retVal = HtmlMessageFormatter.Companion.format(errorMsg);
		} else if (!bookContainsAnyOf(book, key)) {
			Log.w(TAG, "KEY:"+key.getOsisID()+" not found in doc:"+book);
			retVal = HtmlMessageFormatter.Companion.format(R.string.error_key_not_in_document);
		} else {

			// we have a fast way of handling OSIS zText docs but some docs need the superior JSword error recovery for mismatching tags 
			// try to parse using optimised method first if a suitable document and it has not failed previously
			boolean isParsedOk = false;
			if ("OSIS".equals(book.getBookMetaData().getProperty("SourceType")) &&
				"zText".equals(book.getBookMetaData().getProperty("ModDrv")) &&
				documentParseMethod.isFastParseOkay(book, key)) {
				try {
					retVal = readHtmlTextOptimizedZTextOsis(book, key, asFragment);
					isParsedOk = true;
				} catch (ParseException pe) {
					documentParseMethod.failedToParse(book, key);
				}
			} 
			
			// fall back to slightly slower JSword method with JSword's fallback approach of removing all tags
			if (!isParsedOk) {
				retVal = readHtmlTextStandardJSwordMethod(book, key, asFragment);
			}
		}
		return retVal;
	}

	/** Get Footnotes and references from specified document page
	 */
	public List<Note> readFootnotesAndReferences(Book book, Key key) throws ParseException {
		List<Note> retVal = new ArrayList<>();
		try {
			// based on standard JSword SAX handling method because few performance gains would be gained for the extra complexity of Streaming
			BookData data = new BookData(book, key);		
			SAXEventProvider osissep = data.getSAXEventProvider();
			if (osissep != null) {
				OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book, key, true);
		
				osissep.provideSAXEvents(osisToHtml);
		
				retVal = osisToHtml.getNotesList();
			} else {
				Log.e(TAG, "No osis SEP returned");
			}
	        return retVal;
		} catch (Exception e) {
			log.error("Parsing error", e);
			throw new ParseException("Parsing error", e);
		}
		
	}
	
	/**
	 * Use OSISInputStream which loads a single verse at a time as required.
	 * This reduces memory requirements compared to standard JDom SaxEventProvider 
	 */
	protected String readHtmlTextOptimizedZTextOsis(Book book, Key key, boolean asFragment) throws ParseException
	{
		log.debug("Using fast method to fetch document data");
		/*
		  When you supply an InputStream, the SAX implementation wraps the stream in an InputStreamReader;
		  then SAX automatically detects the correct character encoding from the stream. You can then omit the setEncoding() step,
		  reducing the method invocations once again. The result is an application that is faster, and always has the correct character encoding.
		 */
		InputStream is = new OSISInputStream(book, key);

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book, key, asFragment);

		SAXParser parser = null;
		try {
			parser = saxParserPool.obtain();
			parser.parse(is, osisToHtml);
		} catch (Exception e) {
			log.error("Parsing error", e);
			throw new ParseException("Parsing error", e);
		} finally {
			saxParserPool.recycle(parser);
		}
		
		return osisToHtml.toString();
	}

	protected String readHtmlTextStandardJSwordMethod(Book book, Key key, boolean asFragment) throws ParseException
	{
		log.debug("Using standard JSword to fetch document data");
		String retVal;

		try {
			BookData data = new BookData(book, key);		
			SAXEventProvider osissep = data.getSAXEventProvider();
			if (osissep == null) {
				Log.e(TAG, "No osis SEP returned");
				retVal = "Error fetching osis SEP";
			} else {
				OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book, key, asFragment);
		
				osissep.provideSAXEvents(osisToHtml);
		
				retVal = osisToHtml.toString();
			}		
	        return retVal;
		} catch (Exception e) {
			log.error("Parsing error", e);
			throw new ParseException("Parsing error", e);
		}
	}

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     * 
     * @param book
     *            the book to use
     * @param key
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getCanonicalText(Book book, Key key) throws NoSuchKeyException, BookException, ParseException {
    	try {
			BookData data = new BookData(book, key);
			SAXEventProvider osissep = data.getSAXEventProvider();

			ContentHandler osisHandler = new OsisToCanonicalTextSaxHandler();

			osissep.provideSAXEvents(osisHandler);
		
			return osisHandler.toString();
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting text from book" , e);
    		return BibleApplication.Companion.getApplication().getString(R.string.error_occurred);
    	}
    }

	public String getTextWithVerseNumbers(Book book, VerseRange verseRange) throws NoSuchKeyException, BookException, ParseException {
		try {
			BookData data = new BookData(book, verseRange);
			SAXEventProvider osissep = data.getSAXEventProvider();

			boolean showVerseNumbers = verseRange.toVerseArray().length > 1 &&
					CommonUtils.INSTANCE.getSharedPreferences().getBoolean("show_verseno_pref", true);

			ContentHandler osisHandler = new OsisToCopyTextSaxHandler(showVerseNumbers);
			osissep.provideSAXEvents(osisHandler);

			return osisHandler.toString();
		} catch (Exception e) {
			Log.e(TAG, "Error getting text from book" , e);
			return BibleApplication.Companion.getApplication().getString(R.string.error_occurred);
		}
	}

	private ArrayList<SpeakCommand> getSpeakCommandsForVerse(SpeakSettings settings, Book book, Key key) {
		try {
			BookData data = new BookData(book, key);
			Element frag = data.getOsisFragment(false);
			Document doc = frag.getDocument();
			if (doc == null) {
				doc = new Document(frag);
			}

			SAXEventProvider osissep = new JDOMSAXEventProvider(doc);
			ContentHandler osisHandler = new OsisToBibleSpeak(settings, book.getLanguage().getCode());
			osissep.provideSAXEvents(osisHandler);
			return ((OsisToBibleSpeak) osisHandler).getSpeakCommands();
		} catch (Exception e) {
			Log.e(TAG, "Error getting text from book" , e);
			return new ArrayList<>();
		}
	}

    public SpeakCommandArray getSpeakCommands(SpeakSettings settings, SwordBook book, Verse verse) {
    	VersificationConverter v11nConverter = new VersificationConverter();
    	Verse verse_ = v11nConverter.convert(verse, book.getVersification());
		SpeakCommandArray lst = new SpeakCommandArray();
		if (verse_.getVerse() == 1) {
			lst.addAll(getSpeakCommandsForVerse(settings, book,
					new Verse(book.getVersification(), verse_.getBook(), verse_.getChapter(), 0)));
		}
		lst.addAll(getSpeakCommandsForVerse(settings, book, verse_));
		return lst;
    }

    /**
     * Get text to be spoken without any markup.
     * 
     * @param book
     *            the book to use
     * @param key
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getTextToSpeak(Book book, Key key) {
    	try {
			BookData data = new BookData(book, key);
			SAXEventProvider osissep = data.getSAXEventProvider();
		
			boolean sayReferences = BookCategory.GENERAL_BOOK.equals(book.getBookCategory());
			ContentHandler osisHandler = new OsisToSpeakTextSaxHandler(sayReferences);

			osissep.provideSAXEvents(osisHandler);
		
			return osisHandler.toString();
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting text from book" , e);
    		return BibleApplication.Companion.getApplication().getString(R.string.error_occurred);
    	}
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     * 
     * @param book
     *            the book to use
     * @param reference
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getPlainText(Book book, String reference) throws BookException, NoSuchKeyException {
    	String plainText = "";
    	try {
    		if (book != null) {
		        Key key = book.getKey(reference);
		        plainText = getPlainText(book, key);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting plain text", e);
    	}
    	return plainText;
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     * 
     * @param book
     *            the book to use
     * @param key
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getPlainText(Book book, Key key) throws BookException, NoSuchKeyException {
    	String plainText = "";
    	try {
    		if (book != null) {
		        plainText = getCanonicalText(book, key);

				// trim any preceeding spaces that make the final output look uneven
				plainText = plainText.trim();
			}
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting plain text", e);
    	}
    	return plainText;
    }

    public Key search(Book bible, String searchText) throws BookException {
// 		  example of fetching Strongs ref - only works with downloaded indexes!
//        Book book = getDocumentByInitials("KJV");
//        Key key1 = book.find("strong:h3068");
//        System.out.println("h3068 result count:"+key1.getCardinality());

		Log.d(TAG,	"Searching:"+bible+" Search term:" + searchText);
		
		// This does a standard operator search. See the search
		// documentation for more examples of how to search
		Key key = bible.find(searchText); //$NON-NLS-1$

		Log.d(TAG,	"There are "+key.getCardinality()+" verses containing " + searchText);

		return key;

	}

	private OsisToHtmlSaxHandler getSaxHandler(Book book, Key key, boolean asFragment) {
		OsisToHtmlParameters osisToHtmlParameters = new OsisToHtmlParameters();
		BookCategory bookCategory = book.getBookCategory();
		BookMetaData bmd = book.getBookMetaData();

		osisToHtmlParameters.setAsFragment(asFragment);
		osisToHtmlParameters.setLeftToRight(bmd.isLeftToRight());
		osisToHtmlParameters.setLanguageCode(book.getLanguage().getCode());
		osisToHtmlParameters.setModuleBasePath(book.getBookMetaData().getLocation());
		
		// If Bible or Commentary then set Basis for partial references to current Key/Verse 
		if (BookCategory.BIBLE.equals(bookCategory) || BookCategory.COMMENTARY.equals(bookCategory)) {
			osisToHtmlParameters.setBasisRef(key);
			osisToHtmlParameters.setDocumentVersification(((AbstractPassageBook)book).getVersification());

			// only show chapter divider in Bibles
			osisToHtmlParameters.setShowChapterDivider(BookCategory.BIBLE.equals(bookCategory));
			// but commentaries also have a verse tag which requires a chapter part
			osisToHtmlParameters.setChapter(KeyUtil.getVerse(key).getChapter());
		}

		if (isAndroid) {
	    	// HunUj has an error in that refs are not wrapped so automatically add notes around refs
	    	osisToHtmlParameters.setAutoWrapUnwrappedRefsInNote("HunUj".equals(book.getInitials()));
	    	
			SharedPreferences preferences = CommonUtils.INSTANCE.getSharedPreferences();
			if (preferences!=null) {
				// prefs applying to any doc type
				osisToHtmlParameters.setShowNotes(preferences.getBoolean("show_notes_pref", false));
				osisToHtmlParameters.setRedLetter(preferences.getBoolean("red_letter_pref", false));
				osisToHtmlParameters.setCssStylesheetList( cssControl.getAllStylesheetLinks() );

				// show verse numbers if user has selected to show verse numbers AND the book is a bible (so don't even try to show verses in a Dictionary)
				if (BookCategory.BIBLE.equals(bookCategory)) {
					osisToHtmlParameters.setShowVerseNumbers(preferences.getBoolean("show_verseno_pref", true) && BookCategory.BIBLE.equals(bookCategory));
					osisToHtmlParameters.setVersePerline(preferences.getBoolean("verse_per_line_pref", false));
					osisToHtmlParameters.setShowMyNotes(preferences.getBoolean("show_mynotes_pref", true));
					osisToHtmlParameters.setShowBookmarks(preferences.getBoolean("show_bookmarks_pref", true));
					osisToHtmlParameters.setDefaultBookmarkStyle(BookmarkStyle.valueOf(preferences.getString("default_bookmark_style_pref", BookmarkStyle.YELLOW_STAR.name())));
					osisToHtmlParameters.setShowTitles(preferences.getBoolean("section_title_pref", true));
					osisToHtmlParameters.setVersesWithNotes(myNoteFormatSupport.getVersesWithNotesInPassage(key));
					osisToHtmlParameters.setBookmarkStylesByBookmarkedVerse(bookmarkFormatSupport.getVerseBookmarkStylesInPassage(key));

					// showMorphology depends on showStrongs to allow the toolbar toggle button to affect both strongs and morphology
					boolean showStrongs = preferences.getBoolean("show_strongs_pref", true);
					osisToHtmlParameters.setShowStrongs(showStrongs);
					osisToHtmlParameters.setShowMorphology(showStrongs && preferences.getBoolean("show_morphology_pref", false));
				}
				
				if (BookCategory.DICTIONARY.equals(bookCategory)) {
					if (book.hasFeature(FeatureType.HEBREW_DEFINITIONS)) {
						//add allHebrew refs link
						String prompt = BibleApplication.Companion.getApplication().getString(R.string.all_hebrew_occurrences);
						osisToHtmlParameters.setExtraFooter("<br /><a href='"+Constants.ALL_HEBREW_OCCURRENCES_PROTOCOL+":"+key.getName()+"' class='allStrongsRefsLink'>"+prompt+"</a>");

						//convert text refs to links
						osisToHtmlParameters.setConvertStrongsRefsToLinks(true);
					} else if (book.hasFeature(FeatureType.GREEK_DEFINITIONS)) {
						//add allGreek refs link
						String prompt = BibleApplication.Companion.getApplication().getString(R.string.all_greek_occurrences);
						osisToHtmlParameters.setExtraFooter("<br /><a href='"+Constants.ALL_GREEK_OCCURRENCES_PROTOCOL+":"+key.getName()+"' class='allStrongsRefsLink'>"+prompt+"</a>");

						//convert text refs to links
						osisToHtmlParameters.setConvertStrongsRefsToLinks(true);
					}
				}
				
				// which font, if any
				osisToHtmlParameters.setFont(FontControl.getInstance().getFontForBook(book));				
				osisToHtmlParameters.setCssClassForCustomFont(FontControl.getInstance().getCssClassForCustomFont(book));
				
				// indent depth - larger screens have a greater indent
				osisToHtmlParameters.setIndentDepth(CommonUtils.INSTANCE.getResourceInteger(R.integer.poetry_indent_chars));
			}
		}
		return new OsisToHtmlSaxHandler(osisToHtmlParameters);
	}
	
	public static void setAndroid(boolean isAndroid) {
		SwordContentFacade.isAndroid = isAndroid;
	}

	/**
     * When checking a book contains a chapter SwordBook returns false if verse 0 is not in the chapter so this method compensates for that
     * 
     * This can be removed if SwordBook.contains is converted to be containsAnyOf as discussed in JS-273
     */
    private boolean bookContainsAnyOf(Book book, Key key) {
        if (book.contains(key)) {
            return true;
        }

		for (Key aKey : key) {
			if (book.contains(aKey)) {
				return true;
			}
		}
        
        return false;
    }
}
