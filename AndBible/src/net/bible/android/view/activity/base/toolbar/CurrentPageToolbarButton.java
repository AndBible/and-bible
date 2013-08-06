package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class CurrentPageToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private String mCurrentPageTitle;

	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	
	@SuppressWarnings("unused")
	private static final String TAG = "Toolbar";
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentPageToolbarButton(View parent) {
        super(parent, R.id.titlePassage);

        // listen for verse change events
        PassageEventManager.getInstance().addPassageEventListener(new PassageEventListener() {
			@Override
			public void pageDetailChange(PassageEvent event) {
				update();
			}
		});
	}

	@Override
	protected void onButtonPress() {
		// load Document selector
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent pageHandlerIntent = new Intent(currentActivity, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
    	currentActivity.startActivityForResult(pageHandlerIntent, 1);
	}

	public void update() {
		super.update();
		
        mCurrentPageTitle = ControlFactory.getInstance().getPageControl().getCurrentPageTitle();

        // must do ui update in ui thread
        // copy title to ensure it isn't changed before ui thread executes the following
        final String title = mCurrentPageTitle;
		getButton().post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(title, getButton());
			}
		});
	}

	@Override
	public boolean canShow() {
		return 	mCurrentPageTitle!=null && 
				!documentControl.showSplitPassageSelectorButtons();
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
