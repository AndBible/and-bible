package net.bible.android.view.activity.readingplan.toolbar;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.activity.base.toolbar.Toolbar;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEventListener;
import net.bible.service.device.speak.event.SpeakEventManager;
import android.view.View;

/** manages all the buttons on a toolbar
 * 
 * @author denha1m
 *
 */
public class ReadingPlanToolbar implements Toolbar {

	private List<ToolbarButton> mToolbarButtonList;
	
	private static final int MANDATORY_BUTTON_NUM = 2;

	@Override
	public void initialise(View buttonContainer) {
        mToolbarButtonList = new ArrayList<ToolbarButton>();
        mToolbarButtonList.add(new CurrentReadingPlanToolbarButton(buttonContainer));
        mToolbarButtonList.add(new CurrentDayToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowBibleToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowCommentaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new PauseToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakStopToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakRewToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakFFToolbarButton(buttonContainer));
        mToolbarButtonList.add(new ShowDictionaryToolbarButton(buttonContainer));
//        mToolbarButtonList.add(new StrongsToolbarButton(buttonContainer));
        
		// the manager will also instantly fire a catch-up event to ensure state is current
        SpeakEventManager.getInstance().addSpeakEventListener(new SpeakEventListener() {
			@Override
			public void speakStateChange(SpeakEvent e) {
				updateButtons();
			}
		});
	}

	@Override
	public void updateButtons() {
		int numQuickButtons = ToolbarButtonHelper.numQuickButtonsToShow();
		int maxNumButtonsToShow = numQuickButtons+MANDATORY_BUTTON_NUM;
		int numButtonsShown = 0;
		for (ToolbarButton button : mToolbarButtonList) {
			button.setEnoughRoomInToolbar(numButtonsShown<maxNumButtonsToShow);
			button.setNarrow(numQuickButtons<=3);
			button.update();
			if (button.canShow()) {
				numButtonsShown++;
			}
        }
	}
}
