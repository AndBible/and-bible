package net.bible.android.view.activity.page.toolbar;

import net.bible.android.view.activity.page.MenuCommandHandler;
import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

public class BibleToolbarButtonManager {

	private HomeTitle homeTitle = new HomeTitle();
	private NavigationDrawer navigationDrawer = new NavigationDrawer();
	private BibleToolbarButton bibleToolbarButton = new BibleToolbarButton();
	private CommentaryToolbarButton commentaryToolbarButton = new CommentaryToolbarButton();
	private DictionaryToolbarButton dictionaryToolbarButton = new DictionaryToolbarButton();
	
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar, MenuCommandHandler menuCommandHandler) {
		homeTitle.addToBar(actionBar);
		navigationDrawer.addToBar(activity, actionBar, menuCommandHandler);
		
		bibleToolbarButton.addToMenu(menu);
		commentaryToolbarButton.addToMenu(menu);
		dictionaryToolbarButton.addToMenu(menu);
	}
	
	public void updateButtons() {
		homeTitle.update();
		
		bibleToolbarButton.update();
		commentaryToolbarButton.update();
		dictionaryToolbarButton.update();
	}
	
    public void onPostCreate() {
    	navigationDrawer.onPostCreate();
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	return navigationDrawer.onOptionsItemSelected(item);
    }
	
}
