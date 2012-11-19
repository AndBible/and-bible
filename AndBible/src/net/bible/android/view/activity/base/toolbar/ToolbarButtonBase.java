package net.bible.android.view.activity.base.toolbar;

abstract public class ToolbarButtonBase implements ToolbarButton {

	private boolean isEnoughRoomInToolbar = false;
	private boolean isNarrow = true;

	public boolean isEnoughRoomInToolbar() {
		return isEnoughRoomInToolbar;
	}
	@Override
	public void setEnoughRoomInToolbar(boolean isRoom) {
		isEnoughRoomInToolbar = isRoom;
	}
	public boolean isNarrow() {
		return isNarrow;
	}
	@Override
	public void setNarrow(boolean isNarrow) {
		this.isNarrow = isNarrow;
	}
}
