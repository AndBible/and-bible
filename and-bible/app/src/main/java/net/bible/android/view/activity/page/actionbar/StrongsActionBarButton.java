package net.bible.android.view.activity.page.actionbar;

import android.support.v4.view.MenuItemCompat;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Toggle Strongs numbers on/off
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class StrongsActionBarButton extends QuickActionButton {

	private final DocumentControl documentControl;

	private final PageControl pageControl;

	@Inject
	public StrongsActionBarButton(DocumentControl documentControl, PageControl pageControl) {
		// SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
		// because when visible this button is ALWAYS on the Actionbar
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS|MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
		this.documentControl = documentControl;
		this.pageControl = pageControl;
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
		// update the show-strongs pref setting according to the ToggleButton
		CommonUtils.getSharedPreferences().edit().putBoolean("show_strongs_pref", !isStrongsVisible()).commit();
		// redisplay the current page; this will also trigger update of all menu items
		PassageChangeMediator.getInstance().forcePageUpdate();
		
		return true;
	}

	private boolean isStrongsVisible() {
		return pageControl.isStrongsShown();
	}

	@Override
	protected String getTitle() {
		return CommonUtils.getResourceString(isStrongsVisible() ? R.string.strongs_toggle_button_on : R.string.strongs_toggle_button_off);
	}

	/** 
	 * return true if Strongs are relevant to this doc & screen
	 * Don't show with speak button on narrow screen to prevent over-crowding 
	 */
	@Override
	protected boolean canShow() {
		return  documentControl.isStrongsInBook() &&
				(isWide() || !isSpeakMode());
	}
}
