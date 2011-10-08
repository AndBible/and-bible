package net.bible.android.view.activity.usernote;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.usernote.UserNote;
import net.bible.service.db.usernote.UserNoteDto;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

/**
 * Display a single Note in a list row
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class UserNoteItemAdapter extends ArrayAdapter<UserNoteDto> {
	private int resource;
	private UserNote usernoteControl;
	
	private static final String TAG = "UserNoteItemAdapter";

	public UserNoteItemAdapter(Context _context, int _resource, List<UserNoteDto> _items) {
		super(_context, _resource, _items);
		resource = _resource;
		usernoteControl = ControlFactory.getInstance().getUserNoteControl();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		UserNoteDto item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TwoLineListItem view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TwoLineListItem) inflater.inflate(resource, parent, false);
		} else {
			view = (TwoLineListItem) convertView;
		}

		// Set value for the first text field
		if (view.getText1() != null) {
			String key = item.getKey().getName();
			view.getText1().setText(key);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			try {
				String noteText = usernoteControl.getUserNoteText(item, true);
				view.getText2().setText(noteText);
			} catch (Exception e) {
				Log.e(TAG, "Error loading label verse text", e);
				view.getText2().setText("");
			}
		}

		return view;
	}
}
