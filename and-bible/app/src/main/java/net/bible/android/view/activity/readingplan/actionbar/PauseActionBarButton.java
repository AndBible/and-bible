package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PauseActionBarButton extends SpeakActionBarButton {

	/**
	 *  do not show if nothing is being said.  If speaking then allow pause and vice-versa
	 */
	@Override
	public boolean canShow() {
		return super.canShow() &&
				isSpeakMode();
	}
}
