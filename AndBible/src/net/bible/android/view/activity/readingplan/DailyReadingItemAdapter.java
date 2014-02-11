package net.bible.android.view.activity.readingplan;

import java.util.List;

import net.bible.service.readingplan.OneDaysReadingsDto;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DailyReadingItemAdapter extends ArrayAdapter<OneDaysReadingsDto> {

	private int resource;

	public DailyReadingItemAdapter(Context _context, int _resource, List<OneDaysReadingsDto> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		OneDaysReadingsDto item = getItem(position);

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
			String line1 = item.getDayDesc();
			view.getText1().setText(line1);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String line2 = item.getReadingsDesc();
			view.getText2().setText(line2);
		}
		
		return view;
	}
}