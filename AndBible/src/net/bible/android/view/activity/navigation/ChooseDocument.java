package net.bible.android.view.activity.navigation;

import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.DocumentSelectionBase;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseDocument extends DocumentSelectionBase {
	private static final String TAG = "ChooseDocument";
	
	private DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	setInstallStatusIconsShown(false);
    	
        super.onCreate(savedInstanceState);
        
        setDeletePossible(true);
    	populateMasterDocumentList(false);
    }

	/** load list of docs to display
	 * 
	 */
    @Override
    protected List<Book> getDocumentsFromSource(boolean refresh) {
		Log.d(TAG, "get document list from source");
		return SwordDocumentFacade.getInstance().getDocuments();
	}

    @Override
    protected void handleDocumentSelection(Book selectedBook) {
    	Log.d(TAG, "Book selected:"+selectedBook.getInitials());
    	try {
    		documentControl.changeDocument(selectedBook);

    		// if key is valid then the new doc will have been shown already
			finishedSelection();
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
    	}
    }

    private void finishedSelection() {
    	Log.i(TAG, "finished selection");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
    @Override
    protected void setInitialDocumentType() {
    	setSelectedBookCategory(documentControl.getCurrentCategory());
    }
}
