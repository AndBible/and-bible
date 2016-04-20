package net.bible.service.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.base.AndBibleActivity;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Application managed History List
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class HistoryManager {

	private static int MAX_HISTORY = 80;

	private final static String HISTORY_STR = "history";
	private final static String KEY_STR = "historyItemKey";
	private final static String BOOK_STR = "historyItemBook";
	private final static String TOP_STR = "top";
	
	private Map<Window, Stack<HistoryItem>> screenHistoryStackMap = new HashMap<>();

	private static HistoryManager singleton = new HistoryManager();

	private boolean isGoingBack = false;
	
	private static WindowControl windowControl = ControlFactory.getInstance().getWindowControl();
	
	private static final String TAG = "HistoryManager";
	
	public static HistoryManager getInstance() {
		return singleton;
	}
	
	private HistoryManager() {}
	
	public void initialise() {
		Log.i(TAG, "Registering HistoryManager with EventBus");
		// register for BeforePageCangeEvent
		ABEventBus.getDefault().safelyRegister(this);
	}
	
    /** allow current page to save any settings or data before being changed
     */
    public void onEvent(BeforeCurrentPageChangeEvent event) {
    	beforePageChange();
    }
	
	public boolean canGoBack() {
		if(getHistoryStack().size()>0)
			if (!getHistoryStack().peek().isFromPersistentHistory())
				return true;
		return false;
	}

	private void saveHistoryToPersistent(KeyHistoryItem keyItem) {
		final Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		SharedPreferences history = currentActivity.getSharedPreferences(HISTORY_STR, Context.MODE_PRIVATE);
		int i = history.getInt(TOP_STR, -1);
		i = i >= MAX_HISTORY - 1 ? 0 : i + 1;
		SharedPreferences.Editor edit = history.edit();
		edit.putString(BOOK_STR + i, keyItem.getDoc().getName());
		edit.putString(KEY_STR + i, keyItem.getKey().getName());
		edit.putInt(TOP_STR, i);
		edit.commit();
	}

	/**
	 *  called when a verse is changed to allow current Activity to be saved in History list
	 */
	public void beforePageChange() {
		// if we cause the change by requesting Back then ignore it
		if (!isGoingBack) {
			HistoryItem item = createHistoryItem();
			add(getHistoryStack(), item);
			if (item instanceof KeyHistoryItem)
				saveHistoryToPersistent((KeyHistoryItem) item); // save to SharedPreferences
		}
	}
	private HistoryItem createHistoryItem() {
		HistoryItem historyItem = null;
		
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (currentActivity instanceof MainBibleActivity) {
			CurrentPage currentPage = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage();
			Book doc = currentPage.getCurrentDocument();
			if (currentPage.getKey()==null) {
				return null;
			}
			
			Key key = currentPage.getSingleKey();
			float yOffsetRatio = currentPage.getCurrentYOffsetRatio();
			historyItem = new KeyHistoryItem(doc, key, yOffsetRatio);
		} else if (currentActivity instanceof AndBibleActivity) {
			AndBibleActivity andBibleActivity = (AndBibleActivity)currentActivity;
			if (andBibleActivity.isIntegrateWithHistoryManager()) {
				historyItem = new IntentHistoryItem(currentActivity.getTitle(), ((AndBibleActivity) currentActivity).getIntentForHistoryList());
			}
		}
		return historyItem;
	}
	
	public void goBack() {
		if (getHistoryStack().size()>0) {
			try {
				Log.d(TAG, "History size:"+getHistoryStack().size());
				isGoingBack = true;
	
				// pop the previous item
				HistoryItem previousItem = getHistoryStack().pop();
	
				if (previousItem!=null) {
					Log.d(TAG, "Going back to:"+previousItem);
					previousItem.revertTo();
					
					// finish current activity if not the Main screen
					Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
					if (!(currentActivity instanceof MainBibleActivity)) {
						currentActivity.finish();
					}
				}
			} finally {
				isGoingBack = false;
			}
		}
	}
	
	public List<HistoryItem> getHistory() {
		List<HistoryItem> allHistory = new ArrayList<HistoryItem>(getHistoryStack());
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

	private void loadHistoryFromPersitent(Stack<HistoryItem> historyStack){
		final Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		SharedPreferences history = currentActivity.getSharedPreferences(HISTORY_STR, Context.MODE_PRIVATE);
		int originalI = history.getInt(TOP_STR, -1);
		if (originalI != -1){
			int i = originalI;
			do{
				i = i >= MAX_HISTORY - 1 ? 0: i+1;
				String docStr = history.getString(BOOK_STR + i, null);
				String keyStr = history.getString(KEY_STR + i, null);
				if (docStr == null || keyStr == null)
					continue;
				try {
					Book doc = Books.installed().getBook(docStr);
					Key key = doc.getKey(keyStr);
					KeyHistoryItem item = new KeyHistoryItem(doc, key, 0);
					item.setFromPersistent(true);
					add(historyStack, item);
				} catch (NoSuchKeyException e) {}
				catch (NullPointerException e) {}
			} while (i != originalI);
		}

	}

	private Stack<HistoryItem> getHistoryStack() {
		Window window = windowControl.getActiveWindow();
		Stack<HistoryItem> historyStack = screenHistoryStackMap.get(window);
		if (historyStack==null) {
			synchronized(screenHistoryStackMap) {
				historyStack = screenHistoryStackMap.get(window);
				if (historyStack==null) {
					historyStack = new Stack<HistoryItem>();
					loadHistoryFromPersitent(historyStack); // loading history from SharedPreferences
					screenHistoryStackMap.put(window, historyStack);
				}
			}
		}
		return historyStack;
	}

}
