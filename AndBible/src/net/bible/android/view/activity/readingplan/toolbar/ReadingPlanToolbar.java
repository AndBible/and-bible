package net.bible.android.view.activity.readingplan.toolbar;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.activity.base.toolbar.CommentaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.DictionaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.Toolbar;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import android.view.View;

/** manages all the buttons on a toolbar
 * 
 * @author denha1m
 *
 */
public class ReadingPlanToolbar implements Toolbar {

	private List<ToolbarButton> mToolbarButtonList;
	
	@Override
	public void initialise(View buttonContainer) {
        mToolbarButtonList = new ArrayList<ToolbarButton>();
        mToolbarButtonList.add(new CurrentReadingPlanToolbarButton(buttonContainer));
        mToolbarButtonList.add(new CurrentDayToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowBibleToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowCommentaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowDictionaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new PauseToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakStopToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakRewToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakFFToolbarButton(buttonContainer));
//        mToolbarButtonList.add(new StrongsToolbarButton(buttonContainer));
	}

	@Override
	public void updateButtons() {
		for (ToolbarButton button : mToolbarButtonList) {
        	button.update();
        }
	}
}
