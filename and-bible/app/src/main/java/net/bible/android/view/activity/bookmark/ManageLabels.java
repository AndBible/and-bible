package net.bible.android.view.activity.bookmark;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.LabelDto;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ManageLabels extends ListActivityBase {

	private List<LabelDto> labels = new ArrayList<>();

	private BookmarkControl bookmarkControl;

	private LabelDialogs labelDialogs;

	private static final String TAG = "BookmarkLabels";
	
	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = R.layout.manage_labels_list_item;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);
        setContentView(R.layout.manage_labels);

		super.buildActivityComponent().inject(this);

        initialiseView();
    }

    private void initialiseView() {
    	getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

    	loadLabelList();

		// prepare the document list view
    	ArrayAdapter<LabelDto> listArrayAdapter = new ManageLabelItemAdapter(this, LIST_ITEM_TYPE, labels, this);
    	setListAdapter(listArrayAdapter);
    }

	public void delete(LabelDto label) {
		// delete label from db
		bookmarkControl.deleteLabel(label);
		
		// now refetch the list of labels
		loadLabelList();
	}

    /** 
     * New Label requested
     */
    public void onNewLabel(View v) {
    	Log.i(TAG, "New label clicked");

    	LabelDto newLabel = new LabelDto();
		labelDialogs.createLabel(this, newLabel, new Callback() {
			@Override
			public void okay() {
				loadLabelList();
			}
		});
	}

	/**
	 * New Label requested
	 */
	public void editLabel(LabelDto label) {
		Log.i(TAG, "Edit label clicked");

		labelDialogs.editLabel(this, label, new Callback() {
			@Override
			public void okay() {
				loadLabelList();
			}
		});
	}

	/** Finished editing labels
	 */
	public void onOkay(View v) {
		finish();
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

	@Inject
	void setBookmarkControl(BookmarkControl bookmarkControl) {
		this.bookmarkControl = bookmarkControl;
	}

	@Inject
	public void setLabelDialogs(LabelDialogs labelDialogs) {
		this.labelDialogs = labelDialogs;
	}
}