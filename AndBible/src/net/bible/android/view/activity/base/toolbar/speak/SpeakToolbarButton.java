package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import android.view.View;
import android.view.View.OnClickListener;

public class SpeakToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton, OnClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakToolbarButton(View parent) {
        super(parent, R.id.quickSpeak);
	}

	@Override
	public void update() {
		// run on ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
				//hide/show speak button dependant on lang and speak support of lang && space available
		       	getButton().setVisibility(canShow() ? View.VISIBLE : View.GONE);
		       	if (getSpeakControl().isSpeaking()) {
					getButton().setImageResource(android.R.drawable.ic_media_pause);
				} else if (getSpeakControl().isPaused()) {
					getButton().setImageResource(android.R.drawable.ic_media_play);
				} else {
					getButton().setImageResource(R.drawable.ic_menu_happy_21);
				}
			}
		});
	}

	/**  return true if Speak button can be shown */
	@Override
	public boolean canShow() {
		return super.canSpeak();
	}
	
	/** button clicked */
	@Override
	public void onClick(View v) {
		getSpeakControl().speakToggleCurrentPage();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY;
	}
}
