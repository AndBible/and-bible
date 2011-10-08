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

package net.bible.android.view.activity.usernote;

import org.apache.commons.lang.CharSequenceUtils;
import org.crosswire.jsword.passage.Key;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.usernote.UserNoteControl;
import net.bible.service.db.usernote.UserNoteDto;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class UserNotesAdd extends Activity implements OnClickListener {
	private static final String TAG = "UserNotesAdd";
	
	UserNoteControl ctrl;
	
	Button button1;
	EditText editText1;
	TextView textView1;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		Log.d(TAG, "UserNotesAdd Activity onCreate started...");
	
		ctrl = new UserNoteControl();
		
		setContentView(R.layout.usernotes_add);

		// Find views
		editText1 = (EditText) findViewById(R.id.editText1);
		button1 = (Button) findViewById(R.id.button1);
		button1.setOnClickListener(this);

		textView1 = (TextView) findViewById(R.id.TextView1);
		
		Key currentVerse = CurrentPageManager.getInstance().getCurrentBible().getSingleKey();
		textView1.setText(currentVerse.toString().toCharArray(), 0, currentVerse.toString().length());

		UserNoteDto usernoteDto = ctrl.getUserNoteByKey(currentVerse);
		if (usernoteDto != null) {
			Log.i(TAG, "Pre-existing note; pulling note text from database...");
			editText1.setText(usernoteDto.getNoteText().toCharArray(), 0, usernoteDto.getNoteText().length());
		}
		Log.d(TAG, "UserNotesAdd Activity onCreate done...");
	}

	// Called when button is clicked //
	public void onClick(View v) {
		Log.d(TAG, "onClicked");
		Boolean result = ctrl.usernoteCurrentVerse(editText1.getText().toString());
		Log.i(TAG, "...result = " + result);
	}
}