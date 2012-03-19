package net.bible.android.control.link;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchControl.SearchBibleSection;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.SearchIndex;
import net.bible.android.view.activity.search.SearchResults;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.search.SearchType;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Control traversal via links pressed by user in a browser e.g. to Strongs
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class LinkControl {

	private static final String TAG = "LinkControl";
	
	/** Currently the only uris handled are for Strongs refs
	 * see OSISToHtmlSaxHandler.getStrongsUrl for format of uri
	 * 
	 * @param url
	 * @return true if successfully changed to Strongs ref
	 */
	public boolean loadApplicationUrl(String uri) {
		try {
			Log.d(TAG, "Loading: "+uri);

			UriAnalyzer uriAnalyzer = new UriAnalyzer(); 
			if (uriAnalyzer.analyze(uri)) {
				switch (uriAnalyzer.getDocType()) {
				case BIBLE:
		        	showBible(uriAnalyzer.getKey());
					break;
				case GREEK_DIC:
					showStrongs(Defaults.getGreekDefinitions(), uriAnalyzer.getKey());
					break;
				case HEBREW_DIC:
					showStrongs(Defaults.getHebrewDefinitions(), uriAnalyzer.getKey());
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
			Book document = SwordDocumentFacade.getInstance().getDocumentByInitials(initials);
			if (document==null) {
	        	// tell user to install book
	        	Dialogs.getInstance().showErrorMsg(R.string.document_not_installed, initials);
			} else {
				Key bookKey = document.getKey(ref);
				CurrentPageManager.getInstance().setCurrentDocumentAndKey(document, bookKey);
			}
		}
	}

	/** user has selected a Bible verse link
	 */
	private void showBible(String key) throws NoSuchKeyException {
		Book book = CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument();

        Key bibleKey = book.getKey(key); 
   		CurrentPageManager.getInstance().setCurrentDocumentAndKey(book, bibleKey);
		
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
   		CurrentPageManager.getInstance().setCurrentDocumentAndKey(book, strongsNumberKey);
		
		return;
	}

	/** user has selected a morphology link so show morphology page for key in link
	 */
	private void showRobinsonMorphology(String key) throws NoSuchKeyException {
		Book robinson = SwordDocumentFacade.getInstance().getDocumentByInitials("robinson");
        // valid Strongs uri but Strongs refs not installed
        if (robinson==null) {
        	Dialogs.getInstance().showErrorMsg(R.string.morph_robinson_not_installed);
        	// this uri request was handled by showing an error message
        	return;
        }

        Key robinsonNumberKey = robinson.getKey(key); 
   		CurrentPageManager.getInstance().setCurrentDocumentAndKey(robinson, robinsonNumberKey);
		
		return;
	}

	private void showAllOccurrences(String ref, SearchBibleSection biblesection, String refPrefix) {
    	Book currentBible = CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument();
    	Book strongsBible = null;

    	// if current bible has no Strongs refs then try to find one that has
    	if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
    		strongsBible = currentBible;
    	} else {
    		strongsBible = SwordDocumentFacade.getInstance().getDefaultBibleWithStrongs();
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
    	String searchText = ControlFactory.getInstance().getSearchControl().decorateSearchString("strong:"+refPrefix+ref, SearchType.ANY_WORDS, biblesection);
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

		return;
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
}
