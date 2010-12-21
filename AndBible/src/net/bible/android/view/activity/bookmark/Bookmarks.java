package net.bible.android.view.activity.bookmark;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import android.R.integer;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
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
public class Bookmarks extends ListActivityBase {
	private static final String TAG = "Bookmarks";

	static final String BOOKMARK_EXTRA = "bookmark";

	private Bookmark bookmarkControl;
	
	// language spinner
	private Spinner labelSpinner;
	private List<LabelDto> labelList = new ArrayList<LabelDto>();
	private int selectedLabelNo = 0;
	private ArrayAdapter<LabelDto> labelArrayAdapter; 
	
	// the document list
	private ArrayAdapter<BookmarkDto> bookmarkArrayAdapter;
	private List<BookmarkDto> bookmarkList = new ArrayList<BookmarkDto>();

	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_2;

	private BookmarkDto selectedBookmark;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmarks);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        
       	initialiseView();
    }

    private void initialiseView() {
    	
    	//prepare the Label spinner
    	loadLabelList();
    	labelArrayAdapter = new ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_item, labelList);
    	labelSpinner = (Spinner)findViewById(R.id.labelSpinner);
    	labelSpinner.setAdapter(labelArrayAdapter);
    	labelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		    	selectedLabelNo = position;
		    	Bookmarks.this.loadBookmarkList();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
    	
    	loadBookmarkList();
    	
    	// prepare the document list view
    	bookmarkArrayAdapter = new BookmarkItemAdapter(this, LIST_ITEM_TYPE, bookmarkList);
    	setListAdapter(bookmarkArrayAdapter);
    	
    	registerForContextMenu(getListView());
    }

    @Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
    	try {
    		bookmarkSelected(bookmarkList.get(position));
    	} catch (Exception e) {
    		Log.e(TAG, "document selection error", e);
    		showErrorMsg(R.string.error_occurred);
    	}
	}

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.bookmark_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		BookmarkDto bookmark = bookmarkList.get(menuInfo.position);
		if (bookmark!=null) {
			switch (item.getItemId()) {
			case (R.id.assign_labels):
				assignLabels(bookmark);
				return true;
			case (R.id.delete):
				delete(bookmark);
				return true;
			}
		}
		return false; 
	}

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	// the bookmarkLabels activity may have added/deleted labels or changed the bookmarks with the current label
    	LabelDto prevLabel = labelList.get(selectedLabelNo);
    	
    	// reload labels
    	loadLabelList();
    	
    	int prevLabelPos = labelList.indexOf(prevLabel);
    	if (prevLabelPos>=0) {
    		selectedLabelNo = prevLabelPos;
    	} else {
    		// this should be 'All'
    		selectedLabelNo = 0;
    	}
    	
    	loadBookmarkList();
    }

    private void assignLabels(BookmarkDto bookmark) {
		Intent intent = new Intent(this, BookmarkLabels.class);
		intent.putExtra(BOOKMARK_EXTRA, bookmark.getId());
		startActivityForResult(intent, 1);
	}

	private void delete(BookmarkDto bookmark) {
		bookmarkControl.deleteBookmark(bookmark);
		loadBookmarkList();
	}
    
	private void loadLabelList() {
    	labelList.clear();
    	labelList.addAll(bookmarkControl.getAllLabels());
	}

	/** a spinner has changed so refilter the doc list
     */
    private void loadBookmarkList() {
    	try {
    		if (selectedLabelNo>-1 && selectedLabelNo<labelList.size()) {
   	        	Log.i(TAG, "filtering bookmarks");
   	        	LabelDto selectedLabel = labelList.get(selectedLabelNo);
   	        	bookmarkList.clear();
   	        	bookmarkList.addAll( bookmarkControl.getBookmarksWithLabel(selectedLabel) );
   	        	
	        	if (bookmarkArrayAdapter!=null) {
	        		Bookmarks.this.bookmarkArrayAdapter.notifyDataSetChanged();
	        	}
	        	
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
    private void bookmarkSelected(BookmarkDto bookmark) {
    	Log.d(TAG, "Bookmark selected:"+bookmark.getKey());
    	try {
        	if (bookmark!=null) {
        		CurrentPageManager.getInstance().getCurrentPage().setKey(bookmark.getKey());
        		doFinish();
        	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error on attempt to download", e);
    		Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show();
    	}
    }

    private void doFinish() {
    	Intent resultIntent = new Intent();
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
