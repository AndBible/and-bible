package net.bible.android.view.activity.base;

import android.view.View;

import net.bible.android.control.page.ChapterVerse;

/**
 * Base class for boble and My Note document views
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface DocumentView {

    void show(String html, ChapterVerse chapterVerse, float jumpToYOffsetRatio);

    void applyPreferenceSettings();

    /** may need updating depending on environmental brightness
     */
    boolean changeBackgroundColour();
    
    // allow stop/start of autoscroll
    void onScreenTurnedOn();
    void onScreenTurnedOff();
    
    boolean pageDown(boolean toBottom);
    
    /** prevent swipe right if the user is scrolling the page right */
    boolean isPageNextOkay();
    
    /** prevent swipe left if the user is scrolling the page left */
    boolean isPagePreviousOkay();
    
    float getCurrentPosition();
    
    /** same as this but of type View */
    View asView();
}
