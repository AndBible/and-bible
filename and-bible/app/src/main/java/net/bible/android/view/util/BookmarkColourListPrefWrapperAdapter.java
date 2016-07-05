package net.bible.android.view.util;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.widget.ListPrefWrapperAdapter;
import net.bible.service.common.CommonUtils;

/**
 * Set each list view item to represent background colour od icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
class BookmarkColourListPrefWrapperAdapter extends ListPrefWrapperAdapter {

	private Context context;

	private String sampleText = CommonUtils.getResourceString(R.string.prefs_text_size_sample_text);

    public BookmarkColourListPrefWrapperAdapter(Context context, ListAdapter origAdapter) {
		super(origAdapter);
		this.context = context;
    }

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = (TextView)super.getView(position, convertView, parent);
		int backgroundColor = Color.WHITE;

		switch (BookmarkStyle.values()[position]) {
			case YELLOW_STAR:
				backgroundColor = BookmarkStyle.YELLOW_STAR.getBackgroundColor();
				CharSequence imgText = addImageAtStart("* "+sampleText, R.drawable.goldstar16x16);
				view.setText(imgText, TextView.BufferType.SPANNABLE);
				break;
			case RED_HIGHLIGHT:
				backgroundColor = BookmarkStyle.RED_HIGHLIGHT.getBackgroundColor();
				view.setText(sampleText);
				break;
			case YELLOW_HIGHLIGHT:
				backgroundColor = BookmarkStyle.YELLOW_HIGHLIGHT.getBackgroundColor();
				view.setText(sampleText);
				break;
			case GREEN_HIGHLIGHT:
				backgroundColor = BookmarkStyle.GREEN_HIGHLIGHT.getBackgroundColor();
				view.setText(sampleText);
				break;
			case BLUE_HIGHLIGHT:
				backgroundColor = BookmarkStyle.BLUE_HIGHLIGHT.getBackgroundColor();
				view.setText(sampleText);
				break;
		}
		view.setBackgroundColor(backgroundColor);

		view.setTextColor(Color.BLACK);
		view.setGravity(Gravity.CENTER);
		view.setHeight(CommonUtils.convertDipsToPx(30));
		return view;
	}

	/**
	 * Replace first character of text with image
	 */
	private CharSequence addImageAtStart(String text, int drawableImage) {
		ImageSpan imageSpan = new ImageSpan(context, drawableImage, ImageSpan.ALIGN_BASELINE);
		final SpannableString spannableString = new SpannableString(text);
		spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
		return spannableString;
	}
}