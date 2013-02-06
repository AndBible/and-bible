package net.bible.service.history;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;

public abstract class HistoryItemBase implements HistoryItem {

	private Screen screen;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

	public HistoryItemBase() {
		super();
		this.screen = splitScreenControl.getCurrentActiveScreen();
	}

	@Override
	public Screen getScreen() {
		return screen;
	}
}
