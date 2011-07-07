package net.bible.android.view.activity.navigation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;

import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseDictionaryWord extends ListActivityBase {
	private static final String TAG = "ChooseDictionaryWord";
	
	private List<Key> mDictionaryGlobalList;
	private List<Key> mMatchingKeyList;
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.choose_dictionary_page);

        // ensure there is actually a dictionary
        if (CurrentPageManager.getInstance().getCurrentDictionary().getCurrentDocument()==null) {
        	Log.e(TAG, "No Dictionary");
        	finish();
        	return;
        }
        
        initialise();
        
        EditText searcheditText = (EditText)findViewById(R.id.searchText);
        searcheditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence searchText, int arg1, int arg2, int arg3) {
				showPossibleDictionaryKeys(searchText.toString());
			}

			@Override
			public void afterTextChanged(Editable searchText) {
			}
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,	int arg2, int arg3) {
			}
        });
        searcheditText.requestFocus();
    }

    /**
     * init the list adapter for the current list activity
     * @return
     */
    protected void initialise()
    {
    	Log.d(TAG, "Initialising");
    	// setting up an initially empty list of matches
    	mMatchingKeyList = new ArrayList<Key>();
        setListAdapter(new ArrayAdapter<Key>(ChooseDictionaryWord.this,
    	        LIST_ITEM_TYPE,
    	        mMatchingKeyList));
    	
    	final Handler uiHandler = new Handler();
    	Dialogs.getInstance().showHourglass();
    	
    	new Thread( new Runnable() {

			@Override
			public void run() {
				try {
					// getting all dictionary keys is slow so do in another thread in order to show hourglass
					//TODO need to optimise this using binary search of globalkeylist without caching
					
			    	//already checked a dictionary exists
			    	mDictionaryGlobalList = CurrentPageManager.getInstance().getCurrentDictionary().getCachedGlobalKeyList(); 
			    	
			    	Log.d(TAG, "Finished Initialising");
				} catch (Throwable t) {
					Log.e(TAG, "Error creating dictionary key list");
			    	// must dismiss hourglass in ui thread
			    	uiHandler.post(new Runnable() {
						@Override
						public void run() {
							showErrorMsg("Error preparing dictionary for use.");
						}
			    	});
				} finally {
			    	// must dismiss hourglass in ui thread
			    	uiHandler.post(new Runnable() {
						@Override
						public void run() {
							dismissHourglass();
						}
			    	});
				}
			}
    	}).start();
    }
    
    /** user has typed something so show keys starting with user's text
     * @param searchText
     */
    private void showPossibleDictionaryKeys(String searchText) {
		Log.d(TAG, "Search for:"+searchText);
		try {
			if (mDictionaryGlobalList!=null) {
				searchText = searchText.toLowerCase();
		
				Iterator<Key> iter = mDictionaryGlobalList.iterator();
				mMatchingKeyList.clear();
				while (iter.hasNext()) {
					Key key = iter.next();
					if (key.getName().toLowerCase().startsWith(searchText)) {
						mMatchingKeyList.add(key);
					}
				}
				Log.d(TAG, "matches found:"+mMatchingKeyList.size());
		
		    	notifyDataSetChanged();
				Log.d(TAG, "Finished searching for:"+searchText);
			} else {
				Log.d(TAG, "Cached global key list is null");
			}
		} catch (Throwable e) {
			Log.e(TAG, "Error finding matching keys", e);
			showErrorMsg("Error searching dictionary");
		}
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	itemSelected(mMatchingKeyList.get(position));
	}

    private void itemSelected(Key selectedKey) {
    	try {
    		if (selectedKey!=null) {
		    	Log.i(TAG, "chose:"+selectedKey);
		    	CurrentPageManager.getInstance().getCurrentDictionary().setKey(selectedKey);
		    	doFinish();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Key not found", e);
    		showErrorMsg("Key not found");
    	}
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
}
