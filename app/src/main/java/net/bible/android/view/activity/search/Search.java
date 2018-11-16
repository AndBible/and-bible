package net.bible.android.view.activity.search;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import net.bible.android.activity.R;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.search.SearchControl.SearchBibleSection;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.search.SearchType;

import javax.inject.Inject;

/** Allow user to enter search criteria
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Search extends CustomTitlebarActivityBase {
    
    private EditText mSearchTextInput;
    
    private int wordsRadioSelection = R.id.allWords;
    private int sectionRadioSelection = R.id.searchAllBible;
    
    private String currentBookName;
    
    private SearchControl searchControl;
    
    private static final String SEARCH_TEXT_SAVE = "Search";
    private static final String WORDS_SELECTION_SAVE = "Words";
    private static final String SECTION_SELECTION_SAVE = "Selection";
    private static final String CURRENT_BIBLE_BOOK_SAVE = "BibleBook";
    
    private static final String TAG = "Search";
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Search view");
        setContentView(R.layout.search);

        buildActivityComponent().inject(this);
   
        if (!searchControl.validateIndex(getDocumentToSearch())) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, new Callback() {
                @Override
                public void okay() {
                    finish();
                }
            });
        }
        
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

        // pre-load search string if passed in
        Bundle extras = getIntent().getExtras();
        if (extras!=null) {
            String searchText = extras.getString(SEARCH_TEXT_SAVE);
            if (StringUtils.isNotEmpty(searchText)) {
                mSearchTextInput.setText(searchText);
            }
        }
        
        RadioGroup wordsRadioGroup = (RadioGroup)findViewById(R.id.wordsGroup);
        wordsRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                wordsRadioSelection = checkedId;
            }
        });        
        if (extras!=null) {
            int wordsSelection = extras.getInt(WORDS_SELECTION_SAVE, -1);
            if (wordsSelection!=-1) {
                wordsRadioGroup.check(wordsSelection);
            }
        }
        
        RadioGroup sectionRadioGroup = (RadioGroup)findViewById(R.id.bibleSectionGroup);
        sectionRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                sectionRadioSelection = checkedId;
            }
        });        
        if (extras!=null) {
            int sectionSelection = extras.getInt(SECTION_SELECTION_SAVE, -1);
            if (sectionSelection!=-1) {
                sectionRadioGroup.check(sectionSelection);
            }
        }
        
        // set text for current bible book on appropriate radio button
        RadioButton currentBookRadioButton = (RadioButton)findViewById(R.id.searchCurrentBook);

        // set current book to default and allow override if saved - implies returning via Back button
        currentBookName = searchControl.getCurrentBookName();
        if (extras!=null) {
            String currentBibleBookSaved = extras.getString(CURRENT_BIBLE_BOOK_SAVE);
            if (currentBibleBookSaved!=null) {
                currentBookName = currentBibleBookSaved;
            }
        }
        currentBookRadioButton.setText(currentBookName);
        
        Log.d(TAG, "Finished displaying Search view");
    }

    public void onSearch(View v) {
        Log.i(TAG, "CLICKED");
        String searchText = mSearchTextInput.getText().toString();
        if (!StringUtils.isEmpty(searchText)) {

            // update current intent so search is restored if we return here via history/back
            // the current intent is saved by HistoryManager
            getIntent().putExtra(SEARCH_TEXT_SAVE, searchText);
            getIntent().putExtra(WORDS_SELECTION_SAVE, wordsRadioSelection);
            getIntent().putExtra(SECTION_SELECTION_SAVE, sectionRadioSelection);
            getIntent().putExtra(CURRENT_BIBLE_BOOK_SAVE, currentBookName);
            
            searchText = decorateSearchString(searchText);
            Log.d(TAG, "Search text:"+searchText);

            // specify search string and doc in new Intent; 
            // if doc is not specifed a, possibly invalid, doc may be used when returning to search via history list e.g. search bible, select dict, history list, search results
            Intent intent = new Intent(this, SearchResults.class);
            intent.putExtra(SearchControl.SEARCH_TEXT, searchText);
            String currentDocInitials = getDocumentToSearch().getInitials();
            intent.putExtra(SearchControl.SEARCH_DOCUMENT, currentDocInitials);
            intent.putExtra(SearchControl.TARGET_DOCUMENT, currentDocInitials);
            startActivityForResult(intent, 1);
            
            // Back button is now handled by HistoryManager - Back will cause a new Intent instead of just finish
            finish();
        }
    }
    
    private Book getDocumentToSearch() {
        return getPageControl().getCurrentPageManager().getCurrentPage().getCurrentDocument();
    }
    
    private String decorateSearchString(String searchString) {
        return searchControl.decorateSearchString(searchString, getSearchType(), getBibleSection(), currentBookName);
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
        case R.id.searchCurrentBook:
            return SearchBibleSection.CURRENT_BOOK;
        default:
            Log.e(TAG, "Unexpected radio selection");
            return SearchBibleSection.ALL;
        }
    }

    /** I don't think this is used because of hte finish() in onSearch()
     * TODO remove
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==Activity.RESULT_OK) {
            returnToPreviousScreen();
        }
    }

    @Inject
    void setSearchControl(SearchControl searchControl) {
        this.searchControl = searchControl;
    }
}
