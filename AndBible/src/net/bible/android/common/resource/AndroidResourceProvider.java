package net.bible.android.common.resource;

import net.bible.android.BibleApplication;

public class AndroidResourceProvider implements ResourceProvider {

	@Override
	public String getString(int resourceId) {
		return BibleApplication.getApplication().getString(resourceId);
	}

}
