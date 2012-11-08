package net.bible.android.view.activity.readingplan.toolbar;

import android.view.View;
import net.bible.android.view.activity.base.toolbar.speak.SpeakToolbarButton;

public class PauseToolbarButton extends SpeakToolbarButton {

	public PauseToolbarButton(View parent) {
		super(parent);
	}

	// do not show if nothing is being said.  If speaking then allow pause and vice-versa
	@Override
	public boolean canShow() {
		return super.canShow() &&
				(getSpeakControl().isSpeaking() || getSpeakControl().isPaused());
	}
}
