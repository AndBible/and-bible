package net.bible.android.view.activity.base;

import android.support.annotation.NonNull;

import net.bible.android.view.util.widget.DocumentListItem;

import org.crosswire.common.progress.Progress;
import org.crosswire.jsword.book.Book;

import java.util.HashMap;
import java.util.Map;

/**
 * Store download view items for dynamic update as downloading occurs.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class DocumentDownloadProgressCache {

	private Map<String, DocumentDownloadProgressItem> progressInfoByInitials = new HashMap<>();

	private static final String INSTALL_BOOK_JOB_NAME = "INSTALL_BOOK-";

	/**
	 * Download has progressed and teh ui needs updating if this file item is visible
	 */
	public void updateProgress(Progress progress) {
		final String jobID = progress.getJobID();
		if (jobID.startsWith(INSTALL_BOOK_JOB_NAME)) {
			String initials = jobID.substring(INSTALL_BOOK_JOB_NAME.length());

			DocumentDownloadProgressItem documentDownloadProgressItem = getOrCreateBookProgressInfo(initials);

			documentDownloadProgressItem.setPercentDone(progress.getWork());
			documentDownloadProgressItem.updateListItemDisplay();
		}
	}

	/**
	 * This document list item is no longer visible and should not be updated
	 */
	public void documentListItemHidden(Book document) {
		if (document!=null) {
			DocumentDownloadProgressItem documentDownloadProgressItem = getOrCreateBookProgressInfo(document.getInitials());
			documentDownloadProgressItem.setDocumentListItem(null);
		}
	}

	/**
	 * This document list item is now visible visible but the document may not be being downloaded
	 */
	public void documentListItemShown(Book document, final DocumentListItem documentListItem) {
		if (document!=null && documentListItem!=null) {
			final DocumentDownloadProgressItem documentDownloadProgressItem = getOrCreateBookProgressInfo(document.getInitials());
			documentDownloadProgressItem.setDocumentListItem(documentListItem);

			documentDownloadProgressItem.updateListItemDisplay();
		}
	}

	private @NonNull
	DocumentDownloadProgressItem getOrCreateBookProgressInfo(String initials) {
		DocumentDownloadProgressItem documentDownloadProgressItem = progressInfoByInitials.get(initials);
		if (documentDownloadProgressItem ==null) {
			documentDownloadProgressItem = new DocumentDownloadProgressItem();
			progressInfoByInitials.put(initials, documentDownloadProgressItem);
		}
		return documentDownloadProgressItem;
	}
}
