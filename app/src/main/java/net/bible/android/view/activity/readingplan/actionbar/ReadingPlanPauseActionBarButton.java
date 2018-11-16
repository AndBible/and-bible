package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanPauseActionBarButton extends SpeakActionBarButton {

	@Inject
	public ReadingPlanPauseActionBarButton(SpeakControl speakControl, DocumentControl documentControl) {
		super(speakControl, documentControl);
	}

	/**
	 *  do not show if nothing is being said.  If speaking then allow pause and vice-versa
	 */
	@Override
	public boolean canShow() {
		return super.canShow() &&
				isSpeakMode();
	}
}
