package net.bible.android.control.download;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;
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

	private Map<String, Integer> percentDoneByInitials = new HashMap<>();

	private WorkListener progressUpdater;

	private static final String INSTALL_BOOK_JOB_NAME = "INSTALL_BOOK-";

	public DocumentDownloadProgressCache() {
		progressUpdater = new WorkListener() {
			@Override
			public void workProgressed(WorkEvent ev) {
				sendProgressEvent(ev.getJob());
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				sendProgressEvent(ev.getJob());
			}
		};
	}

	public void startMonitoringDownloads() {
		JobManager.addWorkListener(progressUpdater);
	}

	public void stopMonitoringDownloads() {
		JobManager.removeWorkListener(progressUpdater);
	}

	/**
	 * Download has progressed and the ui needs updating if this file item is visible
	 */
	public void sendProgressEvent(Progress progress) {
		final String jobID = progress.getJobID();
		if (jobID.startsWith(INSTALL_BOOK_JOB_NAME)) {
			String initials = jobID.substring(INSTALL_BOOK_JOB_NAME.length());

			final int percentDone = progress.getWork();
			percentDoneByInitials.put(initials, percentDone);

			ABEventBus.getDefault().post(new DocumentDownloadEvent(initials, DocumentStatus.DocumentInstallStatus.BEING_INSTALLED, percentDone));
		}
	}

	public int getPercentDone(Book document) {
		Integer percentDone = percentDoneByInitials.get(document.getInitials());
		return percentDone!=null ? percentDone : 0;
	}
}
