package net.bible.android.view.activity.references;

import java.util.List;

import net.bible.service.format.Note;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * @author denha1m
 *
 */
public class NoteRefItemAdapter extends ArrayAdapter<Note> {

	private int resource;

	public NoteRefItemAdapter(Context _context, int _resource, List<Note> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Note item = getItem(position);

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
			String summary = item.getSummary();
			view.getText1().setText(summary);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String detail = item.getDetail();
			view.getText2().setText(detail);
		}

		return view;
	}
}