package net.bible.service.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

public class HistoryManager {

	private static int MAX_HISTORY = 20;
	private Stack<KeyHistoryItem> history = new Stack<KeyHistoryItem>();
	private static HistoryManager singleton = new HistoryManager();

	private boolean isGoingBack = false;
	
	private static final String TAG = "HistoryManager";
	
	public static HistoryManager getInstance() {
		return singleton;
	}
	
	public boolean canGoBack() {
		return history.size()>0;
	}
	
	/**
	 *  called when a verse is changed
	 */
	public void beforePageChange() {
		// if we cause the change by requesting Back then ignore it
		if (!isGoingBack) {
			KeyHistoryItem item = createHistoryItem();
			add(history, item);
		}
	}
	private KeyHistoryItem createHistoryItem() {
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		Book doc = currentPage.getCurrentDocument();
		if (currentPage.getKey()==null) {
			return null;
		}
		
		KeyHistoryItem historyItem;
		if (currentPage instanceof CurrentBiblePage) {
			CurrentBiblePage currentBiblePage = (CurrentBiblePage)currentPage;
			Key key = currentBiblePage.getKey(true);
			historyItem = new KeyHistoryItem(doc, key);
		} else {
			Key key = CurrentPageManager.getInstance().getCurrentPage().getKey();
			historyItem = new KeyHistoryItem(doc, key);
		}
		
		return historyItem;
	}
	
	//not used just keep a list of verse changes for now
	public void documentChanged() {
	}
	
	public void goBack() {
		if (history.size()>1) {
			try {
				Log.d(TAG, "History size:"+history.size());
				isGoingBack = true;
	
				// pop the previous item
				KeyHistoryItem previousItem = history.pop();
	
				if (previousItem!=null) {
					Log.d(TAG, "Going back to:"+previousItem);
					previousItem.revertTo();
				}
			} finally {
				isGoingBack = false;
			}
		}
	}
	
	public List<HistoryItem> getHistory() {
		List<HistoryItem> allHistory = new ArrayList<HistoryItem>(history);
		// reverse so most recent items are at top rather than end
		Collections.reverse(allHistory);
		return allHistory;
	}
	
	/** add item and check size of stack
	 * 
	 * @param stack
	 * @param item
	 */
	private synchronized void add(Stack<KeyHistoryItem> stack, KeyHistoryItem item) {
		if (item!=null) {
			Log.d(TAG, "Adding "+item.getKey()+" to history");
			Log.d(TAG, "Stack size:"+stack.size());
			
			stack.push(item);
			
			while (stack.size()>MAX_HISTORY) {
				Log.d(TAG, "Shrinking large stack");
				stack.remove(0);
			}
		}
	}
}
