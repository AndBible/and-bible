package net.bible.android.view.activity.speak.actionbarbuttons;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import android.view.MenuItem;

/** 
 * Toggle Strongs numbers on/off
 */
public class SpeakActionBarButton extends SpeakActionBarButtonBase {

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		getSpeakControl().speakToggleCurrentPage();
		
		update(menuItem);
		return true;
	}

	@Override
	protected String getTitle() {
		return CommonUtils.getResourceString(R.string.speak);
	}
	
	@Override
	protected int getIcon() {
       	if (getSpeakControl().isSpeaking()) {
			return android.R.drawable.ic_media_pause;
		} else if (getSpeakControl().isPaused()) {
			return android.R.drawable.ic_media_play;
		} else {
			return R.drawable.ic_action_volume_on;
		}
	}

	@Override
	protected boolean canShow() {
		return super.canSpeak();
	}
}
