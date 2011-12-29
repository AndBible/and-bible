/**
 * 
 */
package net.bible.android.view.activity.mynote;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.mynote.MyNoteDto;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Show a list of existing User Notes and allow view/edit/delete
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNotes extends ListActivityBase {
	private static final String TAG = "UserNotes";

	private MyNote myNoteControl;
	
	// the document list
	private List<MyNoteDto> myNoteList = new ArrayList<MyNoteDto>();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		// integrateWithHistoryManager to ensure the previous document is loaded again when the user presses Back
        super.onCreate(savedInstanceState, true);

        setContentView(R.layout.list);

        myNoteControl = ControlFactory.getInstance().getMyNoteControl();
        
       	initialiseView();
    }

    private void initialiseView() {
    	loadUserNoteList();
    	
    	// prepare the document list view
    	ArrayAdapter<MyNoteDto> myNoteArrayAdapter = new MyNoteItemAdapter(this, LIST_ITEM_TYPE, myNoteList);
    	setListAdapter(myNoteArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		myNoteSelected(myNoteList.get(position));
    		
    		// HistoryManager will create a new Activity on Back
    		finish();
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.usernote_context_menu, menu);
	} 

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        MyNoteDto myNote = myNoteList.get(menuInfo.position);
		if (myNote!=null) {
			switch (item.getItemId()) {
			case (R.id.delete):
				delete(myNote);
				return true;
			}
		}
		return false; 
	}

	private void delete(MyNoteDto myNote) {
		myNoteControl.deleteMyNote(myNote);
		loadUserNoteList();
	}

	private void loadUserNoteList() {
    	myNoteList.clear();
    	myNoteList.addAll( myNoteControl.getAllMyNotes() );
    	
    	notifyDataSetChanged();
    }

    /** user selected a document so download it
     * 
     * @param document
     */
    private void myNoteSelected(MyNoteDto myNote) {
    	Log.d(TAG, "User Note selected:"+myNote.getKey());
    	try {
        	if (myNote!=null) {
        		myNoteControl.showNoteView(myNote);
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to show note", e);
    		Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
    	}
    }
}
