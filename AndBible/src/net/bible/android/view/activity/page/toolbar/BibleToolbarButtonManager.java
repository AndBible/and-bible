package net.bible.android.view.activity.page.toolbar;

import android.view.Menu;

public class BibleToolbarButtonManager {
	
	private CommentaryToolbarButton commentaryToolbarButton = new CommentaryToolbarButton();
	
	public void prepareOptionsMenu(Menu menu) {
		commentaryToolbarButton.addToMenu(menu);
	}
	
	public void updateButtons() {
		commentaryToolbarButton.update();
	}

}
