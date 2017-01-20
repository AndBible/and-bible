package net.bible.android.view.activity.readingplan.actionbar;

import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.Menu;

import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEventListener;
import net.bible.service.device.speak.event.SpeakEventManager;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanActionBarManager extends DefaultActionBarManager implements ActionBarManager {

	private final ReadingPlanTitle readingPlanTitle;
	private BibleActionBarButton bibleActionBarButton = new BibleActionBarButton();
	private CommentaryActionBarButton commentaryActionBarButton = new CommentaryActionBarButton();
	private DictionaryActionBarButton dictionaryActionBarButton = new DictionaryActionBarButton();

	private final PauseActionBarButton pauseActionBarButton;
	private final SpeakStopActionBarButton speakStopActionBarButton;

	@Inject
	public ReadingPlanActionBarManager(ReadingPlanTitle readingPlanTitle, PauseActionBarButton pauseActionBarButton, SpeakStopActionBarButton speakStopActionBarButton) {
		this.readingPlanTitle = readingPlanTitle;
		this.pauseActionBarButton = pauseActionBarButton;
		this.speakStopActionBarButton = speakStopActionBarButton;

		// the manager will also instantly fire a catch-up event to ensure state is current
        SpeakEventManager.getInstance().addSpeakEventListener(new SpeakEventListener() {
			@Override
			public void speakStateChange(SpeakEvent e) {
				updateButtons();
			}
		});
    }

	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		super.prepareOptionsMenu(activity, menu, actionBar);
		
		readingPlanTitle.addToBar(actionBar, activity);

		// order is important to keep bible, cmtry, ... in same place on right
		speakStopActionBarButton.addToMenu(menu);
		pauseActionBarButton.addToMenu(menu);

		dictionaryActionBarButton.addToMenu(menu);
		commentaryActionBarButton.addToMenu(menu);
		bibleActionBarButton.addToMenu(menu);
	}
	
	public void updateButtons() {
		super.updateButtons();
		
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				readingPlanTitle.update();
				
				bibleActionBarButton.update();
				commentaryActionBarButton.update();
				dictionaryActionBarButton.update();
				
				pauseActionBarButton.update();
				speakStopActionBarButton.update();
			}
		});
	}
}
