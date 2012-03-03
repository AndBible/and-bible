package net.bible.android.view.activity.base;

import android.view.View;

public interface DocumentView {

	public abstract void selectAndCopyText();

	public abstract void show(String html, int jumpToVerse, float jumpToYOffsetRatio);

	public abstract void applyPreferenceSettings();

	/** may need updating depending on environmental brightness
	 */
	public abstract boolean changeBackgroundColour();
	
	public abstract boolean pageDown(boolean toBottom);
	
	/** prevent swipe right if the user is scrolling the page right */
	public boolean isPageRightOkay();
	
	/** prevent swipe left if the user is scrolling the page left */
	public boolean isPageLeftOkay();
	
    public float getCurrentPosition();
    
    /** same as this but of type View */
    public View asView();
    
    /** give document an opportunity to save any data entered */
    public void save();
}
