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

package net.bible.android.view.activity.mynote;

import android.app.Activity;
import android.view.ViewGroup;

import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.MainBibleActivity;

import javax.inject.Inject;

/**
 * Build a MyNote TextView for viewing or editing notes
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class MyNoteViewBuilder {

	private MyNoteEditTextView myNoteText;
	private static final int MYNOTE_TEXT_ID = 992;
	
	private MainBibleActivity mainActivity;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String TAG = "MyNoteViewBuilder";

	@Inject
	public MyNoteViewBuilder(MainBibleActivity mainBibleActivity, MyNoteControl myNoteControl, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.mainActivity = mainBibleActivity;
		
        myNoteText = new MyNoteEditTextView(this.mainActivity, myNoteControl);

		//noinspection ResourceType
		myNoteText.setId(MYNOTE_TEXT_ID);

		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isMyNoteViewType() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager().isMyNoteShown();
	}
	
	public void addMyNoteView(ViewGroup parent) {
    	boolean isMynoteTextEdit = isMyNoteViewShowing(parent);
    	parent.setTag(TAG);

    	if (!isMynoteTextEdit) {
    		parent.addView(myNoteText);
    		mainActivity.registerForContextMenu(myNoteText);
    	}
	}

	public void removeMyNoteView(ViewGroup parent) {
    	boolean isMynoteTextEdit = isMyNoteViewShowing(parent);
    	
    	if (isMynoteTextEdit) {
        	parent.setTag("");
    		parent.removeView(myNoteText);
    		mainActivity.unregisterForContextMenu(myNoteText);
    	}
	}

	public DocumentView getView() {
		return myNoteText;
	}

	private boolean isMyNoteViewShowing(ViewGroup parent) {
		Object tag = parent.getTag();
		return tag!=null && tag.equals(TAG);
	}
}
