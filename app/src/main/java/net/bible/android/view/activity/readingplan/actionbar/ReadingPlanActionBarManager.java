/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.readingplan.actionbar;

import android.app.Activity;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;
import net.bible.service.device.speak.event.SpeakEvent;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class ReadingPlanActionBarManager extends DefaultActionBarManager implements ActionBarManager {

	private final ReadingPlanTitle readingPlanTitle;
	private final ReadingPlanBibleActionBarButton bibleActionBarButton;
	private final ReadingPlanCommentaryActionBarButton commentaryActionBarButton;
	private final ReadingPlanDictionaryActionBarButton dictionaryActionBarButton;

	private final ReadingPlanPauseActionBarButton pauseActionBarButton;
	private final ReadingPlanStopActionBarButton stopActionBarButton;

	@Inject
	public ReadingPlanActionBarManager(ReadingPlanTitle readingPlanTitle, ReadingPlanPauseActionBarButton pauseActionBarButton, ReadingPlanStopActionBarButton stopActionBarButton, ReadingPlanBibleActionBarButton bibleActionBarButton, ReadingPlanCommentaryActionBarButton commentaryActionBarButton, ReadingPlanDictionaryActionBarButton dictionaryActionBarButton) {
		this.readingPlanTitle = readingPlanTitle;
		this.pauseActionBarButton = pauseActionBarButton;
		this.stopActionBarButton = stopActionBarButton;
		this.bibleActionBarButton = bibleActionBarButton;
		this.commentaryActionBarButton = commentaryActionBarButton;
		this.dictionaryActionBarButton = dictionaryActionBarButton;

		ABEventBus.getDefault().register(this);
    }

    public void onEvent(SpeakEvent e) {
    	updateButtons();
	}

	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		super.prepareOptionsMenu(activity, menu, actionBar);
		
		readingPlanTitle.addToBar(actionBar, activity);

		// order is important to keep bible, cmtry, ... in same place on right
		stopActionBarButton.addToMenu(menu);
		pauseActionBarButton.addToMenu(menu);

		dictionaryActionBarButton.addToMenu(menu);
		commentaryActionBarButton.addToMenu(menu);
		bibleActionBarButton.addToMenu(menu);
	}
	
	public void updateButtons() {
		super.updateButtons();
		
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				readingPlanTitle.update();
				
				bibleActionBarButton.update();
				commentaryActionBarButton.update();
				dictionaryActionBarButton.update();
				
				pauseActionBarButton.update();
				stopActionBarButton.update();
			}
		});
	}
}
