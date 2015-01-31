package net.bible.android.control.page.splitscreen;

public class Screen {

	public enum ScreenState {MINIMISED, MAXIMISED, SPLIT} 

	// 1 based screen no
	private int screenNo;
	
	private ScreenState state = ScreenState.MAXIMISED;
	
	private boolean isSynchronised = true;
	
	public Screen(int screenNo, ScreenState screenState) {
		this.screenNo = screenNo;
		this.state = screenState;
	}

	public int getScreenNo() {
		return screenNo;
	}
	
	public ScreenState getState() {
		return state;
	}
	
	public boolean isSynchronised() {
		return isSynchronised;
	}
	
	public void setSynchronised(boolean isSynchronised) {
		this.isSynchronised = isSynchronised;
	}

	public void setState(ScreenState state) {
		this.state = state;
	}
	
	@Override
	public String toString() {
		return "Screen [screenNo=" + screenNo + ", state=" + state
				+ ", isSynchronised=" + isSynchronised + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + screenNo;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Screen other = (Screen) obj;
		if (screenNo != other.screenNo)
			return false;
		return true;
	}
}
