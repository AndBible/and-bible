package net.bible.android.view.activity.navigation.biblebookactionbar;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.view.activity.base.actionbar.ToggleActionBarButton;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** Toggle between 66 Bible books and deuterocanonical books
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ScriptureToggleActionBarButton extends ToggleActionBarButton {

	private final NavigationControl navigationControl;

	@Inject
	public ScriptureToggleActionBarButton(NavigationControl navigationControl) {
		super(R.drawable.ic_action_new, R.drawable.ic_action_undo);

		this.navigationControl = navigationControl;
	}

	@Override
	protected String getTitle() {
		if (isOn()) {
			return CommonUtils.getResourceString(R.string.deuterocanonical); 
		} else {
			return CommonUtils.getResourceString(R.string.bible); 
		}
	}

	@Override
	protected boolean canShow() {
		return navigationControl.getBibleBooks(false).size()>0;
	}
}
