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

package net.bible.android.view.activity.page.actionmode;

import android.app.Activity;
import android.content.Intent;

import net.bible.android.activity.R;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.passage.VerseRange;
import org.jetbrains.annotations.NotNull;

/** Handle requests from the selected verse action menu
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class VerseMenuCommandHandler {

	private final MainBibleActivity mainActivity;

	private final PageControl pageControl;

	private final BookmarkControl bookmarkControl;

	private final MyNoteControl myNoteControl;

	private final IntentHelper intentHelper = new IntentHelper();

	private static final String TAG = "VerseMenuCommandHandler";

	public VerseMenuCommandHandler(MainBibleActivity mainActivity, PageControl pageControl, BookmarkControl bookmarkControl, MyNoteControl myNoteControl) {
		super();
		this.mainActivity = mainActivity;
		this.pageControl = pageControl;
		this.bookmarkControl = bookmarkControl;
		this.myNoteControl = myNoteControl;
	}
	
	/**
	 * on Click handler for Selected verse menu
	 */
	public boolean handleMenuRequest(int menuItemId, @NotNull VerseRange verseRange) {
		boolean isHandled = false;

		{
			Intent handlerIntent = null;
			int requestCode = ActivityBase.STD_REQUEST_CODE;

			// Handle item selection
			switch (menuItemId) {
				case R.id.compareTranslations:
					handlerIntent = new Intent(mainActivity, CompareTranslations.class);
					isHandled = true;
					break;
				case R.id.notes:
					handlerIntent = new Intent(mainActivity, FootnoteAndRefActivity.class);
					isHandled = true;
					break;
				case R.id.add_bookmark:
					bookmarkControl.addBookmarkForVerseRange(verseRange);
					// refresh view to show new bookmark icon
					isHandled = true;
					break;
				case R.id.delete_bookmark:
					bookmarkControl.deleteBookmarkForVerseRange(verseRange);
					// refresh view to show new bookmark icon
					isHandled = true;
					break;
				case R.id.edit_bookmark_labels:
					bookmarkControl.editBookmarkLabelsForVerseRange(verseRange);
					isHandled = true;
					break;
				case R.id.myNoteAddEdit:
					mainActivity.setFullScreen(false);
					myNoteControl.showMyNote(verseRange);
					mainActivity.invalidateOptionsMenu();
					mainActivity.documentViewManager.resetView();
					isHandled = true;
					break;
				case R.id.copy:
					pageControl.copyToClipboard(verseRange);
					isHandled = true;
					break;
				case R.id.shareVerse:
					pageControl.shareVerse(verseRange);
					isHandled = true;
					break;
			}

			if (handlerIntent!=null) {
				intentHelper.updateIntentWithVerseRange(handlerIntent, verseRange);
				mainActivity.startActivityForResult(handlerIntent, requestCode);
			}
		}

		return isHandled;
	}
}
