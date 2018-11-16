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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
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
		Intent resultIntent = new Intent(this, DownloadStatus.class);
		setResult(Download.DOWNLOAD_FINISH, resultIntent);
		finish();
	}
}
