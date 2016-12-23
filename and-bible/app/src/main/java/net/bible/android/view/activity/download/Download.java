package net.bible.android.view.activity.download;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.DocumentSelectionBase;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;

import java.util.Collection;
import java.util.Date;
import java.util.List;

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

	private DocumentDownloadItemAdapter documentDownloadItemAdapter;

	private static final int LIST_ITEM_TYPE = R.layout.document_download_list_item;

	private DownloadControl downloadControl;

	private static final String REPO_REFRESH_DATE = "repoRefreshDate";
	private static final long REPO_LIST_STALE_AFTER_DAYS = 10;
	private static final long MILLISECS_IN_DAY = 1000*60*60*24;
	
	public static final int DOWNLOAD_FINISH = 1;
	private boolean downloadConfirmationShown = false;

	private static final String TAG = "Download";

	public Download() {
		super(NO_OPTIONS_MENU, R.menu.download_documents_context_menu);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		documentDownloadItemAdapter = new DocumentDownloadItemAdapter(this, LIST_ITEM_TYPE, getDisplayedDocuments(), this);
		setListAdapter(documentDownloadItemAdapter);

		downloadControl = ControlFactory.getInstance().getDownloadControl();
        
    	// in the basic flow we force the user to download a bible
    	getDocumentTypeSpinner().setEnabled(true);

		boolean firstTime = SwordDocumentFacade.getInstance().getBibles().size()==0;
       	// if first time
       	if (firstTime) {
        	// prepare the document list view - done in another thread
        	populateMasterDocumentList(false);
        	updateLastRepoRefreshDate();
       	} else if (isRepoBookListOld()) {
       		// normal user downloading but need to refresh the document list
       		Toast.makeText(this, R.string.download_refreshing_book_list, Toast.LENGTH_LONG).show();
       		
    		// prepare the document list view - done in another thread
    		populateMasterDocumentList(true);

    		// restart refresh timeout
    		updateLastRepoRefreshDate();
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



	@Override
	protected void onStart() {
		super.onStart();

		downloadControl.startMonitoringDownloads();
	}

	@Override
	protected void onStop() {
		super.onStop();

		downloadControl.stopMonitoringDownloads();
	}

    /** 
     * Get normally sorted list of languages for the language selection spinner 
     */
    @Override
	protected List<Language> sortLanguages(Collection<Language> languages) {
    	return downloadControl.sortLanguages(languages);
	}
    
    /** user selected a document so download it
     * 
     * @param document
     */
    @Override
    protected void handleDocumentSelection(Book document) {
    	Log.d(TAG, "Document selected:"+document.getInitials());
    	try {
   			manageDownload(document);
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

	private void showTooManyJobsDialog() {
		Log.i(TAG, "Too many jobs:"+JobManager.getJobCount());
		Dialogs.getInstance().showErrorMsg(R.string.too_many_jobs);
	}

    protected void manageDownload(final Book documentToDownload) {
    	if (documentToDownload!=null) {
			if (downloadConfirmationShown) {
				doDownload(documentToDownload);
			} else {
				new AlertDialog.Builder(this)
						.setMessage(getText(R.string.download_document_confirm_prefix) + " " + documentToDownload.getName())
						.setCancelable(false)
						.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								downloadConfirmationShown = true;
								doDownload(documentToDownload);
							}
						})
						.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
							}
						}).create().show();
			}
    	}
    }

	private void doDownload(Book document) {
    	try {
			// the download happens in another thread
			downloadControl.downloadDocument(document);

			// update screen so the icon to the left of the book changes
			notifyDataSetChanged();

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.download_documents, menu);
        return true;
    }

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        
        switch (item.getItemId()) {
		case (R.id.refresh):
       		// normal user downloading but need to refresh the document list
       		Toast.makeText(this, R.string.download_refreshing_book_list, Toast.LENGTH_LONG).show();
       		
    		// prepare the document list view - done in another thread
    		populateMasterDocumentList(true);

    		// restart refresh timeout
    		updateLastRepoRefreshDate();
    		
    		// update screen
    		notifyDataSetChanged();
    		
			isHandled = true;
			break;
        }
        
		if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }
}
