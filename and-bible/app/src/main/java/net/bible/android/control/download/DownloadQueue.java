package net.bible.android.control.download;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Logger;
import net.bible.service.download.RepoBase;

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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class DownloadQueue {

	private final ExecutorService executorService;

	private Set<String> beingQueued = Collections.synchronizedSet(new HashSet<String>());

	private Logger log = new Logger(this.getClass().getSimpleName());

	public DownloadQueue(ExecutorService executorService) {
		this.executorService = executorService;
	}

	public void addDocumentToDownloadQueue(final Book document, final RepoBase repo) {
		if (!beingQueued.contains(document.getInitials())) {
			beingQueued.add(document.getInitials());
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					log.info("Downloading " + document.getInitials() + " from repo " + repo.getRepoName());

					try {
						repo.downloadDocument(document);
					} catch (InstallException | BookException e) {
						Dialogs.getInstance().showErrorMsg(R.string.error_downloading);
					} finally {
						beingQueued.remove(document.getInitials());
					}
				}
			});
		}
	}

	public boolean isInQueue(Book document) {
		return beingQueued.contains(document.getInitials());
	}
}
