package net.bible.android.view.activity.bookmark;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkLabels extends ListActivityBase {

	private BookmarkDto bookmark;

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
        
        long bookmarkId = getIntent().getLongExtra(Bookmarks.BOOKMARK_EXTRA, -1);
        bookmark = bookmarkControl.getBookmarkById(bookmarkId);

        initialiseView();
    }

    private void initialiseView() {
    	getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

    	loadLabelList();
    	
    	listArrayAdapter = new ArrayAdapter<LabelDto>(this,
    	        LIST_ITEM_TYPE,
    	        labels);
    	setListAdapter(listArrayAdapter);
    	
		updateCheckedLabels();

    	registerForContextMenu(getListView());
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
					
					List<LabelDto> selectedLabels = getCheckedLabels();
					Log.d(TAG, "Num labels checked pre reload:"+selectedLabels.size());
					
					loadLabelList();
					
					setCheckedLabels(selectedLabels);
					Log.d(TAG, "Num labels checked finally:"+selectedLabels.size());
				}  
	    	});  
    	  
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {  
    	  public void onClick(DialogInterface dialog, int whichButton) {  
    	    // Canceled.  
    	  }  
    	});  
    	  
    	alert.show();    
    }
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bookmark_labels_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		LabelDto label = labels.get(menuInfo.position);
		if (bookmark!=null) {
			switch (item.getItemId()) {
			case (R.id.delete):
				delete(label);
				return true;
			}
		}
		return false; 
	}
	
    /** Finished selecting labels
     *  
     * @param v
     */
    public void onOkay(View v) {
    	Log.i(TAG, "Okay clicked");
    	// get the labels that are currently checked
    	List<LabelDto> selectedLabels = getCheckedLabels();
    	
    	//associate labels with bookmark that was passed in    	
    	bookmarkControl.setBookmarkLabels(bookmark, selectedLabels);
       	finish();
    }

	private List<LabelDto> getCheckedLabels() {
		// get selected labels
    	ListView listView = getListView();
    	List<LabelDto> checkedLabels = new ArrayList<LabelDto>();
    	for (int i=0; i<labels.size(); i++) {
    		if (listView.isItemChecked(i)) {
    			LabelDto label = labels.get(i);
    			checkedLabels.add(label);
    			Log.d(TAG, "Selected "+label.getName());
    		}
    	}
		return checkedLabels;
	}

	private void delete(LabelDto label) {
		bookmarkControl.deleteLabel(label);
		loadLabelList();
		updateCheckedLabels();
	}
    


	/** load list of docs to display
	 * 
	 */
	private void loadLabelList() {
    	
    	// get long book names to show in the select list
		// must clear rather than create because the adapter is linked to this specific list
    	labels.clear();
		labels.addAll(bookmarkControl.getAssignableLabels());

    	// ensure ui is updated
    	if (listArrayAdapter!=null) {
			listArrayAdapter.notifyDataSetChanged();
		}
	}

	/** check labels associated with the bookmark
	 */
	private void updateCheckedLabels() {
    	
    	// pre-tick any labels currently associated with the bookmark
    	List<LabelDto> bookmarkLabels = bookmarkControl.getBookmarkLabels(bookmark);
    	setCheckedLabels(bookmarkLabels);
	}

	/** set checked status of all labels
	 * 
	 * @param labelsToCheck
	 */
	private void setCheckedLabels(List<LabelDto> labelsToCheck) {
		for (int i=0; i<labels.size(); i++) {
    		Log.d(TAG, "Is label "+i+" associated with bookmark");
    		if (labelsToCheck.contains(labels.get(i))) {
        		Log.d(TAG, "Yes");
    			getListView().setItemChecked(i, true);
    		} else {
        		Log.d(TAG, "No");
    			getListView().setItemChecked(i, false);
    		}
    	}

    	// ensure ui is updated
    	if (listArrayAdapter!=null) {
			listArrayAdapter.notifyDataSetChanged();
		}
	}
}