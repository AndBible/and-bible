package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleToolbarButton implements OnMenuItemClickListener {

	private MenuItem menuItem;
	private Book mSuggestedDocument;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();

	public void addToMenu(Menu menu) {
		if (menuItem==null) {
			update();
			menuItem = menu.add(getTitle());
			MenuItemCompat.setShowAsAction(menuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			menuItem.setOnMenuItemClickListener(this);
		}
	}

	public void update() {
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
        if (menuItem!=null) {
        	menuItem.setTitle(getTitle());
        }
	}

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
    	return true;
	}
	
	private String getTitle() {
		return StringUtils.left(mSuggestedDocument.getInitials(), 3);
	}
}
