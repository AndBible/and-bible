package net.bible.android.view.activity.page;

import android.util.Log;

public class BibleJavascriptInterface {

	private VerseCalculator verseCalculator;
	
	private static final String TAG = "BibleJavascriptInterface";
	
	public BibleJavascriptInterface(VerseCalculator verseCalculator) {
		this.verseCalculator = verseCalculator;
	}
	
	public void onLoad() {
		Log.d(TAG, "onLoad from js");
		verseCalculator.init();
	}

	public void onScroll(int newYPos) {
		verseCalculator.newPosition(newYPos);
	}
	
	public void registerVersePosition(int verse, int offset) {
		verseCalculator.registerVersePosition(verse, offset);
	}
	
	public void log(String msg) {
		Log.d(TAG, msg);
	}
}
