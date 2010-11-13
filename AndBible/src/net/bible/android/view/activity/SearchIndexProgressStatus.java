package net.bible.android.view.activity;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ProgressActivityBase;
import net.bible.service.common.CommonUtils;

import org.crosswire.common.progress.Progress;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.IndexStatus;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SearchIndexProgressStatus extends ProgressActivityBase {

	private Book documentBeingIndexed;
	
	private static final String TAG = "SearchIndexProgressStatus";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.search_index_status);

		hideButtons();
		setMainText(getString(R.string.indexing_wait_msg));
		
		documentBeingIndexed = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();

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
		while (!documentBeingIndexed.getIndexStatus().equals(IndexStatus.DONE) && attempts++<6) {
			CommonUtils.pause(2);
		}
		
		// if index is fine then goto search
		if (documentBeingIndexed.getIndexStatus().equals(IndexStatus.DONE)) {
			Log.i(TAG, "Index created going to search");
			Intent intent = new Intent(this, Search.class);
			startActivity(intent);
			finish();
		} else {
			// if jobs still running then just wait else error
			
			if (isAllJobsFinished()) {
				Log.e(TAG, "Index finished but document's index is invalid");
				showErrorMsg(getString(R.string.error_occurred));
			}
		}
	}
}
