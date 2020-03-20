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

import androidx.core.view.MenuItemCompat;

import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class SpeakActionBarButtonBase extends QuickActionButton {

	private SpeakControl speakControl;
	
	protected static final int SPEAK_START_PRIORITY = 10;

	public SpeakActionBarButtonBase(SpeakControl speakControl) {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		this.speakControl = speakControl;
	}

	/**  return true if Speak button can be shown */
	public boolean canSpeak() {
		boolean canspeakDoc = speakControl.isCurrentDocSpeakAvailable();
		return //isEnoughRoomInToolbar() && 
				canspeakDoc;
	}
	
	protected SpeakControl getSpeakControl() {
		return speakControl;
	}
}
