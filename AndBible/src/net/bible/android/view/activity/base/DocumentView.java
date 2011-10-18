package net.bible.android.view.activity.base;

import android.view.View;

public interface DocumentView {

	public abstract void selectAndCopyText();

	public abstract void show(String html, int jumpToVerse, float jumpToYOffsetRatio);

	public abstract void applyPreferenceSettings();
	
	public abstract boolean pageDown(boolean toBottom);
	
    public float getCurrentPosition();
    
    /** same as this but of type View */
    public View asView();
    
    /** give document an opportunity to save any data entered */
    public void save();
}
