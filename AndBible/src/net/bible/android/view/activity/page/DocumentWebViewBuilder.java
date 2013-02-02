package net.bible.android.view.activity.page;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.util.TouchDelegateView;
import net.bible.service.common.CommonUtils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * TEMP NOTE: http://stackoverflow.com/questions/961944/overlapping-views-in-android
 * FOR OVERLAY IMAGE
 */
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
	private Separator separator;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

	private ViewGroup splitFrameLayout1;
	private ViewGroup splitFrameLayout2;
	private ViewGroup parentLayout;
	private Activity mainActivity;

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
        bibleWebView = new BibleView(this.mainActivity, Screen.SCREEN_1);
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);

        bibleWebView2 = new BibleView(this.mainActivity, Screen.SCREEN_2);
        bibleWebView2.setId(BIBLE_WEB_VIEW2_ID);
        
        separator = new Separator(this.mainActivity);
        
        splitFrameLayout1 = new FrameLayout(this.mainActivity);
        splitFrameLayout2 = new FrameLayout(this.mainActivity);

	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(ViewGroup parent) {
		this.parentLayout = parent;
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;
    	boolean isAlreadySplitWebView = isWebView && parent.findViewById(BIBLE_WEB_VIEW2_ID)!=null;

    	if (!isWebView || isAlreadySplitWebView!=splitScreenControl.isSplit()) {
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeWebView(parent);
    		
    		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1);

			// add top view whether split or not

    		if (!splitScreenControl.isSplit()) {
    			parent.addView(bibleWebView, lp);
    		} else {
    			//AddTop FrameLayout, then webview, then separatorTouchExtender(beside separator)
    			// add a FrameLayout
        		parent.addView(splitFrameLayout1, lp);

    			// add bible to framelayout
    			LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    			splitFrameLayout1.addView(bibleWebView, frameLayoutParamsBibleWebView);

    			// add separator handle touch delegate to framelayout
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, CommonUtils.convertDipsToPx(10), Gravity.BOTTOM);
    			splitFrameLayout1.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);

    			
    			// line dividing the split screens
    			parent.addView(separator, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, (int) BibleApplication.getApplication().getResources().getDimension(R.dimen.split_screen_separator_width), 0));

    			
    			// add a FrameLayout to the lower part of the LinearLayout to contain both a webView and separator extension
        		LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1);
        		parent.addView(splitFrameLayout2, lp2);
        		
    			// add bible to framelayout
    			LayoutParams frameLayoutParamsBibleWebView2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    			splitFrameLayout2.addView(bibleWebView2, frameLayoutParamsBibleWebView2);
    			
    			// add separator handle touch delegate to framelayout
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, CommonUtils.convertDipsToPx(10), Gravity.TOP);
    			splitFrameLayout2.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate2);

    			// separator will adjust layouts when dragged
    			separator.setView1LayoutParams(lp);
    			separator.setView2LayoutParams(lp2);

    			mainActivity.registerForContextMenu(bibleWebView2);
    		}
    		mainActivity.registerForContextMenu(bibleWebView);
    	}
	}

	public void removeWebView(ViewGroup parent) {
		if (splitFrameLayout1!=null) {
			splitFrameLayout1.removeAllViews();
		}
		if (splitFrameLayout2!=null) {
			splitFrameLayout2.removeAllViews();
		}
		if (parent!=null) {
			parent.removeAllViews();
		}		
	}
	
	public DocumentView getView() {
		if (ControlFactory.getInstance().getSplitScreenControl().isFirstScreenActive()) {
			return bibleWebView;
		} else {
			return bibleWebView2;
		}
	}

	private class Separator extends View {

		// offset absolute points from top of layout to enable correct calculation of screen weights in layout
		private float parentTopPx;

		// the offset of the touch from th centre of the separator - to prevent initial jerk of separator to touch point
		private int touchOffsetPx;
		
		private LinearLayout.LayoutParams view1LayoutParams;
		private LinearLayout.LayoutParams view2LayoutParams;

		private TouchDelegateView touchDelegateView1;
		private TouchDelegateView touchDelegateView2;
		
		private static final int SEPARATOR_COLOUR = 0xFF6B6B6B;
		private static final int SEPARATOR_DRAG_COLOUR = Color.GREEN;

		private static final String TAG = "Separator";
		
		public Separator(Context context) {
			super(context);
	        setBackgroundColor(SEPARATOR_COLOUR);
	        touchDelegateView1 = new TouchDelegateView(context, this);
	        touchDelegateView2 = new TouchDelegateView(context, this);
		}
		
		/** Must use rawY below because this view is moving and getY would give the position relative to a moving component.
		 * 
		 */
		@Override
		public boolean onTouchEvent(MotionEvent event) {
		    switch (event.getAction() & MotionEvent.ACTION_MASK)   
		    {
		    case MotionEvent.ACTION_DOWN:
		    	Log.d(TAG, " y:"+event.getRawY());
		    	int[] rawParentLocation = new int[2]; 
		    	parentLayout.getLocationOnScreen(rawParentLocation);
		    	parentTopPx = rawParentLocation[1];
		        setBackgroundColor(SEPARATOR_DRAG_COLOUR);
		        touchOffsetPx = (int)event.getRawY()-getCentreY();
		        break;
		    case MotionEvent.ACTION_UP:
		    case MotionEvent.ACTION_POINTER_UP:
		    	Log.d(TAG, "Up x:"+event.getX()+" y:"+event.getY());
		        setBackgroundColor(SEPARATOR_COLOUR);
		        break;
		    case MotionEvent.ACTION_MOVE:
		    	int parentHeightPx = getParentHeightPx();
		    	Log.d(TAG, "container top Y:"+parentTopPx+" raw y:"+event.getRawY()+" offset y:"+(event.getRawY()-parentTopPx)+" parent height px:"+parentHeightPx);
		    	// calculate y offset in pixels from top of parent layout
		    	float y = event.getRawY()-parentTopPx-touchOffsetPx;
		    	
		    	// change the weights of both bible views to effectively move the separator
		    	view1LayoutParams.weight = y/parentHeightPx;
		    	view2LayoutParams.weight = 1-(y/parentHeightPx);
		    	parentLayout.requestLayout();
		        break;
		    }   

			return true; //super.onTouchEvent(event);
		}

		private int getParentHeightPx() {
			return parentLayout.getHeight();
		}

		private int getCentreY() {
			return +(int)parentTopPx + ((getTop()+getBottom())/2);
		}
		public void setView1LayoutParams(LinearLayout.LayoutParams view1LayoutParams) {
			this.view1LayoutParams = view1LayoutParams;
		}

		public void setView2LayoutParams(LinearLayout.LayoutParams view2LayoutParams) {
			this.view2LayoutParams = view2LayoutParams;
		}

		public TouchDelegateView getTouchDelegateView1() {
			return touchDelegateView1;
		}
		public TouchDelegateView getTouchDelegateView2() {
			return touchDelegateView2;
		}
	}
}
