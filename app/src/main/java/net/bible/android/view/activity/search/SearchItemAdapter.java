package net.bible.android.view.activity.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

import net.bible.android.control.search.SearchControl;

import org.crosswire.jsword.passage.Key;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SearchItemAdapter extends ArrayAdapter<Key> {

	private int resource;
	private SearchControl searchControl;

	public SearchItemAdapter(Context _context, int _resource, List<Key> _items, SearchControl searchControl) {
		super(_context, _resource, _items);
		this.resource = _resource;
		this.searchControl = searchControl;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		Key item = getItem(position);

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
			String key = item.getName();
			view.getText1().setText(key);
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String verseText = searchControl.getSearchResultVerseText(item);
			view.getText2().setText(verseText);
		}

		return view;
	}
}