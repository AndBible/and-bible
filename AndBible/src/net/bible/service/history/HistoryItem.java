package net.bible.service.history;

public interface HistoryItem {

	public String getDescription();
	
	// do back to the state at this point
	public abstract void revertTo();

}