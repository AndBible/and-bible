package net.bible.android.activity;

 import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.bible.android.CurrentPassage;
import net.bible.android.util.DataPipe;
import net.bible.service.format.Note;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class NotesActivity extends Activity {
	private static final String TAG = "NotesActivity";
	
	private ListView mNotesListView;
	
	private TextView mTitle;
	
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
        mNotesListView =  (ListView)findViewById(R.id.notesList);
        
        mVerseNo = CurrentPassage.getInstance().getCurrentVerse();
        mChapterNotesList = DataPipe.getInstance().popNotes();
        
        initialiseView();
        Log.d(TAG, "Finished displaying Search view");
    }

    private void initialiseView() {
    	showCurrentVerse();
    	populateVerseNotesList();
    	
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

    private void verseChanged() {
    	populateVerseNotesList();
    	mNotesListAdapter.notifyDataSetChanged();
    }
    
    private void populateVerseNotesList() {
    	mVerseNotesList = getNotesList(mVerseNo);
    }
    
	private List<Note> getNotesList(int verseNo) {
		List<Note> verseNotes = new ArrayList<Note>();
		for (Note note : mChapterNotesList) {
			if (note.getVerseNo() == verseNo) {
				verseNotes.add(note);
			}
		}
		return verseNotes;
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
