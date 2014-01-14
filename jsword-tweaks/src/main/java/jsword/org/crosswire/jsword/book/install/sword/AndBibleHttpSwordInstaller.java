package org.crosswire.jsword.book.install.sword;

import java.net.URI;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.util.Version;
import org.crosswire.jsword.JSMsg;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.install.InstallException;

/** 
 * And Bible version of the installer to allow custom location for indexes to be downloaded from
 */
public class AndBibleHttpSwordInstaller extends HttpSwordInstaller {

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.install.Installer#downloadSearchIndex(org.crosswire.jsword.book.Book, java.net.URI)
     */
	@Override
    public void downloadSearchIndex(Book book, URI localDest) throws InstallException {
        // TRANSLATOR: Progress label for downloading one or more files.
        String jobName = JSMsg.gettext("Downloading files");
        Progress job = JobManager.createJob(String.format(Progress.DOWNLOAD_SEARCH_INDEX, book.getInitials()), jobName, Thread.currentThread());
        job.beginJob(jobName);

        //MJD START
        //use and-bible index location
        String indexLocation = "/and-bible/indices/v1";
        try {
            Version versionObj = (Version)book.getBookMetaData().getProperty("Version");
            // if a module has no version then default version is blank
            String versionSuffix = "";
            if (versionObj!=null && versionObj.toString()!=null) {
                versionSuffix = "-"+versionObj.toString();
            }
            download(job, indexLocation, book.getInitials()+versionSuffix + ZIP_SUFFIX, localDest);
            //MJD END
//          download(job, packageDirectory + '/' + SEARCH_DIR, book.getInitials() + ZIP_SUFFIX, localDest);
        } catch (InstallException ex) {
            job.cancel();
            throw ex;
        } finally {
            job.done();
        }
    }
}
