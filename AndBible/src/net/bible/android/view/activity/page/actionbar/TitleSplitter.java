package net.bible.android.view.activity.page.actionbar;

import net.bible.service.common.CommonUtils;

public class TitleSplitter {

	public String[] split(String text) {
		if (text==null) {
			return new String[0];
		}
		String[] parts;
		// only split if in portrait because landscape actionBar has more width but less height
		if (CommonUtils.isPortrait()) {
			// this is normally used for verses e.g. '1Cor 2:1' -> '1Cor','2:1'
			parts = text.split("(?<=... )");
			
			// this is normally used for module names e.g. 'GodsWord' -> 'Gods','Word'
			if (parts.length==1) {
				parts = text.split("(?<=[a-z])(?=[A-Z])");
			}
		} else {
			parts = new String[1];
			parts[0] = text;
		}
		return parts;
	}
}
