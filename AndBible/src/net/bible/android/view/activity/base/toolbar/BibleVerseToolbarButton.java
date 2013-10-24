package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.navigation.GridChoosePassageVerse;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.book.BookCategory;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class BibleVerseToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();
	private final PageControl pageControl = ControlFactory.getInstance().getPageControl();

	public BibleVerseToolbarButton(View parent) {
        super(parent, R.id.bibleVerseButton);
        
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
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (currentActivity instanceof MainBibleActivity) {
			if (BookCategory.BIBLE.equals(documentControl.getCurrentCategory())) {
				// if bible then show verse context menu
				((MainBibleActivity)currentActivity).openContextMenu();
			} else {
				// if commentary allow to change verse quickly
		    	Intent handlerIntent = new Intent(currentActivity, GridChoosePassageVerse.class);
		    	currentActivity.startActivityForResult(handlerIntent, ActivityBase.STD_REQUEST_CODE);
			}
		}
	}

	@Override
	public void update() {
		super.update();

		// run on ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
				getButton().setText(Integer.toString(pageControl.getCurrentBibleVerse().getVerse()));
			}
		});
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
