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

package net.bible.android.view.activity.page.actionbar;

import android.app.Activity;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;

import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton;
import net.bible.service.device.speak.event.SpeakEvent;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class BibleActionBarManager extends DefaultActionBarManager implements ActionBarManager {

	private final HomeTitle homeTitle;

	private final BibleActionBarButton bibleActionBarButton;
	private final CommentaryActionBarButton commentaryActionBarButton;
	private final DictionaryActionBarButton dictionaryActionBarButton;
	private final StrongsActionBarButton strongsActionBarButton;
	
	private final SpeakActionBarButton speakActionBarButton;
	private final SpeakStopActionBarButton stopActionBarButton;

	@Inject
	public BibleActionBarManager(HomeTitle homeTitle, BibleActionBarButton bibleActionBarButton, CommentaryActionBarButton commentaryActionBarButton, DictionaryActionBarButton dictionaryActionBarButton, SpeakActionBarButton speakActionBarButton, SpeakStopActionBarButton stopActionBarButton, StrongsActionBarButton strongsActionBarButton, DocumentControl documentControl) {
		this.homeTitle = homeTitle;
		this.bibleActionBarButton = bibleActionBarButton;
		this.speakActionBarButton = speakActionBarButton;
		this.stopActionBarButton = stopActionBarButton;
		this.commentaryActionBarButton = commentaryActionBarButton;
		this.dictionaryActionBarButton = dictionaryActionBarButton;
		this.strongsActionBarButton = strongsActionBarButton;

		ABEventBus.getDefault().register(this);
	}

	public void onEvent(SpeakEvent e) {
		updateButtons();
	}

	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#prepareOptionsMenu(android.app.Activity, android.view.Menu, android.support.v7.app.ActionBar, net.bible.android.view.activity.page.MenuCommandHandler)
	 */
	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		super.prepareOptionsMenu(activity, menu, actionBar);
		
		homeTitle.addToBar(actionBar, activity);

		// order is important to keep bible, cmtry, ... in same place on right
		stopActionBarButton.addToMenu(menu);
		speakActionBarButton.addToMenu(menu);

		strongsActionBarButton.addToMenu(menu);
		dictionaryActionBarButton.addToMenu(menu);
		commentaryActionBarButton.addToMenu(menu);
		bibleActionBarButton.addToMenu(menu);
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#updateButtons()
	 */
	@Override
	public void updateButtons() {
		super.updateButtons();
		
		// this can be called on end of speech in non-ui thread
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				homeTitle.update();
				
				bibleActionBarButton.update();
				commentaryActionBarButton.update();
				dictionaryActionBarButton.update();
				strongsActionBarButton.update();
				
				speakActionBarButton.update();
				stopActionBarButton.update();
		    }
		});
	}
}
