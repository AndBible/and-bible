package net.bible.android.control.page;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.Window;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * Retain the link between a window and its associated PageTiltScrollControl.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
public class PageTiltScrollControlFactory {

	private final Map<Window, PageTiltScrollControl> screenPageTiltScrollControlMap = new HashMap<>();

	@Inject
	public PageTiltScrollControlFactory() {
	}

	public PageTiltScrollControl getPageTiltScrollControl(Window window) {
		PageTiltScrollControl pageTiltScrollControl = screenPageTiltScrollControlMap.get(window);
		if (pageTiltScrollControl==null) {
			synchronized(screenPageTiltScrollControlMap) {
				pageTiltScrollControl = screenPageTiltScrollControlMap.get(window);
				if (pageTiltScrollControl==null) {
					pageTiltScrollControl = new PageTiltScrollControl();
					screenPageTiltScrollControlMap.put(window, pageTiltScrollControl);
				}
			}
		}
		return pageTiltScrollControl;
	}
}
