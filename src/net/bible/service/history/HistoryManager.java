package net.bible.service.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.activity.search.Search;
import net.bible.android.view.activity.search.SearchResults;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.util.Log;

public class HistoryManager {

	private static int MAX_HISTORY = 80;
	private Stack<HistoryItem> history = new Stack<HistoryItem>();
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
			HistoryItem item = createHistoryItem();
			add(history, item);
		}
	}
	private HistoryItem createHistoryItem() {
		HistoryItem historyItem = null;
		
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (currentActivity instanceof MainBibleActivity) {
			CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
			Book doc = currentPage.getCurrentDocument();
			if (currentPage.getKey()==null) {
				return null;
			}
			
			Key key = currentPage.getSingleKey();
			float yOffsetRatio = currentPage.getCurrentYOffsetRatio();
			historyItem = new KeyHistoryItem(doc, key, yOffsetRatio);
		} else if (currentActivity instanceof Search) {
			historyItem = new IntentHistoryItem("Search", currentActivity.getIntent());
		} else if (currentActivity instanceof SearchResults) {
			historyItem = new IntentHistoryItem("Search Results", currentActivity.getIntent());
		}
		return historyItem;
	}
	
	public void goBack() {
		if (history.size()>0) {
			try {
				Log.d(TAG, "History size:"+history.size());
				isGoingBack = true;
	
				// pop the previous item
				HistoryItem previousItem = history.pop();
	
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
	private synchronized void add(Stack<HistoryItem> stack, HistoryItem item) {
		if (item!=null) {
			if (stack.isEmpty() || !item.equals(stack.peek())) {
				Log.d(TAG, "Adding "+item+" to history");
				Log.d(TAG, "Stack size:"+stack.size());
				
				stack.push(item);
				
				while (stack.size()>MAX_HISTORY) {
					Log.d(TAG, "Shrinking large stack");
					stack.remove(0);
				}
			}
		}
	}
}
