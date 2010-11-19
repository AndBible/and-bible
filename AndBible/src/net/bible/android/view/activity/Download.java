package net.bible.android.view.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;

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
public class Download extends ListActivityBase {
	private static final String TAG = "Download";

	// document type spinner
	private static final BookFilter[] DOCUMENT_TYPE_SPINNER_FILTERS = new BookFilter[] {BookFilters.getBibles(), BookFilters.getCommentaries(), BookFilters.getDictionaries()};
	private int selectedDocumentFilterNo = 0;

	// language spinner
	private Spinner langSpinner;
	private List<String> languageList;
	private int selectedLanguageNo = 0;
	private ArrayAdapter<String> langArrayAdapter; 
	
	// the document list
	private ArrayAdapter<String> listArrayAdapter;
	private List<Book> allDocuments;
	//TODO just use displayedDocuments with a model giving 2 lines in list
	private List<Book> displayedDocuments;
	private List<String> displayedDocumentDescriptions;

	private boolean forceBasicFlow;
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_1;

	private Book selectedDocument;
	
	private static final int DOWNLOAD_CONFIRMATION_DIALOG = 33;
	public static final int DOWNLOAD_MORE_RESULT = 10;
	public static final int DOWNLOAD_FINISH = 1;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        forceBasicFlow = SwordApi.getInstance().getBibles().size()==0;

       	initialiseView();
       	
       	Toast.makeText(this, R.string.download_source_message, Toast.LENGTH_LONG).show();
    }

    private void initialiseView() {
    	languageList = new ArrayList<String>();
    	displayedDocuments = new ArrayList<Book>();
    	displayedDocumentDescriptions = new ArrayList<String>();
    	
    	// prepare the document list view
    	populateMasterDocumentList();
    	listArrayAdapter = new ArrayAdapter<String>(this,
    	        LIST_ITEM_TYPE,
    	        displayedDocumentDescriptions);
    	setListAdapter(listArrayAdapter);
    	
    	//prepare the documentType spinner
    	Spinner documentTypeSpinner = (Spinner)findViewById(R.id.documentTypeSpinner);
    	documentTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedDocumentFilterNo = position;
		    	Download.this.filterDocuments();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    	// in the basic flow we force the user to download a bible
    	documentTypeSpinner.setEnabled(!forceBasicFlow);

    	//prepare the language spinner
    	{
	    	langSpinner = (Spinner)findViewById(R.id.languageSpinner);
	    	langSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
	
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			    	selectedLanguageNo = position;
			    	Download.this.filterDocuments();
				}
	
				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
	    	langArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, languageList);
	    	langArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    	langSpinner.setAdapter(langArrayAdapter);
    	}
    }
    
    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		documentSelected(displayedDocuments.get(position));
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    private void populateMasterDocumentList() {
		if (allDocuments==null || allDocuments.size()==0) {
    	    new AsyncTask<Void, Boolean, Void>() {
    	    	
    	        @Override
    	        protected void onPreExecute() {
    	        	showHourglass();
    	        }
    	        
    			@Override
    	        protected Void doInBackground(Void... noparam) {
    				try {
	    	        	allDocuments = SwordApi.getInstance().getDownloadableDocuments();
	    	        	for (Iterator<Book> iter=allDocuments.iterator(); iter.hasNext(); ) {
	    	        		Book doc = iter.next();
	    	        		if (doc.getLanguage()==null) {
	    	        			Log.d(TAG, "Ignoring "+doc.getName()+" because it has no language");
	    	        			iter.remove();
	    	        		}
	    	        		if (doc.getInitials().equals("WebstersDict")) {
	    	        			Log.d(TAG, "Removing "+doc.getName()+" because it is too big and crashed dictionary code");
	    	        			iter.remove();
	    	        		}
	    	        	}
	    	        	Log.i(TAG, "number of documents available:"+allDocuments.size());
    				} catch (Exception e) {
    					Log.e(TAG, "Error getting documents to download", e);
    					//todo INTERNATIONALIZE
    					showErrorMsg("Error getting documents to download");
    				}
    	        	return null;
    			}
    			
    	        @Override
				protected void onPostExecute(Void result) {
    	        	try {
    	        		populateLanguageList();
    	        		filterDocuments();
    	        	} finally {
    	        		//todo implement this: http://stackoverflow.com/questions/891451/android-dismissdialog-does-not-dismiss-the-dialog
        	        	dismissHourglass();
    	        	}
    	        }

    	    }.execute((Void[])null);
		}
    }
    
    /** a spinner has changed so refilter the doc list
     */
    private void filterDocuments() {
    	try {
    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.i(TAG, "filtering documents");
	        	displayedDocuments.clear();
	        	displayedDocumentDescriptions.clear();
	        	String lang = languageList.get(selectedLanguageNo);
	        	for (Book doc : allDocuments) {
	        		BookFilter filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo];
	        		if (filter.test(doc) && doc.getLanguage().getName().equals(lang)) {
		        		displayedDocuments.add(doc);
		        		displayedDocumentDescriptions.add(doc.getName());
	        		}
	        	}
	        	if (listArrayAdapter!=null) {
	        		Download.this.listArrayAdapter.notifyDataSetChanged();
	        	}
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+e.getMessage(), Toast.LENGTH_SHORT);
    	}
    }

    /** a spinner has changed so refilter the doc list
     */
    private void populateLanguageList() {
    	try {
    		// temporary map to help us see if lang is already added
    		Map<String, String> langMap = new HashMap<String,String>();
    		
    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.i(TAG, "initialising language list");
	        	languageList.clear();
	        	for (Book doc : allDocuments) {
	        		Language lang = doc.getLanguage();
	        		if (lang!=null) {
		        		String docLangName = lang.getName();
		        		if (!langMap.containsKey(docLangName)) {
			        		languageList.add(docLangName);
			        		langMap.put(docLangName, null);
		        		}
	        		}
	        	}
	        	/// sort languages alphabetically
	        	Collections.sort(languageList);
	        	//todo select language native to mobile
	        	for (int i=0; i<languageList.size(); i++) {
	        		if (languageList.get(i).equalsIgnoreCase("English")) {
	        			Log.d(TAG, "Found english at "+i);
	        			selectedLanguageNo = i;
	        			langSpinner.setSelection(i);
	        		}
	        	}
	        	langArrayAdapter.notifyDataSetChanged();
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
    private void documentSelected(Book document) {
    	Log.d(TAG, "Document selected:"+document.getInitials());
    	try {
    		//sometimes the selected doc is null if the list was not clicked properly - odd!
    		if (document!=null) {
	    		this.selectedDocument = document;
	
	    		if (JobManager.getJobs().size()>=2) {
	    			Log.i(TAG, "Too many jobs:"+JobManager.getJobs().size());
	    			Dialogs.getInstance().showErrorMsg(R.string.too_many_jobs, new Callback() {
						@Override
						public void okay() {
							showDownloadStatus();
						}
					});
	    		} else {
	    			showDialog(DOWNLOAD_CONFIRMATION_DIALOG);
	    		}
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

	public void showDownloadStatus() {
		Intent intent = new Intent(this, DownloadStatus.class);
		startActivityForResult(intent, 1);
	}

	@Override
    protected Dialog onCreateDialog(int id) {
    	Dialog superDlg = super.onCreateDialog(id);
    	if (superDlg!=null) {
    		return superDlg;
    	}
    	
        switch (id) {
        case DOWNLOAD_CONFIRMATION_DIALOG:
            	return new AlertDialog.Builder(this)
            		   .setMessage(getText(R.string.download_document_confirm_prefix)+selectedDocument.getName())
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int id) {
            	        	   doDownload(selectedDocument);
            	           }
            	       })
            	       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int id) {
            	           }
            	       }).create();
        }
        return null;
    }

    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
        switch (id) {
        case DOWNLOAD_CONFIRMATION_DIALOG:
        	AlertDialog alertDialog = (AlertDialog)dialog;
        	alertDialog.setMessage(getText(R.string.download_document_confirm_prefix)+selectedDocument.getName());
        };
	}

	private void doDownload(Book document) {
    	try {
			// the download happens in another thread
			SwordApi.getInstance().downloadDocument(document);
	    	Log.d(TAG, "Download requested");
	    	
	    	Intent intent;
	    	if (forceBasicFlow) {
	    		intent = new Intent(this, EnsureBibleDownloaded.class);
	    		finish();
	    		removeDialog(DOWNLOAD_CONFIRMATION_DIALOG);
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
    	if (resultCode==DOWNLOAD_FINISH) {
    		returnToPreviousScreen();
    	}
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
