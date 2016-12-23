package net.bible.android.view.activity.base;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.service.download.DownloadManager;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.Language;
import org.crosswire.common.util.Version;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.book.sword.SwordBookMetaData;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
abstract public class DocumentSelectionBase extends ListActivityBase implements ListActionModeHelper.ActionModeActivity {

	// document type spinner
	private Spinner documentTypeSpinner;
	private static final BookFilter[] DOCUMENT_TYPE_SPINNER_FILTERS = new BookFilter[] {BookFilters.getBibles(), 
																						BookFilters.getCommentaries(), 
																						BookFilters.getDictionaries(), 
																						BookFilters.getGeneralBooks(),
																						BookFilters.getMaps()};
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

	private DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();

	private ListActionModeHelper listActionModeHelper;

	private final int actionModeMenuId;

	private int layoutResource = R.layout.document_selection;

    private static final String TAG = "DocumentSelectionBase";

	/** ask subclass for documents to be displayed
	 */
    abstract protected List<Book> getDocumentsFromSource(boolean refresh);
    
    abstract protected void handleDocumentSelection(Book selectedDocument);
    
    abstract protected List<Language> sortLanguages(Collection<Language> languages);

	public DocumentSelectionBase(int optionsMenuId, int actionModeMenuId) {
		super(optionsMenuId);
		this.actionModeMenuId = actionModeMenuId;
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutResource);

