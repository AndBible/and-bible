package net.bible.android.view.activity.base.toolbar;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakToolbarButton;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEventListener;
import net.bible.service.device.speak.event.SpeakEventManager;

import android.view.View;

/** manages all the buttons on a toolbar
 * 
 * @author denha1m
 *
 */
public class DefaultToolbar implements Toolbar {
	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	
	private List<ToolbarButton> mToolbarButtonList;
	
	private static final int MANDATORY_BUTTON_NUM = 2;
	private static final int MANDATORY_BUTTON_NUM_WITH_SPLIT_PASSAGE_SELECTOR_BUTTONS = 4;
	
	@SuppressWarnings("unused")
	private static final String TAG = "Toolbar";
	
	@Override
	public void initialise(View buttonContainer) {
		// buttons should be added in priority order
        mToolbarButtonList = new ArrayList<ToolbarButton>();
        mToolbarButtonList.add(new CurrentDocumentToolbarButton(buttonContainer));
        
        mToolbarButtonList.add(new BibleBookToolbarButton(buttonContainer));
        mToolbarButtonList.add(new BibleChapterToolbarButton(buttonContainer));
        mToolbarButtonList.add(new BibleVerseToolbarButton(buttonContainer));
        
        mToolbarButtonList.add(new CurrentPageToolbarButton(buttonContainer));
        
        // quich change buttons
        mToolbarButtonList.add(new BibleToolbarButton(buttonContainer));
        mToolbarButtonList.add(new CommentaryToolbarButton(buttonContainer));
        mToolbarButtonList.add(new StrongsToolbarButton(buttonContainer));
//		mToolbarButtonList.add(new VerseMenuToolbarButton(buttonContainer));
//        mToolbarButtonList.add(new SplitScreenToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakStopToolbarButton(buttonContainer));  // Stop is always shown if speaking or paused regardless of priority
        mToolbarButtonList.add(new SpeakRewToolbarButton(buttonContainer));
        mToolbarButtonList.add(new SpeakFFToolbarButton(buttonContainer));
        mToolbarButtonList.add(new DictionaryToolbarButton(buttonContainer));
        
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
		int maxNumButtonsToShow = numQuickButtons+getMandatoryButtonCount();
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
	
	private int getMandatoryButtonCount() {
		return documentControl.showSplitPassageSelectorButtons()? MANDATORY_BUTTON_NUM_WITH_SPLIT_PASSAGE_SELECTOR_BUTTONS : MANDATORY_BUTTON_NUM;  
	}
}
