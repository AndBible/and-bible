package net.bible.android.view.activity.page.actionbar;

import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEventListener;
import net.bible.service.device.speak.event.SpeakEventManager;
import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.Menu;

public class BibleActionBarManager extends DefaultActionBarManager implements ActionBarManager {

	private HomeTitle homeTitle = new HomeTitle();

	private BibleActionBarButton bibleActionBarButton = new BibleActionBarButton();
	private CommentaryActionBarButton commentaryActionBarButton = new CommentaryActionBarButton();
	private DictionaryActionBarButton dictionaryActionBarButton = new DictionaryActionBarButton();
	private StrongsActionBarButton strongsActionBarButton = new StrongsActionBarButton();
	
	private SpeakActionBarButton speakActionBarButton = new SpeakActionBarButton();
	private SpeakStopActionBarButton stopActionBarButton = new SpeakStopActionBarButton();
	
	public BibleActionBarManager() {
		// the manager will also instantly fire a catch-up event to ensure state is current
        SpeakEventManager.getInstance().addSpeakEventListener(new SpeakEventListener() {
			@Override
			public void speakStateChange(SpeakEvent e) {
				updateButtons();
			}
		});
	}

	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#prepareOptionsMenu(android.app.Activity, android.view.Menu, android.support.v7.app.ActionBar, net.bible.android.view.activity.page.MenuCommandHandler)
	 */
	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		super.prepareOptionsMenu(activity, menu, actionBar);
		
		homeTitle.addToBar(actionBar, activity);

		// order is important to keep bible, cmtry, ... in same place on right
		stopActionBarButton.addToMenu(menu);
		speakActionBarButton.addToMenu(menu);

		strongsActionBarButton.addToMenu(menu);
		dictionaryActionBarButton.addToMenu(menu);
		commentaryActionBarButton.addToMenu(menu);
		bibleActionBarButton.addToMenu(menu);
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#updateButtons()
	 */
	@Override
	public void updateButtons() {
		super.updateButtons();
		
		homeTitle.update();
		
		bibleActionBarButton.update();
		commentaryActionBarButton.update();
		dictionaryActionBarButton.update();
		strongsActionBarButton.update();
		
		speakActionBarButton.update();
		stopActionBarButton.update();
	}
}
