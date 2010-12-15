package net.bible.android.view.activity.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.android.view.activity.download.DownloadStatus;
import net.bible.android.view.activity.download.EnsureBibleDownloaded;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

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
public class Bookmarks extends ListActivityBase {
	private static final String TAG = "Bookmarks";

	private Bookmark bookmarkControl;
	
	// language spinner
	private Spinner labelSpinner;
	private List<LabelDto> labelList;
	private int selectedLabelNo = 0;
	private ArrayAdapter<LabelDto> labelArrayAdapter; 
	
	// the document list
	private ArrayAdapter<BookmarkDto> listArrayAdapter;
	private List<BookmarkDto> bookmarkList;

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

	private BookmarkDto selectedBookmark;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmarks);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        
       	initialiseView();
    }

    private void initialiseView() {
    	
    	bookmarkList = bookmarkControl.getAllBookmarks();
    	
    	// prepare the document list view
    	listArrayAdapter = new BookmarkItemAdapter(this, LIST_ITEM_TYPE, bookmarkList);
    	setListAdapter(listArrayAdapter);
    	
    	//prepare the Label spinner
    	labelList = bookmarkControl.getAllLabels();
    	labelArrayAdapter = new ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_item, labelList);
    	labelSpinner = (Spinner)findViewById(R.id.labelSpinner);
    	labelSpinner.setAdapter(labelArrayAdapter);
    	labelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedLabelNo = position;
		    	Bookmarks.this.filterBookmarks();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		bookmarkSelected(bookmarkList.get(position));
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    private void populateMasterDocumentList() {
//		if (allDocuments==null || allDocuments.size()==0) {
//    	    new AsyncTask<Void, Boolean, Void>() {
//    	    	
//    	        @Override
//    	        protected void onPreExecute() {
//    	        	showHourglass();
//    	        }
//    	        
//    			@Override
//    	        protected Void doInBackground(Void... noparam) {
//    				try {
//	    	        	allDocuments = SwordApi.getInstance().getDownloadableDocuments();
//	    	        	for (Iterator<Book> iter=allDocuments.iterator(); iter.hasNext(); ) {
//	    	        		Book doc = iter.next();
//	    	        		if (doc.getLanguage()==null) {
//	    	        			Log.d(TAG, "Ignoring "+doc.getName()+" because it has no language");
//	    	        			iter.remove();
//	    	        		}
//	    	        		if (doc.getInitials().equals("WebstersDict")) {
//	    	        			Log.d(TAG, "Removing "+doc.getName()+" because it is too big and crashed dictionary code");
//	    	        			iter.remove();
//	    	        		}
//	    	        	}
//	    	        	Log.i(TAG, "number of documents available:"+allDocuments.size());
//    				} catch (Exception e) {
//    					Log.e(TAG, "Error getting documents to download", e);
//    					//todo INTERNATIONALIZE
//    					showErrorMsg("Error getting documents to download");
//    				}
//    	        	return null;
//    			}
//    			
//    	        @Override
//				protected void onPostExecute(Void result) {
//    	        	try {
//    	        		populateLanguageList();
//    	        		filterDocuments();
//    	        	} finally {
//    	        		//todo implement this: http://stackoverflow.com/questions/891451/android-dismissdialog-does-not-dismiss-the-dialog
//        	        	dismissHourglass();
//    	        	}
//    	        }
//
//    	    }.execute((Void[])null);
//		}
    }
    
    /** a spinner has changed so refilter the doc list
     */
    private void filterBookmarks() {
    	try {
    		if (selectedLabelNo>-1 && selectedLabelNo<labelList.size()) {
   	        	Log.i(TAG, "filtering bookmarks");
   	        	
   	        	
	        	if (listArrayAdapter!=null) {
	        		Bookmarks.this.listArrayAdapter.notifyDataSetChanged();
	        	}
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+e.getMessage(), Toast.LENGTH_SHORT);
    	}
    }

    /** user selected a document so download it
     * 
     * @param document
     */
    private void bookmarkSelected(BookmarkDto bookmark) {
    	Log.d(TAG, "Bookmark selected:"+bookmark.getKey());
    	try {
        	if (bookmark!=null) {
        		CurrentPageManager.getInstance().getCurrentPage().setKey(bookmark.getKey());
        		doFinish();
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

	@Override
    protected Dialog onCreateDialog(int id) {
//    	Dialog superDlg = super.onCreateDialog(id);
//    	if (superDlg!=null) {
//    		return superDlg;
//    	}
//    	
//        switch (id) {
//        case DOWNLOAD_CONFIRMATION_DIALOG:
//            	return new AlertDialog.Builder(this)
//            		   .setMessage(getText(R.string.download_document_confirm_prefix)+selectedDocument.getName())
//            	       .setCancelable(false)
//            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
//            	           public void onClick(DialogInterface dialog, int id) {
//            	        	   doDownload(selectedDocument);
//            	           }
//            	       })
//            	       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
//            	           public void onClick(DialogInterface dialog, int id) {
//            	           }
//            	       }).create();
//        }
        return null;
    }

    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {
//		super.onPrepareDialog(id, dialog);
//        switch (id) {
//        case DOWNLOAD_CONFIRMATION_DIALOG:
//        	AlertDialog alertDialog = (AlertDialog)dialog;
//        	alertDialog.setMessage(getText(R.string.download_document_confirm_prefix)+selectedDocument.getName());
//        };
	}

    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//    	if (resultCode==DOWNLOAD_FINISH) {
//    		returnToPreviousScreen();
//    	}
    }

//	@Override
//	protected void onStop() {
//		super.onStop();
//		langSpinner = null;
//		languageList = new ArrayList<String>();
//		langArrayAdapter = null;		
//		listArrayAdapter = null;
//		allDocuments = null;
//		displayedDocuments = null;
//		displayedDocumentDescriptions = null;
//		selectedDocument = null;
//	}
}
