package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class SpeakStopToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton, OnClickListener {

	private static final String TAG = "Speak";

	public SpeakStopToolbarButton(View parent) {
		super(parent, R.id.quickSpeakStop);
	}

	@Override
	public void update() {
		Log.d(TAG, "Update Speak button");
		// run on ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
				//hide/show speak button dependant on lang and speak support of lang && space available
		       	getButton().setVisibility(canShow() ? View.VISIBLE : View.GONE);
			}
		});
	}

	@Override
	public boolean canShow() {
		return canSpeak() && (getSpeakControl().isSpeaking() || getSpeakControl().isPaused());
	}

	/** button clicked */
	@Override
	public void onClick(View v) {
		getSpeakControl().stop();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY+1;
	}
}
