package net.bible.android.view.activity.bookmark;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkLabels extends ListActivityBase {

	private List<BookmarkDto> bookmarks;

	private Bookmark bookmarkControl;

	private static final String TAG = "BookmarkLabels";
	
	private List<LabelDto> labels = new ArrayList<>();

	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = android.R.layout.simple_list_item_multiple_choice; 
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);
        setContentView(R.layout.bookmark_labels);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        
        long[] bookmarkIds = getIntent().getLongArrayExtra(BookmarkControl.BOOKMARK_IDS_EXTRA);
        bookmarks = bookmarkControl.getBookmarksById(bookmarkIds);

        initialiseView();
    }

    private void initialiseView() {
    	getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

    	loadLabelList();
    	
    	ArrayAdapter<LabelDto> listArrayAdapter = new ArrayAdapter<>(this,
    	        LIST_ITEM_TYPE,
    	        labels);
    	setListAdapter(listArrayAdapter);
    	
		initialiseCheckedLabels(bookmarks);
    }

    /** Finished selecting labels
     */
    public void onOkay(View v) {
    	Log.i(TAG, "Okay clicked");
    	// get the labels that are currently checked
    	List<LabelDto> selectedLabels = getCheckedLabels();

    	//associate labels with bookmarks that were passed in
		for (BookmarkDto bookmark : bookmarks) {
			bookmarkControl.setBookmarkLabels(bookmark, selectedLabels);
		}
       	finish();
    }

	/**
	 * New Label requested
	 */
	public void onNewLabel(View v) {
		Log.i(TAG, "New label clicked");

		LabelDto newLabel = new LabelDto();
		new LabelDialogs().createLabel(this, newLabel, new Callback() {
			@Override
			public void okay() {
				List<LabelDto> selectedLabels = getCheckedLabels();
				Log.d(TAG, "Num labels checked pre reload:"+selectedLabels.size());

				loadLabelList();

				setCheckedLabels(selectedLabels);
				Log.d(TAG, "Num labels checked finally:"+selectedLabels.size());			}
		});
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
		notifyDataSetChanged();
	}

	/** check labels associated with the bookmark
	 */
	private void initialiseCheckedLabels(List<BookmarkDto> bookmarks) {
		Set<LabelDto> allCheckedLabels = new HashSet<>();
    	for (BookmarkDto bookmark : bookmarks) {
			// pre-tick any labels currently associated with the bookmark
			allCheckedLabels.addAll(bookmarkControl.getBookmarkLabels(bookmark));
		}
		setCheckedLabels(allCheckedLabels);
	}

	/**
	 * get checked status of all labels
	 */
	private List<LabelDto> getCheckedLabels() {
		// get selected labels
    	ListView listView = getListView();
    	List<LabelDto> checkedLabels = new ArrayList<>();
    	for (int i=0; i<labels.size(); i++) {
    		if (listView.isItemChecked(i)) {
    			LabelDto label = labels.get(i);
    			checkedLabels.add(label);
    			Log.d(TAG, "Selected "+label.getName());
    		}
    	}
		return checkedLabels;
	}

	/**
	 * set checked status of all labels
	 */
	private void setCheckedLabels(Collection<LabelDto> labelsToCheck) {
		for (int i=0; i<labels.size(); i++) {
    		if (labelsToCheck.contains(labels.get(i))) {
    			getListView().setItemChecked(i, true);
    		} else {
    			getListView().setItemChecked(i, false);
    		}
    	}

    	// ensure ui is updated
		notifyDataSetChanged();
	}
}