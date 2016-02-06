package net.bible.android.view.activity.comparetranslations;

 import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.comparetranslations.TranslationDto;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.util.swipe.SwipeGestureEventHandler;
import net.bible.android.view.util.swipe.SwipeGestureListener;

import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.crosswire.jsword.versification.Versification;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** do the search and show the search results
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CompareTranslations extends ListActivityBase implements SwipeGestureEventHandler {
	private static final String TAG = "CompareTranslations";
	
    private List<TranslationDto> mTranslations = new ArrayList<TranslationDto>();
    private ArrayAdapter<TranslationDto> mKeyArrayAdapter;
    
	// detect swipe left/right
	private GestureDetector gestureDetector;

	private CompareTranslationsControl compareTranslationsControl = ControlFactory.getInstance().getCompareTranslationsControl();

    public static final String VERSE = "net.bible.android.view.activity.comparetranslations.Verse";
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, true);
        Log.i(TAG, "Displaying Compare Translations view");
        setContentView(R.layout.list);
        
        //fetch verse from intent if set - so that goto via History works nicely
		Bundle extras = getIntent().getExtras();
		try {
			if (extras != null) {
				if (extras.containsKey(VERSE)) {
					CurrentBiblePage currentDoc = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible();
					Versification currentV11n = ((SwordBook) currentDoc.getCurrentDocument()).getVersification();
					Verse verse = VerseFactory.fromString(currentV11n, extras.getString(VERSE));
					compareTranslationsControl.setVerse(verse);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting compare verse, using default");
		}

		prepareScreenData();

    	mKeyArrayAdapter = new ItemAdapter(this, LIST_ITEM_TYPE, mTranslations);
        setListAdapter(mKeyArrayAdapter);

        // create gesture related objects
        gestureDetector = new GestureDetector( new SwipeGestureListener(this) );
    }
    
    private void prepareScreenData() {

        setTitle(compareTranslationsControl.getTitle());

        mTranslations.clear();
        mTranslations.addAll(compareTranslationsControl.getAllTranslations());
        
        notifyDataSetChanged();

        Log.d(TAG, "Finished displaying Compare Translations view");
    }

    /** swiped left
     */
    @Override
	public void onNext() {
    	Log.d(TAG, "Next");
    	compareTranslationsControl.next();
    	prepareScreenData();
    }

    /** swiped right
     */
    @Override
	public void onPrevious() {
    	Log.d(TAG, "Previous");
    	compareTranslationsControl.previous();
    	prepareScreenData();
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		// no need to call HistoryManager.beforePageChange() here because PassageChangeMediator will tell HistoryManager a change is about to occur 
    		
	    	translationSelected(mTranslations.get(position));
		} catch (Exception e) {
			Log.e(TAG, "Selection error", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
	}
    
    private void translationSelected(TranslationDto translationDto) {
    	if (translationDto!=null) {
        	Log.i(TAG, "chose:"+translationDto.getBook());
        	
        	compareTranslationsControl.showTranslation(translationDto);
    		
    		// this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
    		returnToPreviousScreen();
    	}
    }

    /** implement getHistoryIntent to allow correct verse to be shown if history nav occurs
     */
	@Override
	public Intent getIntentForHistoryList() {
		Intent intent = getIntent();
		
		intent.putExtra(VERSE, compareTranslationsControl.getVerse().getOsisID());

		return intent;
	}

	// handle swipe left and right
	@Override
	public boolean dispatchTouchEvent(MotionEvent motionEvent) {
		this.gestureDetector.onTouchEvent(motionEvent);
		return super.dispatchTouchEvent(motionEvent);
	}
}
