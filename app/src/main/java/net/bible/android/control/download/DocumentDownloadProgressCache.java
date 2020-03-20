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
