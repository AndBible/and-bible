package net.bible.android.view;

public class BibleJavascriptInterface {

	private VerseCalculator verseCalculator;
	
	public BibleJavascriptInterface(VerseCalculator verseCalculator) {
		this.verseCalculator = verseCalculator;
	}
	
	public void onLoad(int scrollHeight) {
		System.out.println("*onLoad from js - scrollht="+scrollHeight+" ******");
		verseCalculator.init(scrollHeight);
	}

	public void onScroll(int newYPos) {
		System.out.println("*onScroll from js - y="+newYPos+" ******");
		verseCalculator.newPosition(newYPos);
	}
	
	public void registerVersePosition(int verse, int offset) {
		System.out.println("******** Verse:"+verse+" offset:"+offset);
	}
	
	public void log(String msg) {
		System.out.println("***"+msg);
	}
}
