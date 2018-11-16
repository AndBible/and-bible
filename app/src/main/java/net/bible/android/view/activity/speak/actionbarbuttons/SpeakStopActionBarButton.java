package net.bible.android.view.activity.speak.actionbarbuttons;

import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.speak.SpeakControl;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Stop Speaking
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SpeakStopActionBarButton extends SpeakActionBarButtonBase {

	@Inject
	public SpeakStopActionBarButton(SpeakControl speakControl) {
		super(speakControl);
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		getSpeakControl().stop();

		return true;
	}

	@Override
	protected String getTitle() {
		return CommonUtils.getResourceString(R.string.stop);
	}
	
	@Override
	protected int getIcon() {
		return R.drawable.ic_media_stop;
	}

	@Override
	protected boolean canShow() {
		return isSpeakMode();
	}
}
