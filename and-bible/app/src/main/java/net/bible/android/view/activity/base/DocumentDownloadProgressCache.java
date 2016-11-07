package net.bible.android.view.activity.base;

import android.support.annotation.NonNull;
import android.widget.ProgressBar;

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
			documentDownloadProgressItem.updateProgressBar();
		}
	}

	/**
	 * This progressBar is no longer visible and should not be updated
	 */
	public void documentHidden(Book document) {
		if (document!=null) {
			DocumentDownloadProgressItem documentDownloadProgressItem = getOrCreateBookProgressInfo(document.getInitials());
			documentDownloadProgressItem.setProgressBar(null);
		}
	}

	/**
	 * This progressBar is now visible visible but the document may not be being downloaded
	 */
	public void documentShown(Book document, final ProgressBar progressBar) {
		if (document!=null && progressBar!=null) {
			final DocumentDownloadProgressItem documentDownloadProgressItem = getOrCreateBookProgressInfo(document.getInitials());
			documentDownloadProgressItem.setProgressBar(progressBar);

			documentDownloadProgressItem.updateProgressBar();
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
