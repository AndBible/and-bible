/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.speak.actionbarbuttons;

import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.speak.SpeakControl;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Stop Speaking
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class SpeakStopActionBarButton extends SpeakActionBarButtonBase {

	@Inject
	public SpeakStopActionBarButton(SpeakControl speakControl) {
		super(speakControl);
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		getSpeakControl().stop(false, false);

		return true;
	}

	@Override
	protected String getTitle() {
		return CommonUtils.INSTANCE.getResourceString(R.string.stop);
	}
	
	@Override
	protected int getIcon() {
		return R.drawable.ic_media_stop;
	}

	@Override
	protected boolean canShow() {
		return isSpeakMode();
	}
}
