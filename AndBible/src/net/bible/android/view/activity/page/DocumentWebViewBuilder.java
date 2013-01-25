package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

/**
 * Build the main WebView component for displaying most document types
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DocumentWebViewBuilder {

	private BibleView bibleWebView;
	private BibleView bibleWebView2;
	private static final int BIBLE_WEB_VIEW_ID = 991;
	private static final int BIBLE_WEB_VIEW2_ID = 992;
	private View separatorLine;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

	private Activity mainActivity;

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
        bibleWebView = new BibleView(this.mainActivity, Screen.SCREEN_1);
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);

        bibleWebView2 = new BibleView(this.mainActivity, Screen.SCREEN_2);
        bibleWebView2.setId(BIBLE_WEB_VIEW2_ID);
        
        separatorLine = new View(this.mainActivity);
        separatorLine.setBackgroundColor(Color.GRAY);
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(ViewGroup parent) {
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;
    	boolean isSplitWebView = isWebView && parent.findViewById(BIBLE_WEB_VIEW2_ID)!=null;

    	if (!isWebView ||
    		 isSplitWebView!=splitScreenControl.isSplit()) {
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeWebView(parent);
    		
    		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1);
    		parent.addView(bibleWebView, lp);
    		mainActivity.registerForContextMenu(bibleWebView);

    		if (splitScreenControl.isSplit()) {
    			parent.addView(separatorLine, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 2, 0));
    			parent.addView(bibleWebView2, lp);
        		mainActivity.registerForContextMenu(bibleWebView2);
    		}
    	}
	}

	public void removeWebView(ViewGroup parent) {
		parent.removeAllViews();
	}
	
	public DocumentView getView() {
		if (ControlFactory.getInstance().getSplitScreenControl().isFirstScreenActive()) {
			return bibleWebView;
		} else {
			return bibleWebView2;
		}
	}
}
