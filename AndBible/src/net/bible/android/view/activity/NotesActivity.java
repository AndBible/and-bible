package net.bible.android.view.activity;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.activity.R.id;
import net.bible.android.activity.R.layout;
import net.bible.android.activity.R.string;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.util.DataPipe;
import net.bible.service.format.Note;

import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/** Show Notes and Cross references for the current verse
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class NotesActivity extends ActivityBase {
	private static final String TAG = "NotesActivity";
	
	private ListView mNotesListView;
	
	private TextView mTitle;
	private TextView mWarning;
	
    static final protected String LIST_ITEM_LINE1 = "line1";
    static final protected String LIST_ITEM_LINE2 = "line2";
    
    private int mVerseNo=1;
    private List<Note> mChapterNotesList;
    private List<Note> mVerseNotesList;
	private SimpleAdapter mNotesListAdapter; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying notes");
        setContentView(R.layout.notes);
    
        mTitle =  (TextView)findViewById(R.id.title);
        mWarning =  (TextView)findViewById(R.id.warningText);
        mNotesListView =  (ListView)findViewById(R.id.notesList);
        
        mVerseNo = CurrentPageManager.getInstance().getCurrentBible().getCurrentVerseNo();
        mChapterNotesList = DataPipe.getInstance().popNotes();
        
        initialiseView();
        Log.d(TAG, "Finished displaying Search view");
    }

    private void initialiseView() {
    	mVerseNotesList = new ArrayList<Note>();
    	
    	showCurrentVerse();
    	populateVerseNotesList();
    	prepareWarningMsg();
    	
        mNotesListAdapter = new SimpleAdapter(this, mVerseNotesList, 
                    android.R.layout.two_line_list_item, 
                    new String[] {Note.SUMMARY, Note.DETAIL}, 
                    new int[] {android.R.id.text1, android.R.id.text2});
        	
        mNotesListView.setAdapter(mNotesListAdapter);
        
    	mNotesListView.setOnItemClickListener(new OnItemClickListener() {
    	    @Override
    	    public void onItemClick(AdapterView<?> parentView, View selectedItemView, int position, long id) {
    	    	noteSelected(mVerseNotesList.get(position));
    	    }
    	});
    }

    public void onPrevious(View v) {
    	if (mVerseNo>1) {
    		mVerseNo--;
    		onVerseChanged();
    	}
    }
    public void onNext(View v) {
    	if (mVerseNo<CurrentPageManager.getInstance().getCurrentBible().getNumberOfVersesDisplayed()) {
    		mVerseNo++;
    		onVerseChanged();
    	}
    }

    private void onVerseChanged() {
    	showCurrentVerse();
    	populateVerseNotesList();
    	mNotesListAdapter.notifyDataSetChanged();
    	prepareWarningMsg();
    }
    
    private void populateVerseNotesList() {
    	mVerseNotesList.clear();
    	
    	if (mChapterNotesList!=null) {
			for (Note note : mChapterNotesList) {
				if (note.getVerseNo() == mVerseNo) {
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
    		mNotesListView.setVisibility(View.GONE);
    	} else {
    		mWarning.setVisibility(View.GONE);
    		mNotesListView.setVisibility(View.VISIBLE);
    	}
    }

    private void showCurrentVerse() {
    	mTitle.setText("Verse "+mVerseNo);
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
}
