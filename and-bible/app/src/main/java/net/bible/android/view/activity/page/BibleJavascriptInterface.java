package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.touch.ShowContextMenuEvent;
import net.bible.android.control.page.window.WindowControl;
import android.util.Log;
import android.webkit.JavascriptInterface;

import de.greenrobot.event.EventBus;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleJavascriptInterface {

	private boolean notificationsEnabled = false;
	
	private VerseCalculator verseCalculator;
	
	private WindowControl windowControl = ControlFactory.getInstance().getWindowControl();
	
	private static final String TAG = "BibleJavascriptIntrfc";
	
	public BibleJavascriptInterface(VerseCalculator verseCalculator) {
		this.verseCalculator = verseCalculator;
	}
	
	@JavascriptInterface 
	public void onLoad() {
		Log.d(TAG, "onLoad from js");
	}

	@JavascriptInterface
	public void onScroll(int newYPos) {
		// do not try to change verse while the page is changing - can cause all sorts of errors e.g. selected verse may not be valid in new chapter and cause chapter jumps
		if (notificationsEnabled && !PassageChangeMediator.getInstance().isPageChanging() && !windowControl.isSeparatorMoving()) {
			verseCalculator.newPosition(newYPos);
		}
	}
	
	@JavascriptInterface
	public void clearVersePositionCache() {
		Log.d(TAG, "clear verse positions");
		verseCalculator.init();
	}

	@JavascriptInterface
	public void registerVersePosition(String verseId, int offset) {
		verseCalculator.registerVersePosition(Integer.valueOf(verseId), offset);
	}

	@JavascriptInterface
	public void verseSelected(int verse) {
		Log.d(TAG, "Verse selected event:"+verse);
		EventBus.getDefault().post(new ShowContextMenuEvent());
	}

	@JavascriptInterface
	public void log(String msg) {
		Log.d(TAG, msg);
	}

	public void setNotificationsEnabled(boolean notificationsEnabled) {
		this.notificationsEnabled = notificationsEnabled;
	}
}
