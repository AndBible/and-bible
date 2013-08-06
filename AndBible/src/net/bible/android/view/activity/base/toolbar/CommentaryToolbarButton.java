package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.widget.Button;

// does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class CommentaryToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private Book mSuggestedDocument;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CommentaryToolbarButton(View parent) {
        super(parent, R.id.quickCommentaryChange);
	}

	@Override
	protected void onButtonPress() {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
	}

	public void update() {
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
        helper.updateQuickButton(mSuggestedDocument, getButton(), true);
	}

	@Override
	public boolean canShow() {
		return mSuggestedDocument!=null;
	}

	@Override
	public int getPriority() {
		return 2;
	}
}
