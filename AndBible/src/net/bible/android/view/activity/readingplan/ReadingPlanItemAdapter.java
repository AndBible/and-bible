package net.bible.android.view.activity.readingplan;

import java.util.List;

import net.bible.service.readingplan.ReadingPlanInfoDto;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingPlanItemAdapter extends ArrayAdapter<ReadingPlanInfoDto> {

	private int resource;

	public ReadingPlanItemAdapter(Context _context, int _resource, List<ReadingPlanInfoDto> _items) {
		super(_context, _resource, _items);
		resource = _resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		ReadingPlanInfoDto item = getItem(position);

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
			String text = item.getCode();
			view.getText1().setText(text);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String text = item.getDescription();
			view.getText2().setText(text);
		}

		return view;
	}
}