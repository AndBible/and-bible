package net.bible.android.view.activity.base.actionbar;

import net.bible.service.common.CommonUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TitleSplitter {

	public String[] split(String text) {
		if (text==null) {
			return new String[0];
		}
		String[] parts;
		// only split if in portrait because landscape actionBar has more width but less height
		if (CommonUtils.isPortrait()) {
			// this is normally used for verses e.g. '1Cor 2:1' -> '1Cor','2:1'
			// Explained: there must be at least 3 chars before a space to split
			parts = text.split("(?<=... )");
			
			// this is normally used for module names e.g. 'GodsWord' -> 'Gods','Word'
			if (parts.length==1) {
				parts = text.split("(?<=[a-z])(?=[A-Z0-9])");
			}
		} else {
			parts = new String[1];
			parts[0] = text;
		}
		return parts;
	}
}
