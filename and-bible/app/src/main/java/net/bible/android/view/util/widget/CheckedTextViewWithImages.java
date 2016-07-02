package net.bible.android.view.util.widget;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.widget.CheckedTextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Show images in a TextView.
 *
 * Example:
 * view = new TextViewWithImages(context);
 * view.setText("[img src=goldstar16x16/]"+sampleText);
 * OR
 * <com.xyz.customandroid.TextViewWithImages
 *   android:layout_width="wrap_content"
 *   android:layout_height="wrap_content"
 *   android:textColor="#FFFFFF00"
 *   android:text="@string/can_try_again"
 *   android:textSize="12dip"
 *   style=...
 * />
 * <string name="can_try_again">Press [img src=ok16/] to accept or [img src=retry16/] to retry</string>
 *
 * See http://stackoverflow.com/questions/15352496/how-to-add-image-in-a-textview-text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CheckedTextViewWithImages extends CheckedTextView {

	public CheckedTextViewWithImages(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public CheckedTextViewWithImages(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CheckedTextViewWithImages(Context context) {
		super(context);
	}

	@Override
	public void setText(CharSequence text, BufferType type) {
		Spannable s = getTextWithImages(getContext(), text);
		super.setText(s, BufferType.SPANNABLE);

		// need to do the following to show the check mark
		setCheckMarkDrawable(android.support.design.R.drawable.abc_btn_radio_material);
	}

	private static final Spannable.Factory spannableFactory = Spannable.Factory.getInstance();

	private static boolean addImages(Context context, Spannable spannable) {
		Pattern refImg = Pattern.compile("\\Q[img src=\\E([a-zA-Z0-9_]+?)\\Q/]\\E");
		boolean hasChanges = false;

		Matcher matcher = refImg.matcher(spannable);
		while (matcher.find()) {
			boolean set = true;
			for (ImageSpan span : spannable.getSpans(matcher.start(), matcher.end(), ImageSpan.class)) {
				if (spannable.getSpanStart(span) >= matcher.start()
						&& spannable.getSpanEnd(span) <= matcher.end()
						) {
					spannable.removeSpan(span);
				} else {
					set = false;
					break;
				}
			}
			String resname = spannable.subSequence(matcher.start(1), matcher.end(1)).toString().trim();
			int id = context.getResources().getIdentifier(resname, "drawable", context.getPackageName());
			if (set) {
				hasChanges = true;
				spannable.setSpan(new ImageSpan(context, id),
						matcher.start(),
						matcher.end(),
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				);
			}
		}

		return hasChanges;
	}

	private static Spannable getTextWithImages(Context context, CharSequence text) {
		Spannable spannable = spannableFactory.newSpannable(text);
		addImages(context, spannable);
		return spannable;
	}
}