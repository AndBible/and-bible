package net.bible.android.view.activity.base;

import net.bible.android.view.activity.page.LongPressControl;

import android.view.View;

/**
 * Base class for boble and My Note document views
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface DocumentView {

	public abstract void selectAt(float x, float y);

	public abstract void show(String html, int jumpToVerse, float jumpToYOffsetRatio);

	public abstract void applyPreferenceSettings();

	/** may need updating depending on environmental brightness
	 */
	public abstract boolean changeBackgroundColour();
	
	// allow stop/start of autoscroll
	public abstract void onScreenTurnedOn();
	public abstract void onScreenTurnedOff();
	
	public abstract boolean pageDown(boolean toBottom);
	
	/** prevent swipe right if the user is scrolling the page right */
	public boolean isPageNextOkay();
	
	/** prevent swipe left if the user is scrolling the page left */
	public boolean isPagePreviousOkay();
	
    public float getCurrentPosition();
    
    /** same as this but of type View */
    public View asView();
}
