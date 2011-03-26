package net.bible.android.view.activity.navigation;

import java.util.List;

import org.apache.commons.lang.ABStringUtils;
import org.apache.commons.lang.WordUtils;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 * @author denha1m
 *
 */
public class GeneralBookKeyItemAdapter extends ArrayAdapter<Key> {

	private int resource;

	public GeneralBookKeyItemAdapter(Context _context, int _resource, List<Key> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Key item = getItem(position);

		// Pick up the TwoLineListItem defined in the xml file
		TextView view;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (TextView) inflater.inflate(resource, parent, false);
		} else {
			view = (TextView) convertView;
		}

		// Set value for the first text field
		if (view != null) {
			String key = item.getName();
			// make all uppercase in Calvin's Institutes look nicer
			if (ABStringUtils.isAllUpperCaseWherePossible(key)) {
				key = WordUtils.capitalizeFully(key);
			}
			view.setText(key);
		}

		return view;
	}
}