package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import android.view.View;
import android.view.View.OnClickListener;

public class SpeakRewToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton, OnClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakRewToolbarButton(View parent) {
        super(parent, R.id.quickSpeakRew);
	}

	@Override
	public void update() {
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
		return canShowFFRew();
	}

	/** button clicked */
	@Override
	public void onClick(View v) {
		getSpeakControl().rewind();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY+2;
	}
}
