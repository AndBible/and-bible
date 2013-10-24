package net.bible.android.view.activity.base.toolbar.speak;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonBase;

import android.view.View;
import android.widget.ImageButton;

public abstract class SpeakToolbarButtonBase extends ToolbarButtonBase<ImageButton> implements ToolbarButton {

	private SpeakControl speakControl = ControlFactory.getInstance().getSpeakControl();
	
	protected static final int SPEAK_START_PRIORITY = 10;

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakToolbarButtonBase(View parent, int resourceId) {
        super(parent, resourceId);
	}

	/**  return true if Speak button can be shown */
	public boolean canSpeak() {
		boolean canspeakDoc = speakControl.isCurrentDocSpeakAvailable();
		return isEnoughRoomInToolbar() && canspeakDoc;
	}
	
	public boolean canShowFFRew() {
		return canSpeak() && (speakControl.isSpeaking() || speakControl.isPaused());
	}

	protected SpeakControl getSpeakControl() {
		return speakControl;
	}
}
