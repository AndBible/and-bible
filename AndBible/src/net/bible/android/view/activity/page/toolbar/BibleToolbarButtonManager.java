package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;

public class BibleToolbarButtonManager {
	
	private CommentaryToolbarButton commentaryToolbarButton = new CommentaryToolbarButton();
	
	private ActionBar actionBar;
	
	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	private static final String TAG = "BibleToolbarButtonManager";
	
	public void prepareOptionsMenu(Menu menu, ActionBar actionBar) {
		this.actionBar = actionBar;
		
		commentaryToolbarButton.addToMenu(menu);
		
        // listen for verse change events
        PassageEventManager.getInstance().addPassageEventListener(new PassageEventListener() {
			@Override
			public void pageDetailChange(PassageEvent event) {
				onMinorUpdate();;
			}
		});

	}
	
	public void updateButtons() {
		commentaryToolbarButton.update();
		onMinorUpdate();
	}
	
	private void onMinorUpdate() {
		if (actionBar!=null) {
			CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					actionBar.setTitle(getTitle());
				}
			});
		}
	}

	private String getTitle() {
		return pageControl.getCurrentDocumentTitle()+" "+pageControl.getCurrentPageTitle();
	}
}
