package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.service.db.bookmark.LabelDto;

import java.util.List;

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ManageLabelItemAdapter extends ArrayAdapter<LabelDto> {

	private int resource;
	private Bookmark bookmarkControl;
	private ManageLabels manageLabels;

	private static final String TAG = "LabelItemAdapter";

	public ManageLabelItemAdapter(Context _context, int resource, List<LabelDto> items, ManageLabels manageLabels) {
		super(_context, resource, items);
		this.resource = resource;
		this.bookmarkControl = ControlFactory.getInstance().getBookmarkControl();
		this.manageLabels = manageLabels;
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
		TextView nameView = (TextView) rowView.findViewById(R.id.labelName);
		nameView.setText(labelDto.getName());

		ImageButton editButton = (ImageButton) rowView.findViewById(R.id.editLabel);
		editButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				manageLabels.edit(R.string.edit, labelDto);
			}
		});

		return rowView;
	}
}