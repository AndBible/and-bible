package net.bible.android.activity;

import org.crosswire.common.progress.Progress;

import android.os.Bundle;

public class SearchIndexProgressStatus extends ProgressStatus {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		hideOkayButton();
		setMainText(getString(R.string.indexing_wait_msg));
	}

	@Override
	protected void jobFinished(Progress job) {
		//todo check index exists and go to search screen
		// if index exists or no more jobs in progress
		//    goto search
		
		
	}

}
