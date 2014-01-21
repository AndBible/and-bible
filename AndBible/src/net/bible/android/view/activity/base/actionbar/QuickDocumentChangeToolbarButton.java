package net.bible.android.view.activity.base.actionbar;

import net.bible.android.control.page.CurrentPageManager;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickDocumentChangeToolbarButton extends QuickActionButton implements OnMenuItemClickListener {
	
	private Book mSuggestedDocument;

	protected abstract Book getSuggestedDocument();
	
	/**
	 * SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
	 */
	public QuickDocumentChangeToolbarButton() {
		this(MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
	}
	
	public QuickDocumentChangeToolbarButton(int showAsActionFlags) {
		super(showAsActionFlags);
	}

	public void update(MenuItem menuItem) {
        mSuggestedDocument = getSuggestedDocument();
        super.update(menuItem);
	}

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
    	return true;
	}

	@Override
	protected boolean canShow() {
		return mSuggestedDocument!=null;
	}
	
	@Override
	protected String getTitle() {
		if (mSuggestedDocument!=null) {
			return StringUtils.left(mSuggestedDocument.getInitials(), 4);
		} else {
			return "";
		}
	}
}
