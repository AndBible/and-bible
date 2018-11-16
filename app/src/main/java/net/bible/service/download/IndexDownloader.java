package net.bible.service.download;

import android.util.Log;

import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.bridge.BookIndexer;

import java.io.IOException;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class IndexDownloader {

	private static final String TAG = "IndexDownloader";
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.Installer#install(org.crosswire.jsword
     * .book.Book)
     */
    public void downloadIndexInNewThread(final Installer installer, final Book book) {
    	// So now we know what we want to install - all we need to do
        // is installer.install(name) however we are doing it in the
        // background so we create a job for it.
        final Thread worker = new Thread("DisplayPreLoader") //$NON-NLS-1$
        {
            public void run() {
            	Log.i(TAG, "Starting index download thread - book:"+book.getInitials());

				downloadIndex(installer, book);
			}
		};

        // this actually starts the thread off
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

	public void downloadIndex(final Installer installer, final Book book) {
		try {
			BookIndexer bookIndexer = new BookIndexer(book);

			// Delete the index, if present
			// At the moment, JSword will not re-install. Later it will, if the
			// remote version is greater.
			if (bookIndexer.isIndexed()) {
				Log.d(TAG, "deleting index");
				bookIndexer.deleteIndex();
			}

			try {
				org.crosswire.jsword.util.IndexDownloader.downloadIndex(book, installer);
			} catch (IOException e) {
				Reporter.informUser(this, "IO Error creating index");
				throw new RuntimeException("IO Error downloading index", e);
			}
			Log.i(TAG, "Finished index download thread");
		} catch (Exception e) {
			Log.e(TAG, "Error downloading index", e);
			Reporter.informUser(this, "Error downloading index");
		}
	}
}
