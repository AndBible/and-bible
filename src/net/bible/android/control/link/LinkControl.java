package net.bible.android.control.link;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchControl.SearchBibleSection;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.search.SearchResults;
import net.bible.service.common.Constants;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.search.SearchType;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;

import android.app.Activity;
import android.content.Intent;
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
	        } else if (Constants.ALL_GREEK_OCCURRENCES_PROTOCOL.equals(protocol)) {
	        	showAllOccurrences(ref, SearchBibleSection.NT, "g");
	        } else if (Constants.ALL_HEBREW_OCCURRENCES_PROTOCOL.equals(protocol)) {
	        	showAllOccurrences(ref, SearchBibleSection.OT, "h");
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

	private void showAllOccurrences(String ref, SearchBibleSection biblesection, String refPrefix) {
    	Book currentBible = CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument();
    	Book strongsBible = null;

    	// if current bible has no refs then try to find one that has
    	if (currentBible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
    		strongsBible = currentBible;
    	} else {
    		strongsBible = SwordApi.getInstance().getDefaultBibleWithStrongs();
    	}
    	
    	if (strongsBible == null || !strongsBible.getIndexStatus().equals(IndexStatus.DONE)) {
    		Dialogs.getInstance().showErrorMsg(R.string.no_indexed_bible_with_strongs_ref);
    		return;
    	}
    	
    	// The below uses ANY_WORDS because that does not add anything to the search string
    	String searchText = ControlFactory.getInstance().getSearchControl().decorateSearchString("strong:"+refPrefix+ref, SearchType.ANY_WORDS, biblesection);
    	Log.d(TAG, "Search text:"+searchText);

    	Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent intent = new Intent(activity, SearchResults.class);
    	intent.putExtra(SearchControl.SEARCH_TEXT, searchText);
    	intent.putExtra(SearchControl.SEARCH_DOCUMENT, strongsBible.getInitials());
    	intent.putExtra(SearchControl.TARGET_DOCUMENT, currentBible.getInitials());
    	activity.startActivity(intent);
		return;
	}
}
