package net.bible.android.view.activity.base;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ProgressBar;

import net.bible.android.view.util.Threadutils;
import net.bible.android.view.util.widget.DocumentListItem;

public class DocumentDownloadProgressItem {
	private int percentDone;
	private @Nullable DocumentListItem documentListItem;

	public synchronized void setPercentDone(int percentDone) {
		this.percentDone = percentDone;
	}

	public synchronized void setDocumentListItem(@Nullable DocumentListItem documentListItem) {
		this.documentListItem = documentListItem;
	}

	public synchronized void updateListItemDisplay() {
		updateListItemDisplay(percentDone);
	}

	/**
	 * If this document is still using the reallocated list item then clear this item's reference to prevent update for the wrong document.
	 */
	public synchronized void documentListItemReallocated(DocumentListItem documentListItem) {
		if (documentListItem == this.documentListItem) {
			setDocumentListItem(null);
		}
	}

	private void updateListItemDisplay(final int percentDone) {
		if (documentListItem!=null) {
			final ProgressBar progressBar = documentListItem.getProgressBar();
				if (progressBar != null && progressBar.getParent() != null) {
				Threadutils.runOnUiThread(
						new Runnable() {
							@Override
							public void run() {
								progressBar.setProgress(percentDone);
									progressBar.setVisibility(percentDone > 0 ? View.VISIBLE : View.GONE);
							}
						}
				);
			}
		}
	}
}
