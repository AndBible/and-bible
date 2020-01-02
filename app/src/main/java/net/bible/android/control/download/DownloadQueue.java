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

import net.bible.android.activity.R;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Logger;
import net.bible.service.download.AndBibleRepo;
import net.bible.service.download.RepoBase;
import net.bible.service.download.RepoFactory;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.install.InstallException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Download a single document at a time.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DownloadQueue {

	private final ExecutorService executorService;
	private final RepoFactory repoFactory;

	private Set<String> beingQueued = Collections.synchronizedSet(new HashSet<String>());

	private Set<String> downloadError = Collections.synchronizedSet(new HashSet<String>());

	private Logger log = new Logger(this.getClass().getSimpleName());

	public DownloadQueue(ExecutorService executorService, RepoFactory repoFactory) {
		this.executorService = executorService;
		this.repoFactory = repoFactory;
	}

	public void addDocumentToDownloadQueue(final Book document, final RepoBase repo) {
		if (!beingQueued.contains(document.getInitials())) {
			beingQueued.add(document.getInitials());
			downloadError.remove(document.getInitials());

			executorService.submit(new Runnable() {
				@Override
				public void run() {
					log.info("Downloading " + document.getInitials() + " from repo " + repo.getRepoName());
					try {
						repo.downloadDocument(document);
						ABEventBus.getDefault().post(new DocumentDownloadEvent(document.getInitials(), DocumentStatus.DocumentInstallStatus.INSTALLED, 100));
					} catch (Exception e) {
						log.error("Error downloading "+document, e);
						handleDownloadError(document);
					} finally {
						beingQueued.remove(document.getInitials());
					}
				}
			});
		}
	}

	private void handleDownloadError(Book document) {
		ABEventBus.getDefault().post(new DocumentDownloadEvent(document.getInitials(), DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0));
		downloadError.add(document.getInitials());
		Dialogs.getInstance().showErrorMsg(R.string.error_downloading);
	}

	public void addDocumentIndexToDownloadQueue(final Book document) {
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				log.info("Downloading index of " + document.getInitials() + " from AndBible repo");
				try {
					final AndBibleRepo andBibleRepo = repoFactory.getAndBibleRepo();
					andBibleRepo.downloadIndex(document);
				} catch (InstallException | BookException e) {
					Dialogs.getInstance().showErrorMsg(R.string.error_downloading);
				}
			}
		});
	}

	public boolean isInQueue(Book document) {
		return beingQueued.contains(document.getInitials());
	}

	public boolean isErrorDownloading(Book document) {
		return downloadError.contains(document.getInitials());
	}
}
