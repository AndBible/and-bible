package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;

import android.view.View;

public class SpeakRewToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton {

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakRewToolbarButton(View parent) {
        super(parent, R.id.quickSpeakRew);
	}

	@Override
	public boolean canShow() {
		return canShowFFRew();
	}

	/** button clicked */
	@Override
	protected void onButtonPress() {
		getSpeakControl().rewind();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY+2;
	}
}
