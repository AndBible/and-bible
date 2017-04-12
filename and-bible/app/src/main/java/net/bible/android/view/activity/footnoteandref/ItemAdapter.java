package net.bible.android.view.activity.footnoteandref;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TwoLineListItem;

import net.bible.android.control.footnoteandref.NoteDetailCreator;
import net.bible.service.format.Note;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ItemAdapter extends ArrayAdapter<Note> {

	private int resource;
	private final NoteDetailCreator noteDetailCreator;

	public ItemAdapter(Context _context, int _resource, List<Note> _items, NoteDetailCreator noteDetailCreator) {
		super(_context, _resource, _items);
		resource = _resource;
		this.noteDetailCreator = noteDetailCreator;
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
			view.getText1().setText(Html.fromHtml(summary));
		}

		// set value for the second text field
		if (view.getText2() != null) {
			String detail = noteDetailCreator.getDetail(item);
			view.getText2().setText(Html.fromHtml(detail));
		}

		return view;
	}
}