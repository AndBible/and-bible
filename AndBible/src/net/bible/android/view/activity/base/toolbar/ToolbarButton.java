package net.bible.android.view.activity.base.toolbar;

public interface ToolbarButton {
	public void update();

	public void setEnoughRoomInToolbar(boolean isRoom);
	public void setNarrow(boolean isNarrow);
	
	// is the current state suitable to show this button
	public boolean canShow();
	
	public int getPriority();
}
