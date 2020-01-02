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

package net.bible.android.view.activity.speak.actionbarbuttons;

import android.util.Log;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Start speaking current page
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class SpeakActionBarButton extends SpeakActionBarButtonBase {

	private final DocumentControl documentControl;

	private static final String TAG = "SpeakActionBarButtonBas";

	@Inject
	public SpeakActionBarButton(SpeakControl speakControl, DocumentControl documentControl) {
		super(speakControl);
		this.documentControl = documentControl;
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		try {
			getSpeakControl().toggleSpeak();
			
			update(menuItem);
		} catch (Exception e) {
			Log.e(TAG, "Error toggling speech", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
		return true;
	}

	@Override
	protected String getTitle() {
		return CommonUtils.INSTANCE.getResourceString(R.string.speak);
	}
	
	@Override
	protected int getIcon() {
       	if (getSpeakControl().isSpeaking()) {
			return android.R.drawable.ic_media_pause;
		} else if (getSpeakControl().isPaused()) {
			return android.R.drawable.ic_media_play;
		} else {
			return R.drawable.ic_hearing_24dp;
		}
	}

	@Override
	protected boolean canShow() {
		// show if speakable or already speaking (to pause), and only if plenty of room
		return (super.canSpeak() || isSpeakMode()) &&
				(isWide() || !documentControl.isStrongsInBook() || isSpeakMode());
	}
}
