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

// does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class CommentaryToolbarButton implements OnMenuItemClickListener {

	private MenuItem menuItem;
	private Book mSuggestedDocument;
	
	private static final String TAG = "CommentaryToolbarButton";
	
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
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
//        helper.updateQuickButton(mSuggestedDocument, getButton(), true);
	}

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
    	Log.d(TAG, "Quick cmtry select");

    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
    	
    	update();
    	menuItem.setTitle(getTitle());
		return true;
	}
	
	private String getTitle() {
		return StringUtils.left(mSuggestedDocument.getInitials(), 3);
	}
}
