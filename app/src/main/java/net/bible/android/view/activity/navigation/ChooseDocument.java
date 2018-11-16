package net.bible.android.view.activity.navigation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.DocumentSelectionBase;
import net.bible.android.view.activity.base.IntentHelper;
import net.bible.android.view.activity.download.Download;

import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class ChooseDocument extends DocumentSelectionBase {
	private static final String TAG = "ChooseDocument";

	private static final int LIST_ITEM_TYPE = R.layout.list_item_2_highlighted;

	private DownloadControl downloadControl;
	
	public ChooseDocument() {
		super(R.menu.choose_document_menu, R.menu.document_context_menu);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		buildActivityComponent().inject(this);

		initialiseView();

		DocumentItemAdapter documentItemAdapter = new DocumentItemAdapter(this, LIST_ITEM_TYPE, getDisplayedDocuments(), this);
		setListAdapter(documentItemAdapter);

		populateMasterDocumentList(false);

		Log.i(TAG, "ChooseDocument downloadControl:"+downloadControl);
	}

	/** load list of docs to display
	 * 
	 */
	@Override
	protected List<Book> getDocumentsFromSource(boolean refresh) {
		Log.d(TAG, "get document list from source");
		return getSwordDocumentFacade().getDocuments();
	}

	/** 
	 * Get normally sorted list of languages for the language selection spinner 
	 */
	@Override
	protected List<Language> sortLanguages(Collection<Language> languages) {
		List<Language> languageList = new ArrayList<>();

		if (languages!=null) {
			languageList.addAll(languages);
			
			// sort languages alphabetically
			Collections.sort(languageList);
		}
		return languageList;
	}
	
	@Override
	protected void handleDocumentSelection(Book selectedBook) {
		Log.d(TAG, "Book selected:"+selectedBook.getInitials());
		try {
			getDocumentControl().changeDocument(selectedBook);

			// if key is valid then the new doc will have been shown already
			returnToPreviousScreen();
		} catch (Exception e) {
			Log.e(TAG, "error on select of bible book", e);
		}
	}

	@Override
	protected void setInitialDocumentType() {
		setSelectedBookCategory(getDocumentControl().getCurrentCategory());
	}
	
	/** 
	 * on Click handlers
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean isHandled = false;
		
		switch (item.getItemId()) {
		// Jump to Download documents screen
		case (R.id.downloadButton):
			isHandled = true;
			try {
				if (downloadControl.checkDownloadOkay()) {
					Intent handlerIntent = new Intent(this, Download.class);
					int requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
					startActivityForResult(handlerIntent, requestCode);
					
					// do not return here after download
					finish();
				}
			} catch (Exception e) {
				Log.e(TAG, "Error sorting bookmarks", e);
				Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
			}

			break;
		}
		
		if (!isHandled) {
			isHandled = super.onOptionsItemSelected(item);
		}
		
	 	return isHandled;
	}

	@Inject
	void setDownloadControl(DownloadControl downloadControl) {
		this.downloadControl = downloadControl;
	}
}
