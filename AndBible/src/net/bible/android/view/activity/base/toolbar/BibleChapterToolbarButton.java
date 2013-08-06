package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.navigation.GridChoosePassageChapter;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class BibleChapterToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	private final PageControl pageControl = ControlFactory.getInstance().getPageControl();

	public BibleChapterToolbarButton(View parent) {
		super(parent, R.id.bibleChapterButton);
	}

	@Override
	protected void onButtonPress() {
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent handlerIntent = new Intent(currentActivity, GridChoosePassageChapter.class);
    	currentActivity.startActivityForResult(handlerIntent, ActivityBase.STD_REQUEST_CODE);
	}

	@Override
	public void update() {
		super.update();

		if (getButton()!=null) {
			// run on ui thread
			getButton().post(new Runnable() {
				@Override
				public void run() {
					getButton().setText(Integer.toString(pageControl.getCurrentBibleVerse().getChapter()));
				}
			});
		}
	}
	
	/** return true if verse context menu is to be shown */
	@Override
	public boolean canShow() {
		return documentControl.showSplitPassageSelectorButtons();
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
