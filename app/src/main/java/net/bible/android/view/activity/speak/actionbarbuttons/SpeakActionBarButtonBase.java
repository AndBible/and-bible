package net.bible.android.view.activity.speak.actionbarbuttons;

import androidx.core.view.MenuItemCompat;

import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class SpeakActionBarButtonBase extends QuickActionButton {

	private SpeakControl speakControl;
	
	protected static final int SPEAK_START_PRIORITY = 10;

	public SpeakActionBarButtonBase(SpeakControl speakControl) {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		this.speakControl = speakControl;
	}

	/**  return true if Speak button can be shown */
	public boolean canSpeak() {
		boolean canspeakDoc = speakControl.isCurrentDocSpeakAvailable();
		return //isEnoughRoomInToolbar() && 
				canspeakDoc;
	}
	
	protected SpeakControl getSpeakControl() {
		return speakControl;
	}
}
