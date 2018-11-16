package net.bible.android.view.activity.speak.actionbarbuttons;

import android.util.Log;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Start speaking current page
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SpeakActionBarButton extends SpeakActionBarButtonBase {

	private final DocumentControl documentControl;

	private static final String TAG = "SpeakActionBarButtonBas";

	@Inject
	public SpeakActionBarButton(SpeakControl speakControl, DocumentControl documentControl) {
		super(speakControl);
		this.documentControl = documentControl;
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		try {
			getSpeakControl().toggleSpeak();
			
			update(menuItem);
		} catch (Exception e) {
			Log.e(TAG, "Error toggling speech", e);
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
		}
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
			return R.drawable.ic_hearing_24dp;
		}
	}

	@Override
	protected boolean canShow() {
		// show if speakable or already speaking (to pause), and only if plenty of room
		return (super.canSpeak() || isSpeakMode()) &&
				(isWide() || !documentControl.isStrongsInBook() || isSpeakMode());
	}
}
