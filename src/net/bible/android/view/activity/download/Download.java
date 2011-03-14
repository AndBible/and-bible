package net.bible.android.view.activity.download;

import java.util.Date;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.DocumentSelectionBase;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.jsword.book.Book;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Choose Document (Book) to download
 * 
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Download extends DocumentSelectionBase {

	private static final String TAG = "Download";

	private boolean forceBasicFlow;
	
	private DownloadControl downloadControl;

	private static final String REPO_REFRESH_DATE = "repoRefreshDate";
	private static final long REPO_LIST_STALE_AFTER_DAYS = 30;
	private static final long MILLISECS_IN_DAY = 1000*60*60*24;
	
	public static final int DOWNLOAD_MORE_RESULT = 10;
	public static final int DOWNLOAD_FINISH = 1;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        downloadControl = ControlFactory.getInstance().getDownloadControl();

        forceBasicFlow = SwordApi.getInstance().getBibles().size()==0;
        
    	// in the basic flow we force the user to download a bible
    	getDocumentTypeSpinner().setEnabled(!forceBasicFlow);
       	
       	// if first time
       	if (forceBasicFlow) {
        	// prepare the document list view - done in another thread
        	populateMasterDocumentList(false);
        	updateLastRepoRefreshDate();
       	} else if (isRepoBookListOld()) {
       		// normal user downloading but with old doc list
       		// this will also trigger populateMasterDocumentList
       		promptRefreshBookList();
       	} else {
       		// normal user downloading with recent doc list
        	populateMasterDocumentList(false);
       	}
    }

    /** if repo list not refreshed in last 30 days then it is old
     * 
     * @return
     */
    private boolean isRepoBookListOld() {
    	long repoRefreshDate = CommonUtils.getSharedPreferences().getLong(REPO_REFRESH_DATE, 0);
    	Date today = new Date();
    	return (today.getTime()-repoRefreshDate)/MILLISECS_IN_DAY > REPO_LIST_STALE_AFTER_DAYS;
    }
    
    private void promptRefreshBookList() {
    	new AlertDialog.Builder(this)
			.setMessage(getText(R.string.refresh_book_list))
			.setCancelable(false)
			.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// prepare the document list view - done in another thread
					populateMasterDocumentList(true);
				 	updateLastRepoRefreshDate();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// prepare the document list view - done in another thread
					populateMasterDocumentList(false);
					updateLastRepoRefreshDate();
				 }
			}).create().show();
	 }
	 
    private void updateLastRepoRefreshDate() {
	 	Date today = new Date();
		CommonUtils.getSharedPreferences().edit().putLong(REPO_REFRESH_DATE, today.getTime()).commit();
    }
    
    @Override
    protected void showPreLoadMessage() {
       	Toast.makeText(this, R.string.download_source_message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    protected List<Book> getDocumentsFromSource(boolean refresh) {
    	return downloadControl.getDownloadableDocuments(refresh);
    }
    
    /** user selected a document so download it
     * 
     * @param document
     */
    @Override
    protected void handleDocumentSelection(Book document) {
    	Log.d(TAG, "Document selected:"+document.getInitials());
    	try {
    		if (JobManager.getJobs().size()>=2) {
    			showTooManyJobsDialog();
    		} else {
    			manageDownload(document);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

	private void showTooManyJobsDialog() {
		Log.i(TAG, "Too many jobs:"+JobManager.getJobs().size());
		Dialogs.getInstance().showErrorMsg(R.string.too_many_jobs, new Callback() {
			@Override
			public void okay() {
				showDownloadStatus();
			}
		});
	}

	public void showDownloadStatus() {
		Intent intent = new Intent(this, DownloadStatus.class);
		startActivityForResult(intent, 1);
	}

    protected void manageDownload(final Book documentToDownload) {
    	if (documentToDownload!=null) {
        	new AlertDialog.Builder(this)
    		   .setMessage(getText(R.string.download_document_confirm_prefix)+documentToDownload.getName())
    	       .setCancelable(false)
    	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   doDownload(documentToDownload);
    	           }
    	       })
    	       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	           }
    	       }).create().show();
    	}
    }

	private void doDownload(Book document) {
    	try {
			// the download happens in another thread
			SwordApi.getInstance().downloadDocument(document);
	    	Log.d(TAG, "Download requested");
	    	
	    	Intent intent;
	    	if (forceBasicFlow) {
	    		intent = new Intent(this, EnsureBibleDownloaded.class);
	    		// finish this so when EnsureDalogDownload finishes we go straight back to StartupActivity which will start MainBibleActivity
	    		finish();
	    	} else {
	    		intent = new Intent(this, DownloadStatus.class);
	    	}
        	startActivityForResult(intent, 1);

    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}

    }
    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	Log.d(TAG, "onActivityResult:"+resultCode);
    	if (resultCode==DOWNLOAD_FINISH) {
    		returnToPreviousScreen();
    	} else {
    		//result code == DOWNLOAD_MORE_RESULT redisplay this download screen
    	}
    }

}
