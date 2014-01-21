package net.bible.android.view.activity.speak.actionbarbuttons;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;
import android.support.v4.view.MenuItemCompat;

public abstract class SpeakActionBarButtonBase extends QuickActionButton {

	private SpeakControl speakControl = ControlFactory.getInstance().getSpeakControl();
	
	protected static final int SPEAK_START_PRIORITY = 10;

	@SuppressWarnings("unused")
	private static final String TAG = "Speak";

	public SpeakActionBarButtonBase() {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	}

	/**  return true if Speak button can be shown */
	public boolean canSpeak() {
		boolean canspeakDoc = speakControl.isCurrentDocSpeakAvailable();
		return //isEnoughRoomInToolbar() && 
				canspeakDoc;
	}
	
	public boolean canShowFFRew() {
		return canSpeak() && (speakControl.isSpeaking() || speakControl.isPaused());
	}

	protected SpeakControl getSpeakControl() {
		return speakControl;
	}
}
