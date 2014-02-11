package net.bible.android.view.activity.speak.actionbarbuttons;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import android.view.MenuItem;

/** 
 * Stop Speaking
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakStopActionBarButton extends SpeakActionBarButtonBase {

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
