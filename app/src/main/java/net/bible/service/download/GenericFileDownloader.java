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

package net.bible.service.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.FileManager;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.util.LucidException;
import org.crosswire.common.util.NetUtil;
import org.crosswire.common.util.Reporter;
import org.crosswire.common.util.WebResource;
import org.crosswire.jsword.JSMsg;
import org.crosswire.jsword.book.install.InstallException;

import android.util.Log;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class GenericFileDownloader {
	
	private static final String TAG = "GenericFileDownloader";

	public void downloadFileInBackground(final URI source, final File target, final String description) {

        // So now we know what we want to install - all we need to do
        // is installer.install(name) however we are doing it in the
        // background so we create a job for it.
        final Thread worker = new Thread("DisplayPreLoader")
        {
            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            /* @Override */
            public void run() {
            	Log.i(TAG, "Starting generic download thread - file:"+target.getName());
            	
            	try {
	                // Delete the file, if present
	                if (target.exists()) {
	                    Log.d(TAG, "deleting file");
	                    target.delete();
	                }
	
	                try {
	                	downloadFile(source, target, description);
	                    
	                } catch (Exception e) {
	            		Reporter.informUser(this, "IO Error creating index");
	                    throw new RuntimeException("IO Error downloading index", e);
	                }
	            	Log.i(TAG, "Finished index download thread");
            	} catch (Exception e) {
            		Log.e(TAG, "Error downloading index", e);
            		Reporter.informUser(this, "Error downloading index");
            	}
            }
        };

        // this actually starts the thread off
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
	}
	
	public void downloadFile(URI source, File target, String description) {
        String jobName = JSMsg.gettext("Downloading : {0}", target.getName()+" "+ description);
        Progress job = JobManager.createJob(jobName);

        // Don't bother setting a size, we'll do it later.
        job.beginJob(jobName);

        // allow displays to show the new job (at least I think that is why JSword put a yield here)
        Thread.yield();

        URI temp = null;
        try {
            // TRANSLATOR: Progress label indicating the Initialization of installing of a book.
            job.setSectionName(JSMsg.gettext("Initializing"));

            temp = NetUtil.getTemporaryURI("swd", ".tmp");

            copy(job, source, temp);

            // Once the download is complete, we need to continue
            job.setCancelable(false);
            if (!job.isFinished()) {
            	File tempFile = NetUtil.getAsFile(temp);
                if (!FileManager.INSTANCE.copyFile(tempFile, target)) {
                	Log.e(TAG, "Download Error renaming temp file "+tempFile+" to:"+target);
                    Reporter.informUser(this, CommonUtils.INSTANCE.getResourceString(R.string.error_occurred));
                    job.cancel();
                }
            }

        } catch (IOException e) {
            Reporter.informUser(this, e);
            job.cancel();
        } catch (InstallException e) {
            Reporter.informUser(this, e);
            job.cancel();
        } finally {
            job.done();
            // tidy up after ourselves
            // This is a best effort. If for some reason it does not delete now
            // it will automatically be deleted when the JVM exits normally.
            if (temp != null) {
                try {
                    NetUtil.delete(temp);
                } catch (IOException e) {
                    Log.w(TAG, "Error deleting temp download file:" + e.getMessage());
                }
            }
        }

	}
	
    private void copy(Progress job, URI source, URI dest) throws InstallException {
    	Log.d(TAG, "Downloading "+source+" to "+dest);
        if (job != null) {
            // TRANSLATOR: Progress label for downloading one or more files.
            job.setSectionName(JSMsg.gettext("Downloading files"));
        }

        // last 2 params are proxies which I hope we can ignore on Android
        WebResource wr = new WebResource(source, null, null);
        try {
        	wr.copy(dest, job);
        } catch (LucidException le) {
            // TRANSLATOR: Common error condition: {0} is a placeholder for the URL of what could not be found.
            throw new InstallException(JSMsg.gettext("Unable to find: {0}", source.toString()), le);
        } finally {
        	wr.shutdown();
        }
    }
}
