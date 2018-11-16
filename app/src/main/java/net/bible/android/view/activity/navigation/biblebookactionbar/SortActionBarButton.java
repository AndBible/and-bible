package net.bible.android.view.activity.navigation.biblebookactionbar;

import androidx.core.view.MenuItemCompat;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;

import javax.inject.Inject;

/** 
 * Toggle sort of Bible books by name or canonically
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SortActionBarButton extends QuickActionButton {

	private final NavigationControl navigationControl;

	@Inject
	public SortActionBarButton(NavigationControl navigationControl) {
		// SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
		// because when visible this button is ALWAYS on the Actionbar
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

		this.navigationControl = navigationControl;
	}

	
	@Override
	protected int getIcon() {
		return android.R.drawable.ic_menu_sort_by_size;
	}


	@Override
	protected String getTitle() {
		return navigationControl.getBibleBookSortOrderButtonDescription();
	}

	/** 
	 * return true if Strongs are relevant to this doc & screen
	 * Don't show with speak button on narrow screen to prevent over-crowding 
	 */
	@Override
	protected boolean canShow() {
		return  true;
	}
}
