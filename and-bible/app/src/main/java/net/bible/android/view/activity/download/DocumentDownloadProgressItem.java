package net.bible.android.view.activity.download;

import android.support.annotation.Nullable;

import net.bible.android.view.util.Threadutils;

public class DocumentDownloadProgressItem {
	private int percentDone;

	@Nullable
	private DocumentDownloadListItem documentDownloadListItem;

	public synchronized void setPercentDone(int percentDone) {
		this.percentDone = percentDone;
	}

	public synchronized void setDocumentDownloadListItem(@Nullable DocumentDownloadListItem documentDownloadListItem) {
		this.documentDownloadListItem = documentDownloadListItem;
	}

	public synchronized void updateListItemDisplay() {
		updateListItemDisplay(percentDone);
	}

	/**
	 * If this document is still using the reallocated list item then clear this item's reference to prevent update for the wrong document.
	 */
	public synchronized void documentListItemReallocated(DocumentDownloadListItem documentDownloadListItem) {
		if (documentDownloadListItem == this.documentDownloadListItem) {
			setDocumentDownloadListItem(null);
		}
	}

	private void updateListItemDisplay(final int percentDone) {
		final DocumentDownloadListItem listItem = this.documentDownloadListItem;
		if (listItem !=null) {
			Threadutils.runOnUiThread(
					new Runnable() {
						@Override
						public void run() {
							listItem.setProgressPercent(percentDone);
						}
					}
			);
		}
	}
}
