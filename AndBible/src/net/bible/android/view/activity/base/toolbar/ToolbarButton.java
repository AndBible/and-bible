package net.bible.android.view.activity.base.toolbar;

public interface ToolbarButton {
	public void update();
	
	public boolean canShow();
	
	public int getPriority();
}
