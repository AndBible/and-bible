package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.page.CurrentPageManager;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickDocumentChangeToolbarButton implements OnMenuItemClickListener {
	
	private MenuItem menuItem;
	private Book mSuggestedDocument;
	private int showAsActionFlags;

	abstract Book getSuggestedDocument();
	
	public QuickDocumentChangeToolbarButton() {
		this(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	}
	public QuickDocumentChangeToolbarButton(int showAsActionFlags) {
		this.showAsActionFlags = showAsActionFlags;
	}

	public void addToMenu(Menu menu) {
		if (menuItem==null) {
			menuItem = menu.add("");
			MenuItemCompat.setShowAsAction(menuItem, showAsActionFlags);
			menuItem.setOnMenuItemClickListener(this);
			update();
		}
	}

	public void update() {
        mSuggestedDocument = getSuggestedDocument();
        
        if (menuItem!=null) {
            // if there are no documents of this sort then hide quick change button
            menuItem.setVisible(mSuggestedDocument!=null);

            menuItem.setTitle(getTitle());
        }
	}

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
    	return true;
	}
	
	private String getTitle() {
		if (mSuggestedDocument!=null) {
			return StringUtils.left(mSuggestedDocument.getInitials(), 3);
		} else {
			return "";
		}
	}
}
