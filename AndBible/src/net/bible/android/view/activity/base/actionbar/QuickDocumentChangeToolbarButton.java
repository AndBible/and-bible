package net.bible.android.view.activity.base.actionbar;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickDocumentChangeToolbarButton extends QuickActionButton implements OnMenuItemClickListener {
	
	private Book mSuggestedDocument;

	protected abstract Book getSuggestedDocument();
	
	private static int ACTION_BUTTON_MAX_CHARS = CommonUtils.getResourceInteger(R.integer.action_button_max_chars);
	
	/**
	 * SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
	 */
	public QuickDocumentChangeToolbarButton() {
		this(MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
	}
	
	public QuickDocumentChangeToolbarButton(int showAsActionFlags) {
		super(showAsActionFlags);
	}

	@Override
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
			return StringUtils.left(mSuggestedDocument.getInitials(), ACTION_BUTTON_MAX_CHARS);
		} else {
			return "";
		}
	}
}
