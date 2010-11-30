package net.bible.android.view.util;

import android.content.Context;
import android.util.AttributeSet;

public class TextSizePreference extends SeekBarPreference {

	// do not allow text smaller than 6sp;
	// there is no android:min attribute we can put in the xml definitions so easiest way is to set it here
	private static final int MIN_TEXT_SIZE = 6;
	
	private static final String SAMPLE_TEXT = "Sample text";

	private static final String TAG = "TextSizePreference";
	
	public TextSizePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setMin(MIN_TEXT_SIZE);
		setDialogMessage(SAMPLE_TEXT);
	}
	
    protected void updateScreenValue(int value) {
    	super.updateScreenValue(value);

		getDialogMessageView().setTextSize(value);
    }
}
