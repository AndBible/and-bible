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

package net.bible.android.view.activity.download;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import net.bible.android.activity.R;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

/**
 * Only allow progress into the main app once a Bible has been downloaded
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

public class FirstDownload extends Download {

	private Button okayButton;

	private boolean okayButtonEnabled = false;

	private WorkListener downloadCompletionListener;

	public FirstDownload() {
		// Normal document screen but with an added OK button to facilitate forward like flow to main screen
		setLayoutResource(R.layout.document_selection_with_ok);

		downloadCompletionListener = new WorkListener() {
			@Override
			public void workProgressed(WorkEvent workEvent) {
				if (workEvent.getJob().isFinished()) {
					enableOkayButtonIfBibles();
				}
			}

			@Override
			public void workStateChanged(WorkEvent workEvent) {
				// TODO this is never called so have to do it all in workProgressed
			}
		};
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		okayButton = (Button)findViewById(R.id.okayButton);
	}

	@Override
	protected void onStart() {
		super.onStart();

		enableOkayButtonIfBibles();

		JobManager.addWorkListener(downloadCompletionListener);
	}

	@Override
	protected void onStop() {
		super.onStop();

		JobManager.removeWorkListener(downloadCompletionListener);
	}

	private void enableOkayButtonIfBibles() {
		if (!okayButtonEnabled) {
			final boolean enable = getSwordDocumentFacade().getBibles().size() > 0;
			okayButtonEnabled = enable;

			runOnUiThread(
					new Runnable() {
						@Override
						public void run() {
							okayButton.setEnabled(enable);
						}
					}
			);
		}
	}

	public void onOkay(View v) {
		Intent resultIntent = new Intent(this, FirstDownload.class);
		setResult(Download.DOWNLOAD_FINISH, resultIntent);
		finish();
	}
}
