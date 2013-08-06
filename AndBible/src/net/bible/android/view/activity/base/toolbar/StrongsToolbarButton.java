package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.service.common.CommonUtils;

import android.view.View;
import android.widget.ToggleButton;

public class StrongsToolbarButton extends ToolbarButtonBase<ToggleButton> implements ToolbarButton {

	public StrongsToolbarButton(View parent) {
        super(parent, R.id.strongsToggle);
	}

	@Override
	protected void onButtonPress() {
		// update the show-strongs pref setting according to the ToggleButton
		CommonUtils.getSharedPreferences().edit().putBoolean("show_strongs_pref", getButton().isChecked()).commit();
		// redisplay the current page
		PassageChangeMediator.getInstance().forcePageUpdate();
	}

	public void update() {
        boolean showStrongsToggle = canShow();
        ToggleButton button = getButton();
        button.setVisibility(showStrongsToggle? View.VISIBLE : View.GONE);
        if (showStrongsToggle) {
	        boolean isShowstrongs = CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	        button.setChecked(isShowstrongs);
        }
	}

	/** return true if Strongs are relevant to this doc & screen */
	@Override
	public boolean canShow() {
		return isEnoughRoomInToolbar() && ControlFactory.getInstance().getDocumentControl().isStrongsInBook();
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
