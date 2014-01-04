package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.support.v7.app.ActionBar;

/** 
 * Show current verse/key and document on left of actionBar
 */
public class HomeTitle {

	private ActionBar actionBar;

	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	public void addToBar(ActionBar actionBar) {
		this.actionBar = actionBar;
		update(true);
		
		// do not display the app icon in the actionbar
		actionBar.setDisplayShowHomeEnabled(false);

		// listen for verse change events
        PassageEventManager.getInstance().addPassageEventListener(new PassageEventListener() {
			@Override
			public void pageDetailChange(PassageEvent event) {
				update(false);
			}
		});
	}
	
	public void update() {
		// update everything if called externally
		update(true);
	}
	

	private void update(final boolean everything) {
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (actionBar!=null) {
					// always update verse number
					actionBar.setTitle(getTitle());
					// don't always need to redisplay document name
					if (everything) {
						actionBar.setSubtitle(getSubtitle());
					}
				}
			}
		});
	}

	private String getTitle() {
		return pageControl.getCurrentPageTitle();
	}
	private String getSubtitle() {
		return pageControl.getCurrentDocumentTitle();
	}
}
