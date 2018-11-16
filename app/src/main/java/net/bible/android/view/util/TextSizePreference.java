package net.bible.android.view.util;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.util.AttributeSet;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class TextSizePreference extends SeekBarPreference {

	// do not allow text smaller than 6sp;
	// there is no android:min attribute we can put in the xml definitions so easiest way is to set it here
	private static final int MIN_TEXT_SIZE = 6;
	
	@SuppressWarnings("unused")
	private static final String TAG = "TextSizePreference";
	
	public TextSizePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setMin(MIN_TEXT_SIZE);
		setDialogMessage(CommonUtils.getResourceString(R.string.prefs_text_size_sample_text));
	}
	
	protected void updateScreenValue(int value) {
		super.updateScreenValue(value);

		getDialogMessageView().setTextSize(value);
	}
}
