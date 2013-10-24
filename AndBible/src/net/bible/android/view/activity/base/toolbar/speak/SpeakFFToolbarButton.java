package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;

import android.view.View;
/**
 * Speak FF toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakFFToolbarButton extends SpeakToolbarButtonBase implements ToolbarButton {

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakFFToolbarButton(View parent) {
        super(parent, R.id.quickSpeakFF);
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
	protected void onButtonPress() {
		getSpeakControl().forward();
	}

	@Override
	public int getPriority() {
		return SPEAK_START_PRIORITY+3;
	}
}
