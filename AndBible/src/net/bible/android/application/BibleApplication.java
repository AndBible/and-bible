package net.bible.android.application;

import android.app.Application;

public class BibleApplication extends Application{

	private static BibleApplication singleton;
	
	@Override
	public void onCreate() {
		super.onCreate();

		// save to a singleton to allow easy access from anywhere
		singleton = this;
	}

	public static BibleApplication getApplication() {
		return singleton;
	}

}
