package net.bible.android.view.activity.mynote;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.LongPressControl;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import de.greenrobot.event.EventBus;

/**
 * Show a User Note and allow view/edit
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MyNoteEditTextView extends EditText implements DocumentView {

	private MyNote myNoteControl = ControlFactory.getInstance().getMyNoteControl();

	@SuppressWarnings("unused")
	private static final String TAG = "MyNoteEditTextView";
	
	public MyNoteEditTextView(Context context) {
		super(context);
		setSingleLine(false);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		setLayoutParams(layoutParams);
		setGravity(Gravity.TOP);
		
		applyPreferenceSettings();
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		// register for passage change events
        EventBus.getDefault().register(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		// register for passage change events
        EventBus.getDefault().unregister(this);
	}

    /** allow current page to save any settings or data before being changed
     */
    public void onEvent(BeforeCurrentPageChangeEvent event) {
		// force MyNote.save if in MyNote and suddenly change to another view 
		save();
    }
    
	private void save() {
		myNoteControl.saveMyNoteText(getText().toString());		
	}

	@Override
	public void selectAt(float x, float y) {
		Log.d(TAG, "MyNote Select at");
	}
//	@Override
//	public void selectAndCopyText(LongPressControl longPressControl) {
//	}

	@Override
	public void show(String html, int jumpToVerse, float jumpToYOffsetRatio) {
		applyPreferenceSettings();
		setText(html);
	}

	@Override
	public void applyPreferenceSettings() {
		changeBackgroundColour();

		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		int fontSize = preferences.getInt("text_size_pref", 16);
		setTextSize(TypedValue.COMPLEX_UNIT_DIP ,fontSize);
	}

	@Override
	public boolean changeBackgroundColour() {
		if (ScreenSettings.isNightMode()) {
			setBackgroundColor(Color.BLACK);
			setTextColor(Color.WHITE);
		} else {
			setBackgroundColor(Color.WHITE);
			setTextColor(Color.BLACK);
		}
		// should not return false but it is used to see if text needs refreshing, which it doesn't
		return false;
	}

	public boolean isPageNextOkay() {
		return true;
	}
	
	public boolean isPagePreviousOkay() {
		return true;
	}

	@Override
	public boolean pageDown(boolean toBottom) {
		return false;
	}

	@Override
	public float getCurrentPosition() {
		return 0;
	}

	@Override
	public View asView() {
		return this;
	}

	@Override
	public void onScreenTurnedOn() {
		// NOOP
	}
	@Override
	public void onScreenTurnedOff() {
		// NOOP
	}
}
