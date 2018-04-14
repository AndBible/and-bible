package net.bible.android.control.link;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchControl.SearchBibleSection;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.SearchIndex;
import net.bible.android.view.activity.search.SearchResults;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.search.SearchType;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.versification.Versification;

import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

/** Control traversal via links pressed by user in a browser e.g. to Strongs
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class LinkControl {

	private WindowControl windowControl;

	private final SearchControl searchControl;

	private SwordDocumentFacade swordDocumentFacade;

	private static final Pattern IBT_SPECIAL_CHAR_RE = Pattern.compile("_(\\d+)_");

	private static final String TAG = "LinkControl";

	public static final String WINDOW_MODE_THIS = "this";
	public static final String WINDOW_MODE_SPECIAL = "special";
	public static final String WINDOW_MODE_NEW = "new";
	public static final String WINDOW_MODE_MAIN = "main";
	public static final String WINDOW_MODE_UNDEFINED = "undefined";

	private String windowMode = WINDOW_MODE_UNDEFINED;

	@Inject
	public LinkControl(WindowControl windowControl, SearchControl searchControl, SwordDocumentFacade swordDocumentFacade) {
		this.windowControl = windowControl;
		this.searchControl = searchControl;
		this.swordDocumentFacade = swordDocumentFacade;
	}



	/** Currently the only uris handled are for Strongs refs
	 * see OSISToHtmlSaxHandler.getStrongsUrl for format of uri
	 * 
	 * @param uri
	 * @return true if successfully changed to Strongs ref
	 */
	public boolean loadApplicationUrl(String uri) {
		try {
			Log.d(TAG, "Loading: "+uri);
			
			// Prevent occasional class loading errors on Samsung devices
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

			UriAnalyzer uriAnalyzer = new UriAnalyzer(); 
			if (uriAnalyzer.analyze(uri)) {
				switch (uriAnalyzer.getDocType()) {
				case BIBLE:
		        	showBible(uriAnalyzer.getKey());
					break;
				case GREEK_DIC:
					showStrongs(swordDocumentFacade.getDefaultStrongsGreekDictionary(), uriAnalyzer.getKey());
					break;
				case HEBREW_DIC:
					showStrongs(swordDocumentFacade.getDefaultStrongsHebrewDictionary(), uriAnalyzer.getKey());
					break;
				case ROBINSON:
					showRobinsonMorphology(uriAnalyzer.getKey());
					break;
				case ALL_GREEK:
		        	showAllOccurrences(uriAnalyzer.getKey(), SearchBibleSection.ALL, "g");
					break;
				case ALL_HEBREW:
					showAllOccurrences(uriAnalyzer.getKey(), SearchBibleSection.ALL, "h");
					break;
				case SPECIFIC_DOC:
					showSpecificDocRef(uriAnalyzer.getBook(), uriAnalyzer.getKey());
					break;
				default:
					return false;
				}
				
			}
	        // handled this url (or at least attempted to)
	        return true;
		} catch (Exception e) {
			Log.e(TAG, "Error going to link", e);
			return false;
		}
	}

	private void showSpecificDocRef(String initials, String ref) throws NoSuchKeyException {
		if (StringUtils.isEmpty(initials)) {
			showBible(ref);
		} else {
			Book document = swordDocumentFacade.getDocumentByInitials(initials);
			if (document==null) {
	        	// tell user to install book
	        	Dialogs.getInstance().showErrorMsg(R.string.document_not_installed, initials);
			} else {
				//Foreign language keys may have been URLEncoded so need to URLDecode them e.g. UZV module at Matthew 1. The first link is "David" (looks a bit like DOBYA)
				ref = URLDecoder.decode(ref);
				
				//According to the OSIS schema, the osisRef attribute can contain letters and "_", but NOT punctuation and NOT spaces
				//IBT dictionary entries sometimes contain spaces but osisrefs can't so _32_ is used
				// e.g.  UZV Matthew 1:18: The link to "Holy Spirit" (Muqaddas Ruhdan)
				ref = replaceIBTSpecialCharacters(ref);
				
				Key bookKey = document.getKey(ref);
		        showLink(document, bookKey);
			}
		}
	}

	/**
	 * IBT use _nn_ for punctuation chars in references to dictionaries e.g. _32_ represents a space so 'Holy_32_Spirit' should be converted to 'Holy Spirit'
	 * @param ref Key e.g. dictionary key
	 * @return ref with _nn_ replaced by punctuation
	 */
	private String replaceIBTSpecialCharacters(String ref) {
		Matcher refIBTSpecialCharMatcher = IBT_SPECIAL_CHAR_RE.matcher(ref);
		StringBuffer output = new StringBuffer();
		while(refIBTSpecialCharMatcher.find()) {
			String specialChar = Character.toString((char)Integer.parseInt(refIBTSpecialCharMatcher.group(1)));
			refIBTSpecialCharMatcher.appendReplacement(output, specialChar);
		}
		refIBTSpecialCharMatcher.appendTail(output);
		return output.toString();
	}

	/** user has selected a Bible verse link
	 */
	private void showBible(String keyText) throws NoSuchKeyException {
		CurrentPageManager pageManager = getCurrentPageManager();
		Book bible = pageManager.getCurrentBible().getCurrentDocument();

		// get source versification
		Versification sourceDocumentVersification;
		Book currentDoc = pageManager.getCurrentPage().getCurrentDocument();
		if (currentDoc instanceof AbstractPassageBook) {
			sourceDocumentVersification = ((AbstractPassageBook)currentDoc).getVersification();
		} else {
			// default to v11n of current Bible.  
			//TODO av11n issue.  GenBooks have no v11n and this default would be used for links from GenBooks which would only sometimes be correct
			sourceDocumentVersification = ((AbstractPassageBook)bible).getVersification();
		}
		
		// create Passage with correct source Versification 
        Key key = PassageKeyFactory.instance().getKey(sourceDocumentVersification, keyText);
        
        // Bible not specified so use the default Bible version
        showLink(null, key);
		
		return;
	}

	/** user has selected a Strong's Number link so show Strong's page for key in link
	 */
	private void showStrongs(Book book, String key) throws NoSuchKeyException {
		
        // valid Strongs uri but Strongs refs not installed
        if (book==null) {
        	Dialogs.getInstance().showErrorMsg(R.string.strongs_not_installed);
        	// this uri request was handled by showing an error message
        	return;
        }

        Key strongsNumberKey = book.getKey(key); 
        showLink(book, strongsNumberKey);
	}

	/** user has selected a morphology link so show morphology page for key in link
	 */
	private void showRobinsonMorphology(String key) throws NoSuchKeyException {
		Book robinson = swordDocumentFacade.getDocumentByInitials("robinson");
        // valid Strongs uri but Strongs refs not installed
        if (robinson==null) {
        	Dialogs.getInstance().showErrorMsg(R.string.morph_robinson_not_installed);
        	// this uri request was handled by showing an error message
        	return;
        }

        Key robinsonNumberKey = robinson.getKey(key); 
        showLink(robinson, robinsonNumberKey);
	}

	private void showAllOccurrences(String ref, SearchBibleSection biblesection, String refPrefix) {
    	Book currentBible = getCurrentPageManager().getCurrentBible().getCurrentDocument();
    	Book strongsBible = null;

    	// if current bible has no Strongs refs then try to find one that has
    	if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
    		strongsBible = currentBible;
    	} else {
    		strongsBible = swordDocumentFacade.getDefaultBibleWithStrongs();
    	}
    	
    	// possibly no Strong's bible or it has not been indexed
    	boolean needToDownloadIndex = false;
    	if (strongsBible == null) {
    		Dialogs.getInstance().showErrorMsg(R.string.no_indexed_bible_with_strongs_ref);
    		return;
    	} else if (currentBible.equals(strongsBible) && !checkStrongs(currentBible)) {
    		Log.d(TAG, "Index status is NOT DONE");
    		needToDownloadIndex = true;
    	}
    	
    	// The below uses ANY_WORDS because that does not add anything to the search string
    	//String noLeadingZeroRef = StringUtils.stripStart(ref, "0");
    	String searchText = searchControl.decorateSearchString("strong:"+refPrefix+ref, SearchType.ANY_WORDS, biblesection, null);
    	Log.d(TAG, "Search text:"+searchText);

    	Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Bundle searchParams = new Bundle();
    	searchParams.putString(SearchControl.SEARCH_TEXT, searchText);
    	searchParams.putString(SearchControl.SEARCH_DOCUMENT, strongsBible.getInitials());
    	searchParams.putString(SearchControl.TARGET_DOCUMENT, currentBible.getInitials());

    	Intent intent = null;
    	if (needToDownloadIndex) {
    	    intent = new Intent(activity, SearchIndex.class);
    	} else {
        	//If an indexed Strong's module is in place then do the search - the normal situation
        	intent = new Intent(activity, SearchResults.class);
    	}
    	
    	intent.putExtras(searchParams);
		activity.startActivity(intent);
	}
	
	/** ensure a book is indexed and the index contains typical Greek or Hebrew Strongs Numbers
	 */
	private boolean checkStrongs(Book bible) {
		try {
			return bible.getIndexStatus().equals(IndexStatus.DONE) &&
				   (bible.find("+[Gen 1:1] strong:h7225").getCardinality()>0 ||
					bible.find("+[John 1:1] strong:g746").getCardinality()>0 ||
					bible.find("+[Gen 1:1] strong:g746").getCardinality()>0);
		} catch (BookException be) {
			Log.e(TAG, "Error checking strongs numbers", be);
			return false;
		}
	}

	private void showLink(Book document, Key key) {
		// ask window controller to open link in desired window
		CurrentPageManager currentPageManager = getCurrentPageManager();
		Window firstWindow = windowControl.getWindowRepository().getFirstWindow();
		Book defaultDocument = currentPageManager.getCurrentBible().getCurrentDocument();

		if(windowMode.equals(WINDOW_MODE_MAIN)) {
			if (document==null) {
				document = defaultDocument;
			}

			windowControl.setActiveWindow(firstWindow);
			firstWindow.getPageManager().setCurrentDocumentAndKey(document, key);
		}
		else if(windowMode.equals(WINDOW_MODE_NEW)) {
			if (document==null) {
				document = defaultDocument;
			}

			windowControl.addNewWindow(document, key);
		}
		else if (checkIfOpenLinksInDedicatedWindow()) {
			if (document==null) {
				windowControl.showLinkUsingDefaultBible(key);
			} else {
				windowControl.showLink(document, key);
			}
		} else {
			// old style - open links in current window
			if (document==null) {
				document = defaultDocument;
			}

			czxurrentPageManager.setCurrentDocumentAndKey(document, key);
		}
	}
	
	private boolean checkIfOpenLinksInDedicatedWindow() {
		switch(windowMode) {
			case WINDOW_MODE_SPECIAL:
				return true;
			case WINDOW_MODE_THIS:
				return false;
			case WINDOW_MODE_UNDEFINED:
			default:
				return CommonUtils.getSharedPreferences().getBoolean("open_links_in_special_window_pref", true);
		}
	}

	private CurrentPageManager getCurrentPageManager() {
		return windowControl.getActiveWindowPageManager();
	}

	public void setWindowMode(String windowMode) {
		this.windowMode = windowMode;
	}
}
