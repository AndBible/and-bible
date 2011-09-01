package net.bible.android.view.activity.base;

public class SharedActivityState {
	// show title bar state is shared by all Activity windows
	private boolean mFullScreen = false;

	private static SharedActivityState singleton = new SharedActivityState();
	
	public static SharedActivityState getInstance() {
		return singleton;
	}

    public void toggleFullScreen() {
    	mFullScreen = !mFullScreen;
    }
	
	public boolean isFullScreen() {
		return mFullScreen;
	}
}
