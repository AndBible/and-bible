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

package net.bible.android.view.activity.search;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.bible.android.activity.R;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ProgressActivityBase;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.common.progress.Progress;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.IndexStatus;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class SearchIndexProgressStatus extends ProgressActivityBase {

	private Book documentBeingIndexed;
	
	private static final String TAG = "SearchIndexProgressStat";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.search_index_status);

		super.buildActivityComponent().inject(this);

		hideButtons();
		setMainText(getString(R.string.indexing_wait_msg));
		
		String docInitials = getIntent().getStringExtra(SearchControl.SEARCH_DOCUMENT);
		documentBeingIndexed = getSwordDocumentFacade().getDocumentByInitials(docInitials);
	}

	/**
	 * check index exists and go to search screen if index exists 
	 * if no more jobs in progress and no index then error
	 *
	 */
	@Override
	protected void jobFinished(Progress jobJustFinished) {
		// give the document up to 12 secs to reload - the Progress declares itself finished before the index status has been changed
		int attempts = 0;
		while (!IndexStatus.DONE.equals(documentBeingIndexed.getIndexStatus()) && attempts++<6) {
			CommonUtils.INSTANCE.pause(2);
		}
		
		// if index is fine then goto search
		if (IndexStatus.DONE.equals(documentBeingIndexed.getIndexStatus())) {
			Log.i(TAG, "Index created");
			Intent intent;
			if (StringUtils.isNotEmpty( getIntent().getStringExtra(SearchControl.SEARCH_TEXT) )) {
				// the search string was passed in so execute it directly
				intent = new Intent(this, SearchResults.class);
				intent.putExtras(getIntent().getExtras());
			} else {
				// just go to the normal Search screen
				intent = new Intent(this, Search.class);
			}
			startActivity(intent);
			finish();
		} else {
			// if jobs still running then just wait else error
			
			if (isAllJobsFinished()) {
				Log.e(TAG, "Index finished but document's index is invalid");
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
			}
		}
	}
}
