/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.footnoteandref;

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

import net.bible.android.activity.R;
import net.bible.android.control.footnoteandref.FootnoteAndRefControl;
import net.bible.android.control.footnoteandref.NoteDetailCreator;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.util.swipe.SwipeGestureEventHandler;
import net.bible.android.view.util.swipe.SwipeGestureListener;
import net.bible.service.format.Note;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.passage.VerseRange;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** Show Notes and Cross references for the current verse
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class FootnoteAndRefActivity extends ListActivityBase implements SwipeGestureEventHandler {
	private static final String TAG = "NotesActivity";
	
	private TextView mWarning;
	
    static final protected String LIST_ITEM_LINE1 = "line1";
    static final protected String LIST_ITEM_LINE2 = "line2";

	private VerseRange currentVerseRange;

	private List<Note> mChapterNotesList;
    private List<Note> mVerseNotesList;
	private ArrayAdapter<Note> mNotesListAdapter;

	private IntentHelper intentHelper = new IntentHelper();

	// detect swipe left/right
	private GestureDetector gestureDetector;

	private FootnoteAndRefControl footnoteAndRefControl;

	private NoteDetailCreator noteDetailCreator;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying notes");
        setContentView(R.layout.notes);

		buildActivityComponent().inject(this);
		//fetch verse from intent if set - so that goto via History works nicely
		currentVerseRange = intentHelper.getIntentVerseRangeOrDefault(getIntent());

        mWarning =  (TextView)findViewById(R.id.warningText);
        
        mChapterNotesList = footnoteAndRefControl.getCurrentPageFootnotesAndReferences();

        initialiseView();

        // create gesture related objects
        gestureDetector = new GestureDetector( new SwipeGestureListener(this) );
    }

    private void initialiseView() {
    	mVerseNotesList = new ArrayList<>();
    	
    	showCurrentVerse();
    	populateVerseNotesList();
    	prepareWarningMsg();
    	
    	mNotesListAdapter = new ItemAdapter(this, LIST_ITEM_TYPE, mVerseNotesList, noteDetailCreator);
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
    	currentVerseRange = footnoteAndRefControl.next(currentVerseRange);
    	onVerseChanged();
    }

    /** swiped right
     */
    public void onPrevious() {
    	Log.d(TAG, "Previous");
    	currentVerseRange = footnoteAndRefControl.previous(currentVerseRange);
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
    	int startVerseNo = currentVerseRange.getStart().getVerse();
		int endVerseNo = currentVerseRange.getEnd().getVerse();
    	if (mChapterNotesList!=null) {
			for (Note note : mChapterNotesList) {
				final int noteVerseNo = note.getVerseNo();
				if (noteVerseNo >= startVerseNo && noteVerseNo <=endVerseNo) {
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
    	setTitle(footnoteAndRefControl.getTitle(currentVerseRange));
    }
    
    private void noteSelected(Note note) {
    	Log.i(TAG, "chose:"+note);
    	if (note.isNavigable()) {
			footnoteAndRefControl.navigateTo(note);
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

	@Inject
	void setFootnoteAndRefControl(FootnoteAndRefControl footnoteAndRefControl) {
		this.footnoteAndRefControl = footnoteAndRefControl;
	}

	@Inject
	void setNoteDetailCreator(NoteDetailCreator noteDetailCreator) {
		this.noteDetailCreator = noteDetailCreator;
	}
}
