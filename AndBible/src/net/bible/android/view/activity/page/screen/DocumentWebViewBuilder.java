package net.bible.android.view.activity.page.screen;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.BibleView;
import net.bible.android.view.util.TouchDelegateView;
import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
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
	
	private static SplitScreenControl splitScreenControl;

	private ViewGroup parentLayout;
	private ViewGroup splitFrameLayout1;
	private ViewGroup splitFrameLayout2;
	private boolean isLaidOutForPortrait;
	private Button minimiseScreen2Button;
	private Button restoreScreen2Button;
	private Activity mainActivity;
	
	private int SPLIT_SEPARATOR_WIDTH_PX;
	private int SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX;
	private int SPLIT_BUTTON_TEXT_COLOUR;
	private int SPLIT_BUTTON_BACKGROUND_COLOUR;

	private static final String TAG="DocumentWebViewBuilder";

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
		splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
		
        bibleWebView = new BibleView(this.mainActivity, Screen.SCREEN_1);
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);

        bibleWebView2 = new BibleView(this.mainActivity, Screen.SCREEN_2);
        bibleWebView2.setId(BIBLE_WEB_VIEW2_ID);
        
        Resources res = BibleApplication.getApplication().getResources();
        SPLIT_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_width);
        SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_touch_expansion_width);
        SPLIT_BUTTON_TEXT_COLOUR = res.getColor(R.color.split_button_text_colour);
        SPLIT_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.split_button_background_colour);
        
        separator = new Separator(this.mainActivity, SPLIT_SEPARATOR_WIDTH_PX);
        
        splitFrameLayout1 = new FrameLayout(this.mainActivity);
        splitFrameLayout2 = new FrameLayout(this.mainActivity);

        // minimise button
        minimiseScreen2Button = createTextButton("__", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.minimiseScreen2();				
			}
		});

        // restore button
        restoreScreen2Button = createTextButton("\u2588\u2588", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.restoreScreen2();				
			}
		});
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(LinearLayout parent) {
		this.parentLayout = parent;
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;
    	boolean isAlreadySplitWebView = isWebView && parent.findViewById(BIBLE_WEB_VIEW2_ID)!=null;
    	boolean isPortrait = CommonUtils.isPortrait();

    	if (!isWebView || isAlreadySplitWebView!=splitScreenControl.isSplit() || isPortrait!=isLaidOutForPortrait) {
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeWebView(parent);
    		
    		parent.setOrientation(isPortrait? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    		separator.setPortrait(isPortrait);
    		
    		LinearLayout.LayoutParams lp = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1) :
    													new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, 1);

			// add top view whether split or not
			//AddTop FrameLayout, then webview, [then separatorTouchExtender(beside separator)]
			// add a FrameLayout
    		parent.addView(splitFrameLayout1, lp);

			// add bible to framelayout
			LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
			splitFrameLayout1.addView(bibleWebView, frameLayoutParamsBibleWebView);

    		if (splitScreenControl.isSplit()) {

    			// add separator touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait? 	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM) :
    																						new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.RIGHT);
    			splitFrameLayout1.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);
    			
    			// line dividing the split screens
    			parent.addView(separator, isPortrait ? 	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_WIDTH_PX, 0) :
    													new LinearLayout.LayoutParams(SPLIT_SEPARATOR_WIDTH_PX, LayoutParams.FILL_PARENT, 0));
    			
    			// add a FrameLayout to the lower part of the LinearLayout to contain both a webView and separator extension
        		LinearLayout.LayoutParams lp2 = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1) :
        													new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, 1);
        		parent.addView(splitFrameLayout2, lp2);
        		
    			// add bible to framelayout
