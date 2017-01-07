package net.bible.android.view.util.widget;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.android.view.util.UiUtils;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;

/**
 * Set each list view item to represent background colour of icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class BookmarkStyleAdapterHelper {

	private String sampleText = CommonUtils.getResourceString(R.string.prefs_text_size_sample_text);

	public void styleView(TextView view, BookmarkStyle bookmarkStyle, Context context, boolean overrideText, boolean centreText) {

		// prepare text to be shown
		String baseText;
		if (overrideText) {
			baseText = sampleText;
		} else {
			baseText = view.getText().toString();
			// avoid multiple *'s
			if (baseText.startsWith("*")) {
				StringUtils.strip(baseText, "*");
			}
		}

		int backgroundColor = Color.WHITE;
		switch (bookmarkStyle) {
			case YELLOW_STAR:
				backgroundColor = UiUtils.getThemeBackgroundColour(context);
				view.setTextColor(UiUtils.getThemeTextColour(context));
				CharSequence imgText = addImageAtStart("* "+baseText, R.drawable.goldstar16x16, context);
				view.setText(imgText, TextView.BufferType.SPANNABLE);
				break;
			case RED_HIGHLIGHT:
				backgroundColor = BookmarkStyle.RED_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case YELLOW_HIGHLIGHT:
				backgroundColor = BookmarkStyle.YELLOW_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case GREEN_HIGHLIGHT:
				backgroundColor = BookmarkStyle.GREEN_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
			case BLUE_HIGHLIGHT:
				backgroundColor = BookmarkStyle.BLUE_HIGHLIGHT.getBackgroundColor();
				view.setText(baseText);
				break;
		}
		view.setBackgroundColor(backgroundColor);
		view.setHeight(CommonUtils.convertDipsToPx(30));
		if (centreText) {
			view.setGravity(Gravity.CENTER);
		}
	}

	/**
	 * Replace first character of text with image
	 */
	private CharSequence addImageAtStart(String text, int drawableImage, Context context) {
		ImageSpan imageSpan = new ImageSpan(context, drawableImage, ImageSpan.ALIGN_BASELINE);
		final SpannableString spannableString = new SpannableString(text);
		spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
		return spannableString;
	}
}