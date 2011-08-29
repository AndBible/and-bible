package net.bible.android.view.activity.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;

import org.crosswire.common.util.Language;
import org.crosswire.common.util.Languages;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * Choose Document (Book)
 * 
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
abstract public class DocumentSelectionBase extends ListActivityBase {

	// document type spinner
	private Spinner documentTypeSpinner;
	private static final BookFilter[] DOCUMENT_TYPE_SPINNER_FILTERS = new BookFilter[] {BookFilters.getBibles(), 
																						BookFilters.getCommentaries(), 
																						BookFilters.getDictionaries(), 
																						BookFilters.getGeneralBooks()};
	private int selectedDocumentFilterNo = 0;

	// language spinner
	private Spinner langSpinner;
	private List<Language> languageList;
	private int selectedLanguageNo = -1;
	private static Language lastSelectedLanguage; // allow sticky language selection
	private ArrayAdapter<Language> langArrayAdapter; 
	
	// the document list
	private List<Book> allDocuments;
	//TODO just use displayedDocuments with a model giving 2 lines in list
	private List<Book> displayedDocuments;

	private boolean isDeletePossible;
	
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

	private static final String TAG = "DocumentSelectionBase";

	/** ask subclass for documents to be displayed
	 */
    abstract protected List<Book> getDocumentsFromSource(boolean refresh);
    
    abstract protected void handleDocumentSelection(Book selectedDocument);

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.document_selection);

       	initialiseView();
       	
    	registerForContextMenu(getListView());
    }

    private void initialiseView() {
    	languageList = new ArrayList<Language>();
    	displayedDocuments = new ArrayList<Book>();
    	
    	ArrayAdapter<Book> listArrayAdapter = new DocumentItemAdapter(this, LIST_ITEM_TYPE, displayedDocuments);
    	setListAdapter(listArrayAdapter);
    	
    	//prepare the documentType spinner
    	documentTypeSpinner = (Spinner)findViewById(R.id.documentTypeSpinner);
    	documentTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedDocumentFilterNo = position;
		    	DocumentSelectionBase.this.filterDocuments();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

    	//prepare the language spinner
    	{
	    	langSpinner = (Spinner)findViewById(R.id.languageSpinner);
	    	langSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
	
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			    	selectedLanguageNo = position;
			    	lastSelectedLanguage = languageList.get(selectedLanguageNo);
			    	DocumentSelectionBase.this.filterDocuments();
				}
	
				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
	    	langArrayAdapter = new ArrayAdapter<Language>(this, android.R.layout.simple_spinner_item, languageList);
	    	langArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    	langSpinner.setAdapter(langArrayAdapter);
    	}
    }

    private void setDefaultLanguage() {
    	if (selectedLanguageNo==-1) {
    		Language lang = null;
    		// make selected language sticky
    		if (lastSelectedLanguage!=null && languageList.contains(lastSelectedLanguage)) {
    			lang = lastSelectedLanguage;
    		} else {
    			// set default lang to lang of mobile
    	    	lang = getDefaultLanguage();
    		}

	    	selectedLanguageNo = languageList.indexOf(lang);
    	}
		langSpinner.setSelection(selectedLanguageNo);
    }
    
    protected Language getDefaultLanguage() {
    	// get the current language code
    	String langCode = Locale.getDefault().getLanguage();
    	if (!Languages.isValidLanguage(langCode)) {
    		langCode = Locale.ENGLISH.getLanguage();
    	}

    	// create the JSword Language for current lang
    	Language localLanguage = new Language(langCode); 
    	Log.d(TAG, "Local language is:"+localLanguage);

    	// check a bible exists in current lang otherwise use english
    	boolean foundBibleInLocalLanguage = false;
    	for (Book book : getAllDocuments()) {
    		if (book.getBookCategory().equals(BookCategory.BIBLE) && localLanguage.equals(book.getLanguage())) {
    			foundBibleInLocalLanguage = true;
    			break;
    		}
    	}
    	
    	// if no bibles exist in current lang then fall back to default language (English) so the user will not see an initially empty list
    	if (!foundBibleInLocalLanguage) {
        	Log.d(TAG, "No bibles found in local language so falling back to default lang");
    		localLanguage = new Language(Languages.DEFAULT_LANG_CODE);
    	}
    	return localLanguage;
    }


    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		if (position>=0 && position<displayedDocuments.size()) {
        		Book selectedBook = displayedDocuments.get(position);
        		if (selectedBook!=null) {
        			Log.d(TAG, "Selected "+selectedBook.getInitials());
        			handleDocumentSelection(selectedBook);
        		}
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    protected void reloadDocuments() {
		populateMasterDocumentList(false);
	}
    
    protected void showPreLoadMessage() {
    	// default to no message
    }
    
    protected void populateMasterDocumentList(final boolean refresh) {
		Log.d(TAG, "populate Master Document List");

	    new AsyncTask<Void, Boolean, Void>() {
	    	
	        @Override
	        protected void onPreExecute() {
	        	showHourglass();
	        	showPreLoadMessage();
	        }
	        
			@Override
	        protected Void doInBackground(Void... noparam) {
				try {
    	        	allDocuments = getDocumentsFromSource(refresh);
    	        	Log.i(TAG, "number of documents:"+allDocuments.size());
				} catch (Exception e) {
					Log.e(TAG, "Error getting documents", e);
					//todo INTERNATIONALIZE
					showErrorMsg("Error getting documents");
				}
	        	return null;
			}
			
	        @Override
			protected void onPostExecute(Void result) {
	        	try {
	        		if (allDocuments!=null) {
    	        		populateLanguageList();
    	        		
    	        		// default language depends on doc availability so must do in onPostExecute
    	    	    	setDefaultLanguage();
    	        		filterDocuments();
	        		}
	        	} finally {
	        		//todo implement this: http://stackoverflow.com/questions/891451/android-dismissdialog-does-not-dismiss-the-dialog
    	        	dismissHourglass();
	        	}
	        }

	    }.execute((Void[])null);
    }
    
    
    
    /** a spinner has changed so refilter the doc list
     */
    private void filterDocuments() {
    	try {
    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.d(TAG, "filtering documents");
	        	displayedDocuments.clear();
	        	Language lang = languageList.get(selectedLanguageNo);
	        	for (Book doc : allDocuments) {
	        		BookFilter filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo];
	        		if (filter.test(doc) && doc.getLanguage().equals(lang)) {
		        		displayedDocuments.add(doc);
	        		}
	        	}
	        	
	        	// sort by initials because that is field 1
	        	Collections.sort(displayedDocuments, new Comparator<Book>() {
	                public int compare(Book o1, Book o2) {
	                    return o1.getInitials().compareToIgnoreCase(o2.getInitials());
	                }
                });	        		
	        	
        		notifyDataSetChanged();
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
    		// temporary Set to remove duplicate Languages
    		Set<Language> langSet = new HashSet<Language>();

    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.d(TAG, "initialising language list");
	        	for (Book doc : allDocuments) {
	        		langSet.add(doc.getLanguage());
	        	}
	        	
	        	languageList.clear();
	        	languageList.addAll(langSet);

	        	// sort languages alphabetically
	        	Collections.sort(languageList);

	        	langArrayAdapter.notifyDataSetChanged();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+e.getMessage(), Toast.LENGTH_SHORT);
    	}
    }

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.document_context_menu, menu);
		
		Book document = getDisplayedDocuments().get( ((AdapterContextMenuInfo)menuInfo).position);
		Log.d(TAG, "Context selected "+document.getInitials());
		
		// delete document
		MenuItem deleteItem = menu.findItem(R.id.delete);
		deleteItem.setVisible(isDeletePossible);
		if (isDeletePossible) {
			boolean canDeleteCurrentDocument = ControlFactory.getInstance().getDocumentControl().canDelete(document);
			deleteItem.setEnabled(canDeleteCurrentDocument);
		}

		// delete index
		MenuItem deleteIndexItem = menu.findItem(R.id.delete_index);
		deleteIndexItem.setVisible(isDeletePossible);
	}


	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		Book document = getDisplayedDocuments().get(menuInfo.position);
		Log.d(TAG, "Selected "+document.getInitials());
		if (document!=null) {
			switch (item.getItemId()) {
			case (R.id.about):
				showAbout(document);
				return true;
			case (R.id.delete):
				handleDelete(document);
				return true;
			case (R.id.delete_index):
				handleDeleteIndex(document);
				return true;
			}
		}
		return false; 
	}

	protected void handleDelete(final Book document) {
		// default is that delete is disabled, override to handle delete if enabled
    }
	protected void handleDeleteIndex(final Book document) {
		// default is that delete is disabled, override to handle delete if enabled
    }

	/** about display is generic so handle it here
	 */
	protected void showAbout(Book document) {
		
		//get about text
		Map<String,Object> props = document.getBookMetaData().getProperties();
		String about = (String)props.get("About");
		if (about!=null) {
			// either process the odd formatting chars in about 
			about = about.replace("\\par", "\n");
		} else {
			// or default to name if there is no About
			about = document.getName();
		}

    	new AlertDialog.Builder(this)
		   .setMessage(about)
	       .setCancelable(false)
	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
	           public void onClick(DialogInterface dialog, int buttonId) {
	        	   //do nothing
	           }
	       }).create().show();
	}

	public void setDeletePossible(boolean isDeletePossible) {
		this.isDeletePossible = isDeletePossible;
	}

	public Spinner getDocumentTypeSpinner() {
		return documentTypeSpinner;
	}

	public List<Book> getAllDocuments() {
		return allDocuments;
	}

	public List<Book> getDisplayedDocuments() {
		return displayedDocuments;
	}
}
