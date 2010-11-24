package net.bible.android.view.activity.search;

import net.bible.android.activity.R;
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
import android.widget.Toast;
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
	
	private static final String SEARCH_NEW_TESTAMENT = "+[Mat-Rev]";
	private static final String SEARCH_OLD_TESTAMENT = "+[Gen-Mal]";
//	private BookName currentBibleBook; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.search);
    
//        currentBibleBook = CurrentBiblePage.getInstance().getCurrentBibleBook();
        
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
        // removed to make controls fit better on screen
        // set text for current bible book on appropriate radio button
//        RadioButton currentBookRadioButton = (RadioButton)findViewById(R.id.searchCurrentBook);
//        currentBookRadioButton.setText(currentBibleBook.getLongName());
        
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
    	String decorated = searchString;
    	switch (wordsRadioSelection) {
    	case R.id.allWords:
            decorated = SearchType.ALL_WORDS.decorate(searchString);
            break;
    	case R.id.anyWord:
            decorated = SearchType.ANY_WORDS.decorate(searchString);
            break;
    	case R.id.phrase:
            decorated = SearchType.PHRASE.decorate(searchString);
            break;
        default:
        	Log.e(TAG, "Unexpected radio selection");
            decorated = "ERROR";
    	}

    	switch (sectionRadioSelection) {
    	case R.id.searchAllBible:
    		// noop
            break;
    	case R.id.searchOldTestament:
            decorated = SEARCH_OLD_TESTAMENT+" "+decorated;
            break;
    	case R.id.searchNewTestament:
            decorated = SEARCH_NEW_TESTAMENT+" "+decorated;
            break;
//    	case R.id.searchCurrentBook:
//            decorated = "+["+currentBibleBook.getShortName()+"] "+decorated;
//            break;
        default:
        	Log.e(TAG, "Unexpected radio selection");
            decorated = "ERROR";
    	}
    	
    	return decorated;
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
