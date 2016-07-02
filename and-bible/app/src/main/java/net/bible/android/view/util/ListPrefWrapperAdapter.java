package net.bible.android.view.util;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

import net.bible.android.activity.R;
import net.bible.android.view.util.widget.CheckedTextViewWithImages;
import net.bible.service.common.CommonUtils;

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
class ListPrefWrapperAdapter implements WrapperListAdapter {
	private Context context;
	private ListAdapter mOrigAdapter;

	private String sampleText = CommonUtils.getResourceString(R.string.prefs_text_size_sample_text);

    public ListPrefWrapperAdapter(Context context, ListAdapter origAdapter) {
		this.context = context;
		mOrigAdapter = origAdapter;
    }

    @Override
    public ListAdapter getWrappedAdapter() {
        return mOrigAdapter;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return getWrappedAdapter().areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return getWrappedAdapter().isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        getWrappedAdapter().registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        getWrappedAdapter().unregisterDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return getWrappedAdapter().getCount();
    }

    @Override
    public Object getItem(int position) {
        return getWrappedAdapter().getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return getWrappedAdapter().getItemId(position);
    }

    @Override
    public boolean hasStableIds() {
        return getWrappedAdapter().hasStableIds();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = new CheckedTextViewWithImages(context);
		String text = sampleText;
		int backgroundColor = Color.WHITE;

        switch (position) {
            case 0:
                backgroundColor = Color.WHITE;
				text = "[img src=goldstar16x16/]"+text;
				break;
            case 1:
                backgroundColor = Color.RED;
				break;
            case 2:
				backgroundColor = Color.YELLOW;
				break;
            case 3:
				backgroundColor = Color.GREEN;
				break;
            case 4:
				backgroundColor = Color.BLUE;
				break;
        }
		view.setBackgroundColor(backgroundColor);
		view.setText(text);

		view.setTextColor(Color.BLACK);
		view.setGravity(Gravity.CENTER);
		view.setHeight(CommonUtils.convertDipsToPx(30));
        return view;
    }

    @Override
    public int getItemViewType(int position) {
        return getWrappedAdapter().getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return getWrappedAdapter().getViewTypeCount();
    }

    @Override
    public boolean isEmpty() {
        return getWrappedAdapter().isEmpty();
    }
}