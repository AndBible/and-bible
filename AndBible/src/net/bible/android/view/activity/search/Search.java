package net.bible.android.view.activity.search;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchControl.SearchBibleSection;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.index.search.SearchType;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

/** Allow user to enter search criteria
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Search extends ActivityBase {
	
	public static final String SEARCH_TEXT = "SearchText";
	
	private static final String TAG = "Search";
	
	private EditText mSearchTextInput;
	
	private int wordsRadioSelection = R.id.allWords;
	private int sectionRadioSelection = R.id.searchAllBible;
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.search);
    
        mSearchTextInput =  (EditText)findViewById(R.id.searchText);
        mSearchTextInput.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                  // Perform action on key press
                  onSearch(null);
                  return true;
                }
                return false;
            }
        });
        
        RadioGroup wordsRadioGroup = (RadioGroup)findViewById(R.id.wordsGroup);
        wordsRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				wordsRadioSelection = checkedId;
			}
		});        

        RadioGroup sectionRadioGroup = (RadioGroup)findViewById(R.id.bibleSectionGroup);
        sectionRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				sectionRadioSelection = checkedId;
			}
		});        
        
        Log.d(TAG, "Finished displaying Search view");
    }

    public void onSearch(View v) {
    	Log.i(TAG, "CLICKED");
    	String searchText = mSearchTextInput.getText().toString();
    	if (!StringUtils.isEmpty(searchText)) {
        	searchText = decorateSearchString(searchText);
        	Log.d(TAG, "Search text:"+searchText);
        	
        	Intent intent = new Intent(this, SearchResults.class);
        	intent.putExtra(SEARCH_TEXT, searchText);
        	startActivityForResult(intent, 1);
    	}
    }
    
    private String decorateSearchString(String searchString) {
    	SearchControl searchControl = ControlFactory.getInstance().getSearchControl();
    	String decorated = searchControl.decorateSearchString(searchString, getSearchType(), getBibleSection());

    	return decorated;
    }

    /** get all, any, phrase query limitation
     */
    private SearchType getSearchType() {
    	switch (wordsRadioSelection) {
    	case R.id.allWords:
            return SearchType.ALL_WORDS;
    	case R.id.anyWord:
            return SearchType.ANY_WORDS;
    	case R.id.phrase:
            return SearchType.PHRASE;
        default:
        	Log.e(TAG, "Unexpected radio selection");
            return SearchType.ANY_WORDS;
    	}
    }

    /** get OT, NT, or all query limitation
     * 
     * @return
     */
    private SearchBibleSection getBibleSection() {
    	switch (sectionRadioSelection) {
    	case R.id.searchAllBible:
    		return SearchBibleSection.ALL;
    	case R.id.searchOldTestament:
            return SearchBibleSection.OT;
    	case R.id.searchNewTestament:
            return SearchBibleSection.NT;
        default:
        	Log.e(TAG, "Unexpected radio selection");
    		return SearchBibleSection.ALL;
    	}
    }
    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToMainScreen();
    	}
    }
    
    private void returnToMainScreen() {
    	// just pass control back to teh main screen
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();
    }
}
