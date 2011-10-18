package net.bible.android.view.activity.page;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.DocumentView;
import android.app.Activity;
import android.view.ViewGroup;

/**
 * Build the main WebView component for displaying most document types
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DocumentWebViewBuilder {

	private BibleView bibleWebView;
	private static final int BIBLE_WEB_VIEW_ID = 991;
	
	private Activity mainActivity;

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
        bibleWebView = new BibleView(this.mainActivity);
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(ViewGroup parent) {
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;

    	if (!isWebView) {
    		parent.addView(bibleWebView);
    		mainActivity.registerForContextMenu(bibleWebView);
    	}
	}

	public void removeWebView(ViewGroup parent) {
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;
    	if (isWebView) {
    		parent.removeView(bibleWebView);
    		mainActivity.unregisterForContextMenu(bibleWebView);
    	}
	}
	
	public DocumentView getView() {
		return bibleWebView;
	}
}
