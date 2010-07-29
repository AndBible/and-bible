package net.bible.android.activity;

 import org.crosswire.jsword.index.search.SearchType;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

public class Search extends Activity {
	
	public static final String SEARCH_TEXT = "SearchText";
	
	private static final String TAG = "Search";
	
	private EditText mSearchTextInput;
	
	private int wordsRadioSelection = R.id.allWords;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.search);
    
        mSearchTextInput =  (EditText)findViewById(R.id.searchText);
        
        RadioGroup wordsRadioGroup = (RadioGroup)findViewById(R.id.wordsGroup);
        wordsRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				wordsRadioSelection = checkedId;
			}
		});        
        
        Log.d(TAG, "Finished displaying Search view");
    }

    public void onSearch(View v) {
    	Log.i(TAG, "CLICKED");
    	String searchText = mSearchTextInput.getText().toString();
    	searchText = decorateSearchString(searchText);
    	
    	Intent intent = new Intent(this, SearchResults.class);
    	intent.putExtra(SEARCH_TEXT, searchText);
    	startActivityForResult(intent, 1);
    }
    
    private String decorateSearchString(String searchString) {
    	switch (wordsRadioSelection) {
    	case R.id.allWords:
            return SearchType.ALL_WORDS.decorate(searchString);
    	case R.id.anyWord:
            return SearchType.ANY_WORDS.decorate(searchString);
    	case R.id.phrase:
            return SearchType.PHRASE.decorate(searchString);
        default:
        	Log.e(TAG, "Unexpected radio selection");
            return "ERROR";
    	}

//        SearchType.PHRASE.decorate(searchString);
//        search.append(SearchType.RANGE.decorate(restrict));
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
