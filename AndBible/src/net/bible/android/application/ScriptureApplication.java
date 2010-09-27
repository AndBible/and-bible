package net.bible.android.application;

import android.app.Application;

public class ScriptureApplication extends Application{

	private static ScriptureApplication singleton;
	
	public ScriptureApplication() {
		super();
		
		// save to a singleton to allow easy access from anywhere
		singleton = this;
	}
	
	public static ScriptureApplication getApplication() {
		return singleton;
	}

}
