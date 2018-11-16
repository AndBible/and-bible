package net.bible.android.view.activity.comparetranslations;

 import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.comparetranslations.TranslationDto;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.util.swipe.SwipeGestureEventHandler;
import net.bible.android.view.util.swipe.SwipeGestureListener;

import org.crosswire.jsword.passage.VerseRange;

import java.util.ArrayList;
import java.util.List;

 import javax.inject.Inject;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CompareTranslations extends ListActivityBase implements SwipeGestureEventHandler {

    private List<TranslationDto> mTranslations = new ArrayList<TranslationDto>();
    private ArrayAdapter<TranslationDto> mKeyArrayAdapter;

    private VerseRange currentVerseRange;

    // detect swipe left/right
    private GestureDetector gestureDetector;

    private CompareTranslationsControl compareTranslationsControl;

    private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    private IntentHelper intentHelper = new IntentHelper();

    private static final String TAG = "CompareTranslations";

    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Compare Translations view");
        setContentView(R.layout.list);

        buildActivityComponent().inject(this);

        //fetch verse from intent if set - so that goto via History works nicely
        currentVerseRange = intentHelper.getIntentVerseRangeOrDefault(getIntent());

        prepareScreenData();

        mKeyArrayAdapter = new ItemAdapter(this, LIST_ITEM_TYPE, mTranslations);
        setListAdapter(mKeyArrayAdapter);

        // create gesture related objects
        gestureDetector = new GestureDetector( new SwipeGestureListener(this) );
    }
    
    private void prepareScreenData() {

        setTitle(compareTranslationsControl.getTitle(currentVerseRange));

        mTranslations.clear();
        mTranslations.addAll(compareTranslationsControl.getAllTranslations(currentVerseRange));
        
        notifyDataSetChanged();

        Log.d(TAG, "Finished displaying Compare Translations view");
    }

    /** swiped left
     */
    @Override
    public void onNext() {
        Log.d(TAG, "Next");
        currentVerseRange = compareTranslationsControl.getNextVerseRange(currentVerseRange);
        prepareScreenData();
    }

    /** swiped right
     */
    @Override
    public void onPrevious() {
        Log.d(TAG, "Previous");
        currentVerseRange = compareTranslationsControl.getPreviousVerseRange(currentVerseRange);
        prepareScreenData();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        try {
            // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
            
            translationSelected(mTranslations.get(position));
        } catch (Exception e) {
            Log.e(TAG, "Selection error", e);
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
        }
    }
    
    private void translationSelected(TranslationDto translationDto) {
        if (translationDto!=null) {
            Log.i(TAG, "chose:"+translationDto.getBook());
            
            compareTranslationsControl.showTranslationForVerseRange(translationDto, currentVerseRange);
            
            // this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
            returnToPreviousScreen();
        }
    }

    /** implement getHistoryIntent to allow correct verse to be shown if history nav occurs
     */
    @Override
    public Intent getIntentForHistoryList() {
        Intent intent = getIntent();

        intentHelper.updateIntentWithVerseRange(getIntent(), currentVerseRange);

        return intent;
    }

    // handle swipe left and right
    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        this.gestureDetector.onTouchEvent(motionEvent);
        return super.dispatchTouchEvent(motionEvent);
    }

    @Inject
    void setCompareTranslationsControl(CompareTranslationsControl compareTranslationsControl) {
        this.compareTranslationsControl = compareTranslationsControl;
    }
}
