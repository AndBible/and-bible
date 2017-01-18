package net.bible.android.common.resource;

import net.bible.android.BibleApplication;
import net.bible.android.control.ApplicationScope;

import javax.inject.Inject;

@ApplicationScope
public class AndroidResourceProvider implements ResourceProvider {

	@Inject
	public AndroidResourceProvider() {
	}

	@Override
	public String getString(int resourceId) {
		return BibleApplication.getApplication().getString(resourceId);
	}

}
