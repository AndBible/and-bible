package net.bible.android.view.activity.base.toolbar;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakToolbarButton;
import android.view.View;

/** manages all the buttons on a toolbar
 * 
 * @author denha1m
 *
 */
public class DefaultToolbar implements Toolbar {

	private List<ToolbarButton> mToolbarButtonList;
	
	@Override
	public void initialise(View buttonContainer) {
        mToolbarButtonList = new ArrayList<ToolbarButton>();
        mToolbarButtonList.add(new CurrentDocumentToolbarButton(buttonContainer));
        mToolbarButtonList.add(new CurrentPageToolbarButton(buttonContainer));
        mToolbarButtonList.add(new BibleToolbarButton(buttonContainer));
        mToolbarButtonList.add(new CommentaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new DictionaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakStopToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakRewToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakFFToolbarButton(buttonContainer));
        mToolbarButtonList.add(new StrongsToolbarButton(buttonContainer));
	}

	@Override
	public void updateButtons() {
		for (ToolbarButton button : mToolbarButtonList) {
        	button.update();
        }
	}
}
