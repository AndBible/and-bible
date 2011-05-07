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
import net.bible.service.common.Constants;
import net.bible.service.sword.SwordApi;

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
			// check for urls like gdef:01234 
			if (!uri.contains(":")) {
				return false;
			}
			String[] uriTokens = uri.split(":");
	        String protocol = uriTokens[0];
	        String ref = uriTokens[1];
	
	        // hebrew or greek
	        if (Constants.GREEK_DEF_PROTOCOL.equals(protocol)) {
	        	showStrongs(Defaults.getGreekDefinitions(), ref);
	        } else if (Constants.HEBREW_DEF_PROTOCOL.equals(protocol)) {
	        	showStrongs(Defaults.getHebrewDefinitions(), ref);
	        } else if (Constants.ROBINSON_GREEK_MORPH_PROTOCOL.equals(protocol)) {
	        	showRobinsonMorphology(ref);
	        } else if (Constants.ALL_GREEK_OCCURRENCES_PROTOCOL.equals(protocol)) {
	        	showAllOccurrences(ref, SearchBibleSection.ALL, "g");
	        } else if (Constants.ALL_HEBREW_OCCURRENCES_PROTOCOL.equals(protocol)) {
	        	showAllOccurrences(ref, SearchBibleSection.ALL, "h");
	        } else {
	        	// not a valid Strongs Uri
	        	return false;
	        }
	        // handled this url (or at least attemoted to)
	        return true;
		} catch (Exception e) {
			Log.e(TAG, "Error going to Strongs", e);
			return false;
		}
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
		Book robinson = SwordApi.getInstance().getDocumentByInitials("robinson");
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
    		strongsBible = SwordApi.getInstance().getDefaultBibleWithStrongs();
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
