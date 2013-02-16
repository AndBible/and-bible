package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.splitscreen.SplitScreenControl;

import android.util.Log;

public class BibleJavascriptInterface {

	private boolean notificationsEnabled = false;
	
	private VerseCalculator verseCalculator;
	
	private SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
	
	private static final String TAG = "BibleJavascriptInterface";
	
	public BibleJavascriptInterface(VerseCalculator verseCalculator) {
		this.verseCalculator = verseCalculator;
	}
	
	public void onLoad() {
		Log.d(TAG, "onLoad from js");
	}

	public void onScroll(int newYPos) {
		// do not try to change verse while the page is changing - can cause all sorts of errors e.g. selected verse may not be valid in new chapter and cause chapter jumps
		if (notificationsEnabled && !PassageChangeMediator.getInstance().isPageChanging() && !splitScreenControl.isSeparatorMoving()) {
			verseCalculator.newPosition(newYPos);
		}
	}
	
	public void clearVersePositionCache() {
		Log.d(TAG, "clear verse positions");
		verseCalculator.init();
	}

	public void registerVersePosition(String verseId, int offset) {
		verseCalculator.registerVersePosition(Integer.valueOf(verseId), offset);
	}
	
	public void log(String msg) {
		Log.d(TAG, msg);
	}

	public void setNotificationsEnabled(boolean notificationsEnabled) {
		this.notificationsEnabled = notificationsEnabled;
	}
}
