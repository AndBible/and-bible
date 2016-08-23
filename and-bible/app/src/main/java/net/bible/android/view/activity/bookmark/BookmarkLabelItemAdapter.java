package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import net.bible.android.view.util.UiUtils;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.service.db.bookmark.LabelDto;

import java.util.List;

/**
 * Adapter which shows highlight colour of labels
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkLabelItemAdapter extends ArrayAdapter<LabelDto> {

	private int resource;

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();

	private static final String TAG = "BookmarkLabelItemAdapter";

	public BookmarkLabelItemAdapter(Context context, int resource, List<LabelDto> items) {
		super(context, resource, items);
		this.resource = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final LabelDto labelDto = getItem(position);

		View rowView;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(resource, parent, false);
		} else {
			rowView = convertView;
		}
		CheckedTextView nameView = (CheckedTextView) rowView;
		nameView.setText(labelDto.getName());
		if (labelDto.getBookmarkStyle()==null) {
			nameView.setBackgroundColor(UiUtils.getThemeBackgroundColour(getContext()));
		} else {
			bookmarkStyleAdapterHelper.styleView(nameView, labelDto.getBookmarkStyle(), getContext(), false, false);
		}

		return rowView;
	}
}