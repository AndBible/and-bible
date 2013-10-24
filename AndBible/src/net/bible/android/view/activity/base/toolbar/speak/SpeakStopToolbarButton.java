package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;

import android.view.View;

public class SpeakStopToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton {

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakStopToolbarButton(View parent) {
		super(parent, R.id.quickSpeakStop);
	}

	@Override
	public boolean canShow() {
		// this button is unlike the other buttons - if speaking then always show Stop even if there is no room!
		return getSpeakControl().isSpeaking() || getSpeakControl().isPaused();
	}

	/** button clicked */
	@Override
	protected void onButtonPress() {
		getSpeakControl().stop();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY+1;
	}
}
