package net.bible.android.view.activity.page.toolbar;

import android.support.v7.app.ActionBar;
import android.view.Menu;

public class BibleToolbarButtonManager {
	
	private HomeTitle homeTitle = new HomeTitle();
	private BibleToolbarButton bibleToolbarButton = new BibleToolbarButton();
	private CommentaryToolbarButton commentaryToolbarButton = new CommentaryToolbarButton();
	
	private static final String TAG = "BibleToolbarButtonManager";
	
	public void prepareOptionsMenu(Menu menu, ActionBar actionBar) {
		homeTitle.addToBar(actionBar);
		
		bibleToolbarButton.addToMenu(menu);
		commentaryToolbarButton.addToMenu(menu);
		
	}
	
	public void updateButtons() {
		homeTitle.update();
		
		bibleToolbarButton.update();
		commentaryToolbarButton.update();
	}
	
}
