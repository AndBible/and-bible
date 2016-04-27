package net.bible.android.view.activity.page;

import android.util.Log;
import android.webkit.JavascriptInterface;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.window.WindowControl;

/**
 * Interface allowing javascript to call java methods in app
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleJavascriptInterface {

	private boolean notificationsEnabled = false;
	
	private VerseCalculator verseCalculator;

	private VerseActionModeMediator verseActionModeMediator;
	
	private WindowControl windowControl = ControlFactory.getInstance().getWindowControl();
	
	private static final String TAG = "BibleJavascriptIntrfc";
	
	public BibleJavascriptInterface(VerseActionModeMediator verseActionModeMediator) {
		this.verseActionModeMediator = verseActionModeMediator;
		ControlFactory.getInstance().inject(this);
	}

	public void setVerseCalculator(VerseCalculator verseCalculator) {
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
	public void verseLongPress(int verse) {
		Log.d(TAG, "Verse selected event:"+verse);
		verseActionModeMediator.verseLongPress(verse);
	}

	@JavascriptInterface
	public void verseTouch(int verse) {
		Log.d(TAG, "Verse touched event:"+verse);
		verseActionModeMediator.verseTouch(verse);
	}

	@JavascriptInterface
	public void log(String msg) {
		Log.d(TAG, msg);
	}

	public void setNotificationsEnabled(boolean notificationsEnabled) {
		this.notificationsEnabled = notificationsEnabled;
	}
}
