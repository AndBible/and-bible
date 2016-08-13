package net.bible.android.view.activity.bookmark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper;
import net.bible.service.common.CommonUtils;
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
	private ManageLabels manageLabels;

	private BookmarkStyleAdapterHelper bookmarkStyleAdapterHelper = new BookmarkStyleAdapterHelper();

	private static final int DIALOG_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.night_dialog_background);

	private static final String TAG = "LabelItemAdapter";

	public ManageLabelItemAdapter(Context context, int resource, List<LabelDto> items, ManageLabels manageLabels) {
		super(context, resource, items);
		this.resource = resource;
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
		if (labelDto.getBookmarkStyle()==null) {
			nameView.setBackgroundColor(DIALOG_BACKGROUND_NIGHT);
		} else {
			bookmarkStyleAdapterHelper.styleView(nameView, labelDto.getBookmarkStyle(), getContext(), false, false);
		}

		View editButton = rowView.findViewById(R.id.editLabel);
		editButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				manageLabels.editLabel(labelDto);
			}
		});

		View deleteButton = rowView.findViewById(R.id.deleteLabel);
		deleteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				manageLabels.delete(labelDto);
			}
		});

		return rowView;
	}
}