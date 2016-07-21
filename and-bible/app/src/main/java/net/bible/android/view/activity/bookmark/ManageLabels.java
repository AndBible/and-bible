package net.bible.android.view.activity.bookmark;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.view.activity.base.ListActivityBase;
import net.bible.service.db.bookmark.LabelDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Choose a bible or commentary to use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ManageLabels extends ListActivityBase {

	private List<LabelDto> labels = new ArrayList<>();

	private Bookmark bookmarkControl;

	private static final String TAG = "BookmarkLabels";
	
	// this resource returns a CheckedTextView which has setChecked(..), isChecked(), and toggle() methods
	private static final int LIST_ITEM_TYPE = R.layout.manage_labels_list_item;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		setAllowThemeChange(false);

        super.onCreate(savedInstanceState, false);
        setContentView(R.layout.manage_labels);

        bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
        
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
    	edit(R.string.new_label, newLabel);
    }
    
	protected void edit(int titleId, final LabelDto label) {
    	Log.i(TAG, "Edit label clicked");

		View view = getLayoutInflater().inflate(R.layout.bookmark_label_edit, null);
		final EditText labelName = (EditText)view.findViewById(R.id.labelName);
		labelName.setText(label.getName());

		final BookmarkStyleAdapter adp = new BookmarkStyleAdapter(this, android.R.layout.simple_spinner_item);
		final Spinner labelStyle = (Spinner)view.findViewById(R.id.labelStyle);
		labelStyle.setAdapter(adp);
		labelStyle.setSelection(adp.getBookmarkStyleOffset(label.getBookmarkStyle()));

    	AlertDialog.Builder alert = new AlertDialog.Builder(this)
										.setTitle(titleId)
										.setView(view);

    	alert.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
		    	public void onClick(DialogInterface dialog, int whichButton) {
					String name = labelName.getText().toString();
					label.setName(name);
					label.setBookmarkStyle(adp.getBookmarkStyleForOffset(labelStyle.getSelectedItemPosition()));
					bookmarkControl.saveOrUpdateLabel(label);

					loadLabelList();
				}
			    	});

    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    	  public void onClick(DialogInterface dialog, int whichButton) {
    	    // Canceled.
    	  }
    	});

		AlertDialog dialog = alert.show();
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
}