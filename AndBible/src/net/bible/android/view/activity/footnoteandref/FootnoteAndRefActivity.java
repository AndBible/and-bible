package net.bible.android.view.activity.footnoteandref;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.footnoteandref.FootnoteAndRefControl;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.util.DataPipe;
import net.bible.android.view.util.swipe.SwipeGestureEventHandler;
import net.bible.android.view.util.swipe.SwipeGestureListener;
import net.bible.service.format.Note;

import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/** Show Notes and Cross references for the current verse
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FootnoteAndRefActivity extends ListActivityBase implements SwipeGestureEventHandler {
	private static final String TAG = "NotesActivity";
	
	private TextView mWarning;
	
    static final protected String LIST_ITEM_LINE1 = "line1";
    static final protected String LIST_ITEM_LINE2 = "line2";
    
    private List<Note> mChapterNotesList;
    private List<Note> mVerseNotesList;
	private ArrayAdapter<Note> mNotesListAdapter; 
	
	// detect swipe left/right
	private GestureDetector gestureDetector;

	private FootnoteAndRefControl footnoteAndRefControl = ControlFactory.getInstance().getFootnoteAndRefControl();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying notes");
        setContentView(R.layout.notes);
    
        mWarning =  (TextView)findViewById(R.id.warningText);
        
        mChapterNotesList = DataPipe.getInstance().popNotes();
        
        initialiseView();

        // create gesture related objects
        gestureDetector = new GestureDetector( new SwipeGestureListener(this) );
    }

    private void initialiseView() {
    	mVerseNotesList = new ArrayList<Note>();
    	
    	showCurrentVerse();
    	populateVerseNotesList();
    	prepareWarningMsg();
    	
    	mNotesListAdapter = new ItemAdapter(this, LIST_ITEM_TYPE, mVerseNotesList);
        setListAdapter(mNotesListAdapter);
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	noteSelected(mVerseNotesList.get(position));
	}

    /** swiped left
     */
    public void onNext() {
    	Log.d(TAG, "Next");
    	footnoteAndRefControl.next();
    	onVerseChanged();
    }

    /** swiped right
     */
    public void onPrevious() {
    	Log.d(TAG, "Previous");
    	footnoteAndRefControl.previous();
    	onVerseChanged();
    }

    private void onVerseChanged() {
    	showCurrentVerse();
    	populateVerseNotesList();
    	notifyDataSetChanged();
    	prepareWarningMsg();
    }
    
    private void populateVerseNotesList() {
    	mVerseNotesList.clear();
    	int verseNo = footnoteAndRefControl.getVerse().getVerse();
    	if (mChapterNotesList!=null) {
			for (Note note : mChapterNotesList) {
				if (note.getVerseNo() == verseNo) {
					mVerseNotesList.add(note);
				}
			}
    	}
    }
    
    private void prepareWarningMsg() {
    	String warning = "";
    	if (mChapterNotesList==null || mChapterNotesList.size()==0) {
    		warning = getString(R.string.no_chapter_notes);
    	} else if (mChapterNotesList==null || mVerseNotesList.size()==0) {
    		warning = getString(R.string.no_verse_notes);
    	}
    	
		mWarning.setText(warning);
    	if (StringUtils.isNotEmpty(warning)) {
    		mWarning.setVisibility(View.VISIBLE);
    		getListView().setVisibility(View.GONE);
    	} else {
    		mWarning.setVisibility(View.GONE);
    		getListView().setVisibility(View.VISIBLE);
    	}
    }

    private void showCurrentVerse() {
    	setTitle(footnoteAndRefControl.getTitle());
    }
    
    private void noteSelected(Note note) {
    	Log.i(TAG, "chose:"+note);
    	if (note.isNavigable()) {
    		note.navigateTo();
    	}
    	doFinish();
    }
    public void onFinish(View v) {
    	Log.i(TAG, "CLICKED");
    	doFinish();    
    }
    public void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

    // handle swipe left and right
	@Override
	public boolean dispatchTouchEvent(MotionEvent motionEvent) {
		this.gestureDetector.onTouchEvent(motionEvent);
		return super.dispatchTouchEvent(motionEvent);
	}
}
