package net.bible.android.view.activity.navigation;

import java.util.List;
import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.DocumentSelectionBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseDocument extends DocumentSelectionBase {
	private static final String TAG = "ChooseDocument";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
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
		return SwordApi.getInstance().getDocuments();
	}

    @Override
    protected void handleDocumentSelection(Book selectedBook) {
    	Log.d(TAG, "Book selected:"+selectedBook.getInitials());
    	try {
    		ControlFactory.getInstance().getDocumentControl().changeDocument(selectedBook);

    		// if key is valid then the new doc will have been shown already
			finishedSelection();
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of bible book", e);
    	}
    }

    @Override
	protected void handleDelete(final Book document) {
		CharSequence msg = getString(R.string.delete_doc, document.getName());
		new AlertDialog.Builder(this)
			.setMessage(msg).setCancelable(true)
			.setPositiveButton(R.string.okay,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,	int buttonId) {
						try {
							Log.d(TAG, "Deleting:"+document);
							SwordApi.getInstance().deleteDocument(document);

							// the doc list should now change
							reloadDocuments();
						} catch (Exception e) {
							showErrorMsg(R.string.error_occurred);
						}
					}
				}
			)
			.create()
			.show();
	}
	
	private void finishedSelection() {
    	Log.i(TAG, "finished selection");
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