//    			LayoutParams frameLayoutParamsBibleWebView2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    			splitFrameLayout2.addView(bibleWebView2, frameLayoutParamsBibleWebView);
    			
    			// add separator handle touch delegate to framelayout
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate2 = isPortrait ?	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP) :
    																						new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.LEFT);
    			splitFrameLayout2.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate2);
    			splitFrameLayout2.addView(minimiseScreen2Button, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP|Gravity.RIGHT));

    			// separator will adjust layouts when dragged
    			separator.setView1LayoutParams(lp);
    			separator.setView2LayoutParams(lp2);

    			mainActivity.registerForContextMenu(bibleWebView2);
    		} else if (splitScreenControl.isScreen2Minimized()) {
    			Log.d(TAG,  "Show restore button");
    			splitFrameLayout1.addView(restoreScreen2Button, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM|Gravity.RIGHT));
    		}
    		
    		mainActivity.registerForContextMenu(bibleWebView);
    		isLaidOutForPortrait = isPortrait;
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
	
	private Button createTextButton(String text, OnClickListener onClickListener) {
		Button button = new Button(this.mainActivity);
		int buttonSize = BibleApplication.getApplication().getResources().getDimensionPixelSize(R.dimen.minimise_restore_button_size);
        button.setText(text);
        button.setWidth(buttonSize);
        button.setHeight(buttonSize);
        button.setBackgroundColor(SPLIT_BUTTON_BACKGROUND_COLOUR);
        button.setTextColor(SPLIT_BUTTON_TEXT_COLOUR);
        button.setTypeface(null, Typeface.BOLD);
        button.setSingleLine(true);
        button.setOnClickListener(onClickListener);
        return button;
	}
	
	private class Separator extends View {

		// offset absolute points from top of layout to enable correct calculation of screen weights in layout
		private float parentStartRawPx;
		
		private boolean isPortrait = true;

		// the offset of the touch from th centre of the separator - to prevent initial jerk of separator to touch point
		private int touchOffsetPx;
		
		private LinearLayout.LayoutParams view1LayoutParams;
		private LinearLayout.LayoutParams view2LayoutParams;

		private TouchDelegateView touchDelegateView1;
		private TouchDelegateView touchDelegateView2;

		private int SEPARATOR_WIDTH;
		private int SEPARATOR_COLOUR;
		private int SEPARATOR_DRAG_COLOUR;
		
		private TouchOwner touchOwner = TouchOwner.getInstance();

		private static final String TAG = "Separator";
		
		public Separator(Context context, int width) {
			super(context);
			Resources res = BibleApplication.getApplication().getResources();
			SEPARATOR_COLOUR = res.getColor(R.color.split_separator_colour);
			SEPARATOR_DRAG_COLOUR = res.getColor(R.color.split_separator_drag_colour);
	        setBackgroundColor(SEPARATOR_COLOUR);
	        touchDelegateView1 = new TouchDelegateView(context, this);
	        touchDelegateView2 = new TouchDelegateView(context, this);
	        SEPARATOR_WIDTH = width;
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
		    	touchOwner.setTouchOwner(this);
		    	splitScreenControl.setSeparatorMoving(true);
		    	int[] rawParentLocation = new int[2]; 
		    	parentLayout.getLocationOnScreen(rawParentLocation);
		    	parentStartRawPx = isPortrait? rawParentLocation[1] : rawParentLocation[0];
		        setBackgroundColor(SEPARATOR_DRAG_COLOUR);
		        touchOffsetPx = isPortrait?	(int)event.getRawY()-getCentreY() : 
		        							(int)event.getRawX()-getCentreX();
		        break;
		    case MotionEvent.ACTION_UP:
		    case MotionEvent.ACTION_POINTER_UP:
		    	Log.d(TAG, "Up x:"+event.getX()+" y:"+event.getY());
		    	touchOwner.releaseOwnership(this);
		        setBackgroundColor(SEPARATOR_COLOUR);
		    	splitScreenControl.setSeparatorMoving(false);
		        break;
		    case MotionEvent.ACTION_MOVE:
		    	int parentDimensionPx = getParentDimensionPx();
		    	Log.d(TAG, "container top Y:"+parentStartRawPx+" raw y:"+event.getRawY()+" offset y:"+(event.getRawY()-parentStartRawPx)+" parent height px:"+parentDimensionPx);
		    	// calculate y offset in pixels from top of parent layout
		    	float offsetFromEdgePx = (isPortrait? event.getRawY() : event.getRawX())
		    								-parentStartRawPx-touchOffsetPx;
		    	
		    	// change the weights of both bible views to effectively move the separator
		    	// min prevents the separator going off screen at the bottom
				float separatorPercentOfScreen = SEPARATOR_WIDTH/getParentDimensionPx();
		    	view1LayoutParams.weight = Math.min(offsetFromEdgePx/parentDimensionPx, 1-separatorPercentOfScreen);
		    	view2LayoutParams.weight = 1-view1LayoutParams.weight;
		    	parentLayout.requestLayout();
		        break;
		    }   

			return true; //super.onTouchEvent(event);
		}

		private int getParentDimensionPx() {
			return isPortrait? parentLayout.getHeight() : parentLayout.getWidth();
		}

		private int getCentreY() {
			return +(int)parentStartRawPx + ((getTop()+getBottom())/2);
		}
		private int getCentreX() {
			return +(int)parentStartRawPx + ((getLeft()+getRight())/2);
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

		public void setPortrait(boolean isPortrait) {
			this.isPortrait = isPortrait;
		}
	}
}
