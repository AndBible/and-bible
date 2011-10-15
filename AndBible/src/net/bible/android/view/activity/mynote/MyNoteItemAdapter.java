package net.bible.android.view.activity.mynote;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.mynote.MyNote;
import net.bible.service.db.mynote.MyNoteDto;
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
public class MyNoteItemAdapter extends ArrayAdapter<MyNoteDto> {
	private int resource;
	private MyNote usernoteControl;
	
	private static final String TAG = "UserNoteItemAdapter";

	public MyNoteItemAdapter(Context _context, int _resource, List<MyNoteDto> _items) {
		super(_context, _resource, _items);
		resource = _resource;
		usernoteControl = ControlFactory.getInstance().getMyNoteControl();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		MyNoteDto item = getItem(position);

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
				String noteText = usernoteControl.getMyNoteText(item, true);
				view.getText2().setText(noteText);
			} catch (Exception e) {
				Log.e(TAG, "Error loading label verse text", e);
				view.getText2().setText("");
			}
		}

		return view;
	}
}
