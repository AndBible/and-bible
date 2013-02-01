package net.bible.android.view.activity.page;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.service.common.CommonUtils;

import android.app.Activity;
import android.content.Context;
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
	private SeparatorHandle separatorLineHandle;
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

	private ViewGroup bottomSplitFrameLayout;
	private ViewGroup parentLayout;
	private Activity mainActivity;

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
        bibleWebView = new BibleView(this.mainActivity, Screen.SCREEN_1);
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);

        bibleWebView2 = new BibleView(this.mainActivity, Screen.SCREEN_2);
        bibleWebView2.setId(BIBLE_WEB_VIEW2_ID);
        
        separatorLineHandle = new SeparatorHandle(this.mainActivity);
        
        bottomSplitFrameLayout = new FrameLayout(this.mainActivity);

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
			parent.addView(bibleWebView, lp);
    		mainActivity.registerForContextMenu(bibleWebView);

    		if (splitScreenControl.isSplit()) {
    			// line dividing the split screens
    			View separatorLine = new View(this.mainActivity);
    			separatorLine.setBackgroundColor(0xFF6B6B6B);
    			parent.addView(separatorLine, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, (int) BibleApplication.getApplication().getResources().getDimension(R.dimen.split_screen_separator_width), 0));

    			// add a FrameLayout to the lower part of the LinearLayout to contain both the webView and separator handle overlaid
        		LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1);
        		parent.addView(bottomSplitFrameLayout, lp2);
        		
    			// add bible to framelayout
    			LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    			bottomSplitFrameLayout.addView(bibleWebView2, frameLayoutParamsBibleWebView);
    			
    			// add separator handle to framelayout
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorHandle = new FrameLayout.LayoutParams(CommonUtils.convertDipsToPx(40), CommonUtils.convertDipsToPx(20), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    			bottomSplitFrameLayout.addView(separatorLineHandle, frameLayoutParamsSeparatorHandle);

    			separatorLineHandle.setView1LayoutParams(lp);
    			separatorLineHandle.setView2LayoutParams(lp2);

    			mainActivity.registerForContextMenu(bibleWebView2);
    		}
    	}
	}

	public void removeWebView(ViewGroup parent) {
		if (bottomSplitFrameLayout!=null) {
			bottomSplitFrameLayout.removeAllViews();
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
	

	//TODO USE TouchDelegate TO EXTEND TOUCH AREA
	// SEE http://stackoverflow.com/questions/9667870/how-to-extend-a-views-touch-area
	private class SeparatorHandle extends View {

//		private int screenHeightPx;
		private LinearLayout.LayoutParams view1LayoutParams;
		private LinearLayout.LayoutParams view2LayoutParams;
		
		private static final String TAG = "Separator";
		
		public SeparatorHandle(Context context) {
			super(context);
	        setBackgroundResource(R.drawable.separator_handle_horizontal);
		}
		
		private float containerTopY;
		
		private boolean dragging = false;

		/** Must use rawY below because this view is moving and getY would give the position relative to a moving component.
		 * 
		 */
		@Override
		public boolean onTouchEvent(MotionEvent event) {
		    switch (event.getAction() & MotionEvent.ACTION_MASK)   
		    {
		    case MotionEvent.ACTION_DOWN:
		    	Log.d(TAG, "Down x:"+event.getX()+" y:"+event.getY());
		    	containerTopY = parentLayout.getTop();
		    	getBackground().setState(View.PRESSED_ENABLED_STATE_SET);
		        //setBackgroundColor(0xFF6B6B6B);
		        dragging = true;
		        break;
		    case MotionEvent.ACTION_POINTER_DOWN:   // first and second finger down
		    	Log.d(TAG, "Pointer Down x:"+event.getX()+" y:"+event.getY());
		        break;
		    case MotionEvent.ACTION_UP: // first finger lifted
		    	Log.d(TAG, "Up x:"+event.getX()+" y:"+event.getY());
		    	getBackground().setState(View.ENABLED_STATE_SET);
		    	break;
		    case MotionEvent.ACTION_POINTER_UP:   // second finger lifted
		    	Log.d(TAG, "Pointer Up x:"+event.getX()+" y:"+event.getY());
		        //setBackgroundColor(0x556B6B6B);
		    	getBackground().setState(View.ENABLED_STATE_SET);
		    	dragging = false;
		        break;
		    case MotionEvent.ACTION_MOVE:
		    	int screenHeightPx = getScreenHeightPx();
		    	Log.d(TAG, "container top Y:"+containerTopY+" y:"+event.getRawY()+" offset y:"+(event.getRawY()-containerTopY)+" screen height px:"+screenHeightPx);
		    	float y = event.getRawY()-containerTopY;
		    	Log.d(TAG, "weight 1:"+y/screenHeightPx+" weight 2:"+(1-(y/screenHeightPx)));
		    	view1LayoutParams.weight = y/screenHeightPx;
		    	view2LayoutParams.weight = 1-(y/screenHeightPx);
		    	parentLayout.requestLayout();
		        break;
		    }   

			return true; //super.onTouchEvent(event);
		}

		private int getScreenHeightPx() {
//			separatorLine.setScreenHeightPx(CommonUtils.convertDipsToPx(ScreenSettings.getContentViewHeightDips()));
			return parentLayout.getHeight()+(int)containerTopY;
		}
//		private void expandTouchArea() {
//			final View delegate = Separator.this;
//			// post a runnable to the parent view's message queue so its run
//			// after the view is drawn
//			parentLayout.post(new Runnable() {
//				@Override
//				public void run() {
//					Rect delegateArea = new Rect();
//					getHitRect(delegateArea);
//					delegateArea.top -= 200;
////					delegateArea.bottom += 100;
//					TouchDelegate expandedArea = new TouchDelegate(delegateArea, delegate);
//					// give the delegate to an ancestor of the view we're delegating the area to
//					bibleWebView.setTouchDelegate(expandedArea);
//				}
//			});
//		}

		public void setView1LayoutParams(LinearLayout.LayoutParams view1LayoutParams) {
			this.view1LayoutParams = view1LayoutParams;
		}

		public void setView2LayoutParams(LinearLayout.LayoutParams view2LayoutParams) {
			this.view2LayoutParams = view2LayoutParams;
		}
	}
	
	
}
