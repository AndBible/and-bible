package net.bible.service.history;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.passage.Key;

import android.util.Log;

public class HistoryManager {

	private static int MAX_HISTORY = 20;
	private Stack<VerseHistoryItem> history = new Stack<VerseHistoryItem>();
	private Stack<VerseHistoryItem> forward = new Stack<VerseHistoryItem>();
	
	private static HistoryManager singleton = new HistoryManager();
	
	private static final String TAG = "HistoryManager";
	
	public static HistoryManager getInstance() {
		return singleton;
	}
	
	public void initialise() {
		CurrentPassage.getInstance().addObserver(new Observer() {
			@Override
			public void update(Observable observable, Object data) {
				verseChanged();
			}
    	});
		
		// and put the current verse in History
		verseChanged();
	}
	
	public boolean canGoBack() {
		return history.size()>1;
	}
	
	// called when a verse is changed
	public void verseChanged() {
		Key verse = CurrentPassage.getInstance().getKey();
		if (verse!=null) {
			Log.d(TAG, "Adding "+verse+" to history");
			VerseHistoryItem item = new VerseHistoryItem(verse);
			add(history, item);
			forward.clear();
		}
	}
	
	//not used just keep a list of verse changes for now
	public void documentChanged() {
		
	}
	
	public void goBack() {
		if (history.size()>1) {
			Log.d(TAG, "1 History size:"+history.size());

			// pop the current displayed verse 
			VerseHistoryItem currentItem = history.pop();
			add(forward, currentItem);

			// and go to previous item
			VerseHistoryItem previousItem = history.peek();
			if (previousItem!=null) {
				Log.d(TAG, "Going back to:"+previousItem);
				previousItem.revertTo();
			}
		}
	}
	
	public void goForward() {
		VerseHistoryItem item = forward.pop();
		if (item!=null) {
			item.revertTo();
			add(history, item);
		}
	}
	
	public List<VerseHistoryItem> getHistory() {
		return null;
	}
	
	private boolean isInHistory(Key key) {
		Log.d(TAG, "History len:"+history.size());
		for (VerseHistoryItem item : history) {
			Log.d(TAG, "comparing "+key+" with "+item);
			if (key.equals(item.getVerse())) {
				return true;
			}
		}
		for (VerseHistoryItem item : forward) {
			if (key.equals(item.getVerse())) {
				return true;
			}
		}
		return false;
	}

	/** add item and check size of stack
	 * 
	 * @param stack
	 * @param item
	 */
	private void add(Stack<VerseHistoryItem> stack, VerseHistoryItem item) {
		// ensure no duplicates
		// if we don't do this then goBack() would cause the prev key to be re-added and we only ever can go back 1 place
		stack.removeElement(item);
		
		stack.push(item);
		
		if (stack.size()>MAX_HISTORY) {
			stack.setSize(MAX_HISTORY);
		}
	}
}
