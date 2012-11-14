package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public abstract class ShowDocumentToolbarButton implements ToolbarButton {

	private Button mButton;
	
	private boolean isEnoughRoomInToolbar = false;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public abstract Book getDocument();
	
	public ShowDocumentToolbarButton(View parent, int buttonId) {
        mButton = (Button)parent.findViewById(buttonId);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
    	CurrentPageManager.getInstance().setCurrentDocument(getDocument());
    	// exit the Daily Reading page, returning up to the Document page display to see the bible
    	CurrentActivityHolder.getInstance().getCurrentActivity().finish();
	}

	public void update() {
		Book doc = getDocument();
        helper.updateQuickButton(doc, mButton, canShow());
	}

	@Override
	public boolean canShow() {
		return isEnoughRoomInToolbar && getDocument()!=null;
	}

	@Override
	public int getPriority() {
		return 1;
	}
	
	@Override
	public void setEnoughRoomInToolbar(boolean isRoom) {
		isEnoughRoomInToolbar = isRoom;		
	}
}
