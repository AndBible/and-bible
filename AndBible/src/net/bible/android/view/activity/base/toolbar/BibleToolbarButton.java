package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.widget.Button;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleToolbarButton extends ToolbarButtonBase<Button> {

	private Book mSuggestedDocument;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public BibleToolbarButton(View parent) {
        super(parent, R.id.quickBibleChange);
	}

	public void onButtonPress() {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
	}

	public void update() {
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
        helper.updateQuickButton(mSuggestedDocument, getButton(), true);
	}

	@Override
	public boolean canShow() {
		return mSuggestedDocument!=null;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
