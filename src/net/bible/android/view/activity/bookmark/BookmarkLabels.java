package net.bible.android.view.activity.bookmark;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.LabelDto;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkLabels extends ListActivityBase {

	private Bookmark bookmarkControl;

	private static final String TAG = "BookmarkLabels";
	
	private List<LabelDto> labels = new ArrayList<LabelDto>();
	private ArrayAdapter<LabelDto> listArrayAdapter;
	
	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_multiple_choice; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmark_labels);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        initialiseView();
    }

    private void initialiseView() {
    	loadLabelList();
    	
    	listArrayAdapter = new ArrayAdapter<LabelDto>(this,
    	        LIST_ITEM_TYPE,
    	        labels);
    	setListAdapter(listArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

    /** Finished selecting labels
     *  
     * @param v
     */
    public void onOkay(View v) {
    	Log.i(TAG, "Okay clicked");
//    	boolean bOk = ControlFactory.getInstance().getSearchControl().downloadIndex();
//
//    	if (bOk) {
//        	// monitor the progress
//        	Intent myIntent = new Intent(this, SearchIndexProgressStatus.class);
//        	startActivity(myIntent);
        	finish();
//    	}
    }

    /** Finished selecting labels
     *  
     * @param v
     */
    public void onNewLabel(View v) {
    	Log.i(TAG, "New label clicked");

    	// Set an EditText view to get user input   
    	final EditText labelInput = new EditText(this);  

    	AlertDialog.Builder alert = new AlertDialog.Builder(this)
										.setTitle(R.string.new_label)
										.setMessage("Message")
										.setView(labelInput);
    	
    	alert.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {  
		    	public void onClick(DialogInterface dialog, int whichButton) {  
					String name = labelInput.getText().toString();
					LabelDto label = new LabelDto();
					label.setName(name);
					bookmarkControl.addLabel(label);
					loadLabelList();
				}  
	    	});  
    	  
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {  
    	  public void onClick(DialogInterface dialog, int whichButton) {  
    	    // Canceled.  
    	  }  
    	});  
    	  
    	alert.show();    
    }
    
	/** load list of docs to display
	 * 
	 */
	private void loadLabelList() {
    	
    	// get long book names to show in the select list
		// must clear rather than create because the adapter is linked to this specific list
    	labels.clear();
		labels.addAll(bookmarkControl.getAllLabels());
		
		if (listArrayAdapter!=null) {
			listArrayAdapter.notifyDataSetChanged();
		}
		
	}

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	labelSelected(labels.get(position));
    }

//    @Override
//	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//		super.onCreateContextMenu(menu, v, menuInfo);
//		MenuInflater inflater = getMenuInflater();
//		inflater.inflate(R.menu.document_context_menu, menu);
//		
//		Book document = documents.get( ((AdapterContextMenuInfo)menuInfo).position);
//		MenuItem deleteItem = menu.findItem(R.id.delete);
//		
//		boolean canDelete = ControlFactory.getInstance().getDocumentControl().canDelete(document);
//		deleteItem.setEnabled(canDelete);
//	}
//
//	@Override
//	public boolean onContextItemSelected(MenuItem item) {
//		super.onContextItemSelected(item);
//        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
//		Book document = documents.get(menuInfo.position);
//		if (document!=null) {
//			switch (item.getItemId()) {
//			case (R.id.about):
//				showAbout(document);
//				return true;
//			case (R.id.delete):
//				delete(document);
//				return true;
//			}
//		}
//		return false; 
//	}

	private void labelSelected(LabelDto selectedLabel) {
//    	Log.d(TAG, "Book selected:"+selectedBook.getInitials());
//    	try {
//    		CurrentPage newPage = CurrentPageManager.getInstance().setCurrentDocument( selectedBook );
//    		
//    		// page will change due to above
//    		// if there is a valid key then show the page straight away
//    		if (newPage.getKey()==null) {
//    			// no key set for this doc type so show a key chooser
//    			//TODO this code is generic and needs to be performed whenever a doc changes so think where to put it
//    	    	Intent intent = new Intent(this, newPage.getKeyChooserActivity());
//    	    	startActivity(intent);
//    	    	finish();    
//    		} else {
//    			// if key is valid then the new doc will have been shown already
//    			returnToMainBibleView();
//    		}
//    	} catch (Exception e) {
//    		Log.e(TAG, "error on select of bible book", e);
//    	}
    }


//	private void delete(final Book document) {
//			CharSequence msg = getString(R.string.delete_doc, document.getName());
//			new AlertDialog.Builder(this)
//				.setMessage(msg).setCancelable(true)
//				.setPositiveButton(R.string.okay,
//					new DialogInterface.OnClickListener() {
//						public void onClick(DialogInterface dialog,	int buttonId) {
//							try {
//								SwordApi.getInstance().deleteDocument(document);
//
//								// the doc list should now change
//								loadDocumentList();
//								((ArrayAdapter) getListAdapter()).notifyDataSetChanged();
//							} catch (Exception e) {
//								showErrorMsg(R.string.error_occurred);
//							}
//						}
//					}
//				)
//				.create()
//				.show();
//	}

//	private void returnToMainBibleView() {
//    	Log.i(TAG, "returning to main bible view");
//    	Intent resultIntent = new Intent();
//    	setResult(Activity.RESULT_OK, resultIntent);
//    	finish();    
//    }
}
