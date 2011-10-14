/*
 ******************************************************************************
 * Parts of this code sample are licensed under Apache License, Version 2.0   *
 * Copyright (c) 2009, Android Open Handset Alliance. All rights reserved.    *
 *                                                                            *                                                                         *
 * Except as noted, this code sample is offered under a modified BSD license. *
 * Copyright (C) 2010, Motorola Mobility, Inc. All rights reserved.           *
 *                                                                            *
 * For more details, see MOTODEV_Studio_for_Android_LicenseNotices.pdf        * 
 * in your installation folder.                                               *
 ******************************************************************************
 */

package net.bible.android.view.activity.mynote;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

/**
 * Allow user to edit a User Note
 *  
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteEdit extends CustomTitlebarActivityBase {
	private static final String TAG = "MyNoteEdit";
	
	private MyNote mynoteControl = ControlFactory.getInstance().getMyNoteControl();
	
	private EditText mynoteText;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		setContentView(R.layout.mynote_edit);
		
		// Find views
		mynoteText = (EditText) findViewById(R.id.userNoteText);
	}
	
	/** onStart is called after onCreate or when being returned to from a change in verse
	 */
	@Override
	protected void onStart() {
		super.onStart();

		// do enough to make the top menu state work correctly
		mynoteControl.editStarted();

		Key currentVerse = CurrentPageManager.getInstance().getCurrentBible().getSingleKey();
		MyNoteDto usernoteDto = mynoteControl.getUserNoteByKey(currentVerse);
		if (usernoteDto != null) {
			Log.i(TAG, "Pre-existing note; pulling note text from database...");
			mynoteText.setText(usernoteDto.getNoteText().toCharArray(), 0, usernoteDto.getNoteText().length());
		}

		// populate header 
		setDocumentTitle(getText(R.string.mynote));
		updatePageTitle();
		updateSuggestedDocuments();
	}



	@Override
	protected void handleHeaderButtonPress(HeaderButton buttonType) {
		super.handleHeaderButtonPress(buttonType);
		if (buttonType != HeaderButton.PAGE) {
			// all header buttons except pageChange (verse change) navigate away but verse change will come back here
			// cause finish() and request Notes list to exit too and allow Main view to be seen 
			returnToTop();
		}
	}

	/** User is navigating to another page so finish this page to reclaim memory and remove from screen, 
	 *  often falling back to main BibleView unless another activity was requested
	 */
	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "saving My Note");
		Boolean result = mynoteControl.saveUsernoteCurrentVerse(mynoteText.getText().toString());
		mynoteText.setText("");
	}

	@Override
	protected void preferenceSettingsChanged() {
		// can't happen from this screen		
	}
}