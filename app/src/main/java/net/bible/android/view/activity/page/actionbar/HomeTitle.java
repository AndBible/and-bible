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
import android.content.Intent;
import androidx.appcompat.app.ActionBar;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.passage.CurrentVerseChangedEvent;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.actionbar.Title;
import net.bible.android.view.activity.navigation.ChooseDocument;

import javax.inject.Inject;

/** 
 * Show current verse/key and document on left of actionBar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class HomeTitle extends Title {

	private PageControl pageControl;

	@Inject
	public HomeTitle(PageControl pageControl) {
		this.pageControl = pageControl;
	}

	public void addToBar(ActionBar actionBar, final Activity activity) {
		super.addToBar(actionBar, activity);

		// listen for verse change events
		ABEventBus.getDefault().safelyRegister(this);
	}
	
	/**
	 * Receive verse change events
	 */
	public void onEvent(CurrentVerseChangedEvent passageEvent) {
		update(false);
	}
	
	@Override
	protected String[] getDocumentTitleParts() {
		return pageControl.getCurrentDocumentTitleParts();
	}

	@Override
	protected String[] getPageTitleParts() {
		return pageControl.getCurrentPageTitleParts();
	}

	@Override
	protected void onDocumentTitleClick() {
		Intent intent = new Intent(getActivity(), ChooseDocument.class);
		getActivity().startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE);
	}

	@Override
	protected void onPageTitleClick() {
		Intent intent = new Intent(getActivity(), pageControl.getCurrentPageManager().getCurrentPage().getKeyChooserActivity());
		getActivity().startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE);
	}
}
