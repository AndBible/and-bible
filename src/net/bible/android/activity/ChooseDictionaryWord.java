package net.bible.android.activity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.android.activity.base.ListActivityBase;
import net.bible.android.currentpagecontrol.CurrentPageManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
			public void afterTextChanged(Editable searchText) {
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,	int arg2, int arg3) {
			}

			@Override
			public void onTextChanged(CharSequence searchText, int arg1, int arg2, int arg3) {
				showPossibleDictionaryKeys(searchText.toString());
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
    	//already checked a dictionary exists
    	Book dictionary = CurrentPageManager.getInstance().getCurrentDictionary().getCurrentDocument();
    	
    	mDictionaryGlobalList = new ArrayList<Key>();
    	Iterator iter = dictionary.getGlobalKeyList().iterator();
		while (iter.hasNext()) {
			Key key = (Key)iter.next();
			mDictionaryGlobalList.add(key);
		}
    	
    	mMatchingKeyList = new ArrayList<Key>();
    	
        setListAdapter(new ArrayAdapter<Key>(this,
    	        LIST_ITEM_TYPE,
    	        mMatchingKeyList));
    	Log.d(TAG, "Finished Initialising");
    }

    /** user has typed something so show keys starting with user's text
     * @param searchText
     */
    private void showPossibleDictionaryKeys(String searchText) {
		Log.d(TAG, "Search for:"+searchText);
		searchText = searchText.toLowerCase();

		Iterator iter = mDictionaryGlobalList.iterator();
		mMatchingKeyList.clear();
		while (iter.hasNext()) {
			Key key = (Key)iter.next();
			if (key.getName().toLowerCase().startsWith(searchText)) {
				mMatchingKeyList.add(key);
			}
		}
		Log.d(TAG, "matches found:"+mMatchingKeyList.size());

    	((ArrayAdapter)getListAdapter()).notifyDataSetChanged();
		Log.d(TAG, "Finished searching for:"+searchText);
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	itemSelected(mMatchingKeyList.get(position));
	}

    private void itemSelected(Key selectedKey) {
    	try {
	    	Log.i(TAG, "chose:"+selectedKey);
	    	CurrentPageManager.getInstance().getCurrentDictionary().setKey(selectedKey);
	    	doFinish();
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
