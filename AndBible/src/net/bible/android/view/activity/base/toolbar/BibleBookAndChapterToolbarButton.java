package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.CurrentActivityHolder;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class BibleBookAndChapterToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	private final PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	@SuppressWarnings("unused")
	private static final String TAG = "Toolbar";
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public BibleBookAndChapterToolbarButton(View parent) {
        super(parent, R.id.titlePassage);
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

		// run on ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(pageControl.getCurrentPageTitle(), getButton());
			}
		});
	}

	/** return true if this button is to be shown */
	@Override
	public boolean canShow() {
		return documentControl.showSplitPassageSelectorButtons();
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