       	initialiseView();
    }

    private void initialiseView() {
		// prepare action mode
		listActionModeHelper =  new ListActionModeHelper(getListView(), actionModeMenuId);
		// trigger action mode on long press
		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			return listActionModeHelper.startActionMode(DocumentSelectionBase.this, position);
			}
		});

    	languageList = new ArrayList<>();
    	displayedDocuments = new ArrayList<>();
    	
    	//prepare the documentType spinner
    	documentTypeSpinner = (Spinner)findViewById(R.id.documentTypeSpinner);
    	setInitialDocumentType();
    	documentTypeSpinner.setSelection(selectedDocumentFilterNo);
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
	    	langArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languageList);
	    	langArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    	langSpinner.setAdapter(langArrayAdapter);
    	}
    }


	private void setDefaultLanguage() {
    	if (selectedLanguageNo==-1) {
    		Language lang;
    		// make selected language sticky
    		if (lastSelectedLanguage!=null && languageList.contains(lastSelectedLanguage)) {
    			lang = lastSelectedLanguage;
    		} else {
    			// set default lang to lang of mobile
    	    	lang = getDefaultLanguage();
    		}

	    	selectedLanguageNo = languageList.indexOf(lang);
    	}
    	
    	// if last doc in last lang was just deleted then need to adjust index
    	checkSpinnerIndexesValid();
    	
		langSpinner.setSelection(selectedLanguageNo);
    }
    
    protected Language getDefaultLanguage() {
    	// get the current language code
    	String langCode = Locale.getDefault().getLanguage();
    	if (!new Language(langCode).isValidLanguage()) {
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
    		localLanguage = Language.DEFAULT_LANG;
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

				// prevent the item remaining highlighted.  Unfortunately the highlight is cleared before the selection is handled.
				getListView().setItemChecked(position, false);
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
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
					// Prevent occasional class loading errors on Samsung devices
					Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

					allDocuments = getDocumentsFromSource(refresh);
    	        	Log.i(TAG, "Number of documents:"+allDocuments.size());
				} catch (Exception e) {
					Log.e(TAG, "Error getting documents", e);
		    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
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
			// documents list has changed so force action mode to exit, if displayed, because selections are invalidated
			listActionModeHelper.exitActionMode();

			// re-filter documents
			if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.d(TAG, "filtering documents");
	        	displayedDocuments.clear();
	        	Language lang = getSelectedLanguage();
	        	for (Book doc : allDocuments) {
	        		BookFilter filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo];
	        		if (filter.test(doc) && doc.getLanguage().equals(lang)) {
		        		displayedDocuments.add(doc);
	        		}
	        	}
	        	
	        	// sort by initials because that is field 1
	        	Collections.sort(displayedDocuments, new Comparator<Book>() {
	                public int compare(Book o1, Book o2) {
	                    return o1.getAbbreviation().compareToIgnoreCase(o2.getAbbreviation());
	                }
                });	        		
	        	
        		notifyDataSetChanged();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+" "+e.getMessage(), Toast.LENGTH_SHORT).show();
    	}
    }

    /** a spinner has changed so refilter the doc list
     */
    private void populateLanguageList() {
    	try {
    		// temporary Set to remove duplicate Languages
    		Set<Language> langSet = new HashSet<>();

    		if (allDocuments!=null && allDocuments.size()>0) {
   	        	Log.d(TAG, "initialising language list");
	        	for (Book doc : allDocuments) {
	        		langSet.add(doc.getLanguage());
	        	}
	        	
	        	List<Language> sortedLanguages = sortLanguages(langSet);
	        	
	        	languageList.clear();
	        	languageList.addAll(sortedLanguages);

	        	langArrayAdapter.notifyDataSetChanged();
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error initialising view", e);
    		Toast.makeText(this, getString(R.string.error)+" "+e.getMessage(), Toast.LENGTH_SHORT).show();
    	}
    }

	@Override
	public boolean onActionItemClicked(MenuItem item, List<Integer> selectedItemPositions) {
		List<Book> documents = new ArrayList<>();
		final List<Book> displayedDocuments = getDisplayedDocuments();
		for (int posn : selectedItemPositions) {
			if (posn < displayedDocuments.size()) {
				documents.add(displayedDocuments.get(posn));
			}
		}

		if (!documents.isEmpty()) {
			switch (item.getItemId()) {
				case (R.id.about):
					handleAbout(documents);
					return true;
				case (R.id.delete):
					handleDelete(documents);
					return true;
				case (R.id.delete_index):
					handleDeleteIndex(documents);
					return true;
			}
		}
		return false;
	}

	private void handleAbout(List<Book> documents) {
		Book document = documents.get(0);
		try {
			// ensure repo key is retained but reload sbmd to ensure About text is loaded
			SwordBookMetaData sbmd = (SwordBookMetaData)document.getBookMetaData();
			String repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY);
			sbmd.reload();
			sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey);

			showAbout(document);
		} catch (BookException e) {
			Log.e(TAG, "Error expanding SwordBookMetaData for " + document, e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
	}

	protected void handleDelete(final List<Book> documents) {
		for (final Book document : documents) {
			if (documentControl.canDelete(document)) {
				CharSequence msg = getString(R.string.delete_doc, document.getName());
				new AlertDialog.Builder(this)
						.setMessage(msg).setCancelable(true)
						.setPositiveButton(R.string.okay,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int buttonId) {
										try {
											Log.d(TAG, "Deleting:" + document);
											documentControl.deleteDocument(document);

											// the doc list should now change
											reloadDocuments();
										} catch (Exception e) {
											Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
										}
									}
								}
						)
						.create()
						.show();
			}
		}
	}
	
	protected void handleDeleteIndex(final List<Book> documents) {
		for (final Book document : documents) {
			CharSequence msg = getString(R.string.delete_search_index_doc, document.getName());
			new AlertDialog.Builder(this)
					.setMessage(msg).setCancelable(true)
					.setPositiveButton(R.string.okay,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int buttonId) {
									try {
										Log.d(TAG, "Deleting index:" + document);
										SwordDocumentFacade.getInstance().deleteDocumentIndex(document);
									} catch (Exception e) {
										Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
									}
								}
							}
					)
					.create()
					.show();
		}
	}

	@Override
	public boolean isItemChecked(int position) {
		return getListView().isItemChecked(position);
	}

	/** about display is generic so handle it here
	 */
	protected void showAbout(Book document) {
		
		//get about text
		String about = document.getBookMetaData().getProperty("About");
		if (about!=null) {
			// either process the odd formatting chars in about 
			about = about.replace("\\par", "\n");
		} else {
			// or default to name if there is no About
			about = document.getName();
		}

		// Copyright and distribution information
		String shortCopyright = document.getBookMetaData().getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT);
		String copyright = document.getBookMetaData().getProperty(SwordBookMetaData.KEY_COPYRIGHT);
		String distributionLicense = document.getBookMetaData().getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE);
		String copyrightMerged = "";
		if (StringUtils.isNotBlank(shortCopyright)) {
			copyrightMerged += shortCopyright+"\n";
		} else if (StringUtils.isNotBlank(copyright)) {
			copyrightMerged += copyright+"\n";
		}
		if (StringUtils.isNotBlank(distributionLicense)) {
			copyrightMerged += distributionLicense+"\n";
		}
		if (StringUtils.isNotBlank(copyrightMerged)) {
	        String copyrightMsg = BibleApplication.getApplication().getString(R.string.about_copyright, copyrightMerged);
			about += "\n\n"+copyrightMsg;
		}
		
		// add version
		final String version = document.getBookMetaData().getProperty("Version");
		if (version!=null) {
			Version versionObj = new Version(version);
	        String versionMsg = BibleApplication.getApplication().getString(R.string.about_version, versionObj.toString());
			about += "\n\n"+versionMsg;
		}

		// add versification
		if (document instanceof SwordBook) {
			Versification versification = ((SwordBook)document).getVersification();
	        String versificationMsg = BibleApplication.getApplication().getString(R.string.about_versification, versification.getName());
			about += "\n\n"+versificationMsg;
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

	/**
	 *  deletion may have removed a language or document type so need to check current spinner selection is still valid
	 */
	private void checkSpinnerIndexesValid() {
		if (selectedLanguageNo>=languageList.size()) {
			selectedLanguageNo = languageList.size()-1;
		}
	}


	private Language getSelectedLanguage() {
		if (selectedLanguageNo==-1) {
			setDefaultLanguage();
		}
		
		return languageList.get(selectedLanguageNo);
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

	/** allow selection of initial doc type
	 */
	protected void setInitialDocumentType() {
		selectedDocumentFilterNo = 0;
	}
	
	/** map between book category and item no
	 */
	public void setSelectedBookCategory(BookCategory bookCategory) {
    	switch (bookCategory) {
			case BIBLE:				selectedDocumentFilterNo = 0;  		break;
			case COMMENTARY:		selectedDocumentFilterNo = 1;  		break;
			case DICTIONARY:		selectedDocumentFilterNo = 2;  		break;
			case GENERAL_BOOK:		selectedDocumentFilterNo = 3;  		break;
			case MAPS:				selectedDocumentFilterNo = 4;  		break;
			default:				selectedDocumentFilterNo = 0;  		break;
    	}
	}

	public void setLayoutResource(int layoutResource) {
		this.layoutResource = layoutResource;
	}
}
