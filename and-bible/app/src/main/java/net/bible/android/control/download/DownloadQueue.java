package net.bible.android.control.download;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.Logger;
import net.bible.service.download.RepoBase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.install.InstallException;

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

	private Logger log = new Logger(this.getClass().getSimpleName());

	public DownloadQueue(ExecutorService executorService) {
		this.executorService = executorService;
	}

	public void addDocumentToDownloadQueue(final Book document, final RepoBase repo) {
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				log.info("Downloading "+document.getInitials()+" from repo "+repo.getRepoName());
				try {
					repo.downloadDocument(document);
				} catch (InstallException | BookException e) {
					Dialogs.getInstance().showErrorMsg(R.string.error_downloading);
				}
			}
		});
	}
}
