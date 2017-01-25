/**
 * 
 */
package net.bible.android.view.activity.mynote;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import net.bible.android.activity.R;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActionModeHelper;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.mynote.MyNoteDto;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Show a list of existing User Notes and allow view/edit/delete
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNotes extends ListActivityBase implements ListActionModeHelper.ActionModeActivity {

	private MyNoteControl myNoteControl;

	// the document list
	private List<MyNoteDto> myNoteList = new ArrayList<>();

	private ListActionModeHelper listActionModeHelper;

	private static final int LIST_ITEM_TYPE = R.layout.list_item_2_highlighted;

	private static final String TAG = "UserNotes";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		// integrateWithHistoryManager to ensure the previous document is loaded again when the user presses Back
        super.onCreate(savedInstanceState, true);

        setContentView(R.layout.list);

		buildActivityComponent().inject(this);

       	initialiseView();
    }

    private void initialiseView() {
		listActionModeHelper =  new ListActionModeHelper(getListView(), R.menu.usernote_context_menu);

		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				return listActionModeHelper.startActionMode(MyNotes.this, position);
			}
		});

		loadUserNoteList();
    	
    	// prepare the document list view
    	ArrayAdapter<MyNoteDto> myNoteArrayAdapter = new MyNoteItemAdapter(this, LIST_ITEM_TYPE, myNoteList, this, myNoteControl);
    	setListAdapter(myNoteArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
			// check to see if Action Mode is in operation
			if (!listActionModeHelper.isInActionMode()) {
				myNoteSelected(myNoteList.get(position));

				// HistoryManager will create a new Activity on Back
				finish();
			}
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
    	}
	}

	@Override
	public boolean onActionItemClicked(MenuItem item, List<Integer> selectedItemPositions) {
		List<MyNoteDto> selectedNotes = getSelectedMyNotes(selectedItemPositions);
		if (!selectedNotes.isEmpty()) {
			switch (item.getItemId()) {
				case (R.id.delete):
					delete(selectedNotes);
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean isItemChecked(int position) {
		return getListView().isItemChecked(position);
	}

	private List<MyNoteDto> getSelectedMyNotes(List<Integer> selectedItemPositions) {
		List<MyNoteDto> selectedNotes = new ArrayList<>();

		for (int position : selectedItemPositions) {
			selectedNotes.add(myNoteList.get(position));
		}

		return selectedNotes;
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mynote_actionbar_menu, menu);
        return true;
    }

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        
        switch (item.getItemId()) {
        // selected to allow jump to a certain day
		case (R.id.sortByToggle):
			isHandled = true;
	    	try {
	    		myNoteControl.changeSortOrder();
	    		String sortDesc = myNoteControl.getSortOrderDescription();
				Toast.makeText(this, sortDesc, Toast.LENGTH_SHORT).show();
				
	    		loadUserNoteList();
	        } catch (Exception e) {
	        	Log.e(TAG, "Error sorting notes", e);
	        	Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
	        }
			break;
        }
        
		if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }

    private void delete(List<MyNoteDto> myNotes) {
		for (MyNoteDto myNote : myNotes) {
			myNoteControl.deleteMyNote(myNote);
		}
		loadUserNoteList();
	}

	private void loadUserNoteList() {
		// item positions will all change so exit any action mode
		listActionModeHelper.exitActionMode();

    	myNoteList.clear();
    	myNoteList.addAll( myNoteControl.getAllMyNotes() );
    	
    	notifyDataSetChanged();
    }

    /**
	 * User selected a MyNote so download it
     * 
     * @param myNote
     */
    private void myNoteSelected(MyNoteDto myNote) {
    	Log.d(TAG, "User Note selected:"+myNote.getVerseRange());
    	try {
        	if (myNote!=null) {
        		myNoteControl.showNoteView(myNote);
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to show note", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
    	}
    }

	@Inject
	void setMyNoteControl(MyNoteControl myNoteControl) {
		this.myNoteControl = myNoteControl;
	}
}
