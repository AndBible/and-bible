package net.bible.service.download;

import java.io.IOException;

import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.common.util.Reporter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.book.sword.SwordBookMetaData;
import org.crosswire.jsword.bridge.BookIndexer;
import org.crosswire.jsword.util.IndexDownloader;

import android.util.Log;

public class IndexDownloadThread {

	private static final String TAG = "IndexDownloadThread";
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.install.Installer#install(org.crosswire.jsword
     * .book.Book)
     */
    public void downloadIndex(final Installer installer, final Book book) {
        // // Is the Index already installed? Then nothing to do.
        // if (Books.installed().getBook(book.getName()) != null)
        // {
        // return;
        // }
        //
        final SwordBookMetaData sbmd = (SwordBookMetaData) book.getBookMetaData();

        // So now we know what we want to install - all we need to do
        // is installer.install(name) however we are doing it in the
        // background so we create a job for it.
        final Thread worker = new Thread("DisplayPreLoader") //$NON-NLS-1$
        {
            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            /* @Override */
            public void run() {
            	Log.i(TAG, "Starting index download thread - book:"+book.getInitials());
            	
            	try {
	                BookIndexer bookIndexer = new BookIndexer(book);
	
	                // Delete the book, if present
	                // At the moment, JSword will not re-install. Later it will, if the
	                // remote version is greater.
	                if (bookIndexer.isIndexed()) {
	                    Log.d(TAG, "** deleting index");
	                    bookIndexer.deleteIndex();
	                }
	
	                try {
	                	IndexDownloader.downloadIndex(book, installer);
	                    
	                } catch (IOException e) {
	            		Reporter.informUser(this, "IO Error creating index");
	                    throw new RuntimeException("IO Error downloading index", e);
	                }
	            	Log.i(TAG, "Finished index download thread");
            	} catch (Exception e) {
            		Log.e(TAG, "Error downloading index", e);
            		Reporter.informUser(this, "Error downloading index");
            	}
//                
//
//                yield();
//
//                URI tempDownload = null;
//                try {
//                    job.setSectionName(UserMsg.JOB_INIT.toString());
//
//                    tempDownload = NetUtil.getTemporaryURI("swd", ZIP_SUFFIX); //$NON-NLS-1$
//                    log.debug("Temporary URI:"+tempDownload.toString());
//
//                    download(job, packageDirectory, sbmd.getInitials() + ZIP_SUFFIX, tempDownload);
//
//                    // Once the unzipping is started, we need to continue
//                    job.setCancelable(false);
//                    if (!job.isFinished()) {
//                        File dldir = SwordBookPath.getSwordDownloadDir();
//                        IOUtil.unpackZip(NetUtil.getAsFile(tempDownload), dldir);
//                        job.setSectionName(UserMsg.JOB_CONFIG.toString());
//                        sbmd.setLibrary(NetUtil.getURI(dldir));
//                        SwordBookDriver.registerNewBook(sbmd);
//                    }
//
//                } catch (IOException e) {
//                    Reporter.informUser(this, e);
//                    job.cancel();
//                } catch (InstallException e) {
//                    Reporter.informUser(this, e);
//                    job.cancel();
//                } catch (BookException e) {
//                    Reporter.informUser(this, e);
//                    job.cancel();
//                } finally {
//                    job.done();
//                    // tidy up after ourselves
//                    if (tempDownload != null) {
//                        try {
//                            NetUtil.delete(tempDownload);
//                        } catch (Exception e) {
//                            log.warn("Error deleting temp download file:"+e.getMessage());
//                        }
//                        
//                    }
//                }
            }
        };

        // this actually starts the thread off
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

}
