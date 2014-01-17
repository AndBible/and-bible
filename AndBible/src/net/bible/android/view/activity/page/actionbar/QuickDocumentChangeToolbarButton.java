package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.page.CurrentPageManager;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;

import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickDocumentChangeToolbarButton extends QuickActionButton implements OnMenuItemClickListener {
	
	private Book mSuggestedDocument;

	abstract Book getSuggestedDocument();
	
	public QuickDocumentChangeToolbarButton(int showAsActionFlags) {
		super(showAsActionFlags);
	}

	public void update() {
        mSuggestedDocument = getSuggestedDocument();
        super.update();
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
