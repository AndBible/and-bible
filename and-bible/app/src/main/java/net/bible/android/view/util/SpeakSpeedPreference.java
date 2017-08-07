package net.bible.android.view.util;

import android.content.Context;
import android.util.AttributeSet;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;

/**
 * Allow adjustment of Speak speed.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakSpeedPreference extends SeekBarPreference {

	// do not allow text smaller than 6sp;
	// there is no android:min attribute we can put in the xml definitions so easiest way is to set it here
	private static final int MIN_PERCENTAGE_CHANGE = 50;

	@SuppressWarnings("unused")
	private static final String TAG = "SpeakSpeedPreference";

	public SpeakSpeedPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setMin(MIN_PERCENTAGE_CHANGE);
	}
	
    protected void updateScreenValue(int value) {
    	super.updateScreenValue(value);

		int message;
		if (value<98) {
			message = R.string.speak_speed_slow;
		} else if (value>102) {
			message = R.string.speak_speed_fast;
		} else {
			message = R.string.speak_speed_normal;
		}
		getDialogMessageView().setText(message);
    }
}