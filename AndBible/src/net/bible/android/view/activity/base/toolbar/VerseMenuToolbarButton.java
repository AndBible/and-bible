package net.bible.android.view.activity.base.toolbar;

import android.app.Activity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;
import org.crosswire.jsword.book.BookCategory;

public class VerseMenuToolbarButton extends ToolbarButtonBase implements ToolbarButton {

	private static final String VERSE_CONTEXT_MENU_BUTTON_PREFERENCE_KEY = "tilt_to_scroll_pref";
	private final DocumentControl documentControl = ControlFactory.getInstance().getDocumentControl();

	private Button mButton;

	public VerseMenuToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.verseMenu);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (currentActivity instanceof MainBibleActivity) {
			((MainBibleActivity)currentActivity).openContextMenu();
		}
	}

	public void update() {
        boolean showButton = canShow();
        mButton.setVisibility(showButton? View.VISIBLE : View.GONE);
	}

	/** return true if verse context menu is to be shown */
	@Override
	public boolean canShow() {
		return CommonUtils.getSharedPreferences().getBoolean(VERSE_CONTEXT_MENU_BUTTON_PREFERENCE_KEY, false) &&
				BookCategory.BIBLE.equals(documentControl.getCurrentCategory());
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
