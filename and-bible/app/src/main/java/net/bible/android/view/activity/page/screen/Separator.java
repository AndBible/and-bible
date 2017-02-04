package net.bible.android.view.activity.page.screen;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.util.TouchDelegateView;
import net.bible.android.view.util.TouchOwner;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Separator extends View {

	private View parentLayout;
	private Window window1;
	private Window window2;
	private int numWindows;
	
	// offset absolute points from top of layout to enable correct calculation of screen weights in layout
	private float parentStartRawPx;
	
	private boolean isPortrait = true;

	// the offset of the touch from the centre of the separator - to prevent initial jerk of separator to touch point
	private int startTouchPx;
	private float startWeight1 = 1.0f;
	private float startWeight2 = 1.0f;
	
	private int lastOffsetFromEdgePx;

	// try to prevent swamping of ui thread during splitter drag
	private long lastTouchMoveEvent;
	private final static int DRAG_TOUCH_MOVE_FREQUENCY_MILLIS = 200;
	
	private LinearLayout.LayoutParams view1LayoutParams;
	private LinearLayout.LayoutParams view2LayoutParams;

	private TouchDelegateView touchDelegateView1;
	private TouchDelegateView touchDelegateView2;

	private int SEPARATOR_WIDTH;
	private int SEPARATOR_COLOUR;
	private int SEPARATOR_DRAG_COLOUR;
	
	private TouchOwner touchOwner = TouchOwner.getInstance();
	private final WindowControl windowControl;

	private static final String TAG = "Separator";
	
	public Separator(Context context, int width, View parentLayout, Window window, Window nextWindow, int numWindows, boolean isPortrait, WindowControl windowControl) {
		super(context);
		this.windowControl = windowControl;

		Resources res = BibleApplication.getApplication().getResources();
		SEPARATOR_COLOUR = res.getColor(R.color.window_separator_colour);
		SEPARATOR_DRAG_COLOUR = res.getColor(R.color.window_separator_drag_colour);
        setBackgroundColor(SEPARATOR_COLOUR);

        touchDelegateView1 = new TouchDelegateView(context, this);
        touchDelegateView2 = new TouchDelegateView(context, this);
        SEPARATOR_WIDTH = width;
        this.parentLayout = parentLayout;
        this.window1 = window;
        this.window2 = nextWindow;
        this.numWindows = numWindows;
        this.isPortrait = isPortrait;
	}
	
	/** 
	 * Must use rawY below because this view is moving and getY would give the position relative to a moving component.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
	    switch (event.getAction() & MotionEvent.ACTION_MASK)   
	    {
	    case MotionEvent.ACTION_DOWN:
	    	Log.d(TAG, " y:"+event.getRawY());
	    	touchOwner.setTouchOwner(this);
	    	windowControl.setSeparatorMoving(true);
	        setBackgroundColor(SEPARATOR_DRAG_COLOUR);
	    	
	    	int[] rawParentLocation = new int[2]; 
	    	parentLayout.getLocationOnScreen(rawParentLocation);
	    	parentStartRawPx = isPortrait? rawParentLocation[1] : rawParentLocation[0];
	    	
	        startTouchPx = isPortrait? (int)event.getRawY() : (int)event.getRawX();
	        startWeight1 = view1LayoutParams.weight; //window1.getWeight();
	        startWeight2 = view2LayoutParams.weight; //window2.getWeight();
	        break;
	    case MotionEvent.ACTION_UP:
	    case MotionEvent.ACTION_POINTER_UP:
	    	Log.d(TAG, "Up x:"+event.getX()+" y:"+event.getY());
	        setBackgroundColor(SEPARATOR_COLOUR);
	    	window1.getWindowLayout().setWeight(view1LayoutParams.weight);
	    	window2.getWindowLayout().setWeight(view2LayoutParams.weight);
	    	windowControl.setSeparatorMoving(false);
	    	touchOwner.releaseOwnership(this);
	        break;
	    case MotionEvent.ACTION_MOVE:
	    	if (System.currentTimeMillis()>lastTouchMoveEvent+DRAG_TOUCH_MOVE_FREQUENCY_MILLIS) {
		    	Log.d(TAG, "Touch move accepted");
		    	int parentDimensionPx = getParentDimensionPx();

		    	// calculate y offset in pixels from top of parent layout
		    	float offsetFromEdgePx = (isPortrait? event.getRawY() : event.getRawX());
		    	
		    	// prevent going irretrievably off bottom or right edge
	    		offsetFromEdgePx = Math.min(offsetFromEdgePx, parentDimensionPx-SEPARATOR_WIDTH);
		    	
		    	// if position has moved at least one px then redraw separator
		    	if ((int)offsetFromEdgePx != lastOffsetFromEdgePx) {
		    		int changePx = (int)offsetFromEdgePx-startTouchPx;
		    		float aveScreenSize = getAveScreenSize();
		    		float variationPercent = changePx/aveScreenSize; 
		    		
			    	// change the weights of both bible views to effectively move the separator
			    	view1LayoutParams.weight = startWeight1+variationPercent;
			    	view2LayoutParams.weight = startWeight2-variationPercent;
			    	parentLayout.requestLayout();
			    	lastOffsetFromEdgePx = (int)offsetFromEdgePx;
		    	}
		    	lastTouchMoveEvent = System.currentTimeMillis();
		    	Log.d(TAG, "Touch move finished");
	    	}
	        break;
	    }   

		return true; //super.onTouchEvent(event);
	}

	private int getAveScreenSize() {
		return getParentDimensionPx()/numWindows;
	}
	
	private int getParentDimensionPx() {
		return isPortrait? parentLayout.getHeight() : parentLayout.getWidth();
	}

	private int getStartingOffsetY() {
		return +(int)parentStartRawPx + ((getTop()+getBottom())/2);
	}
	private int getStartingOffsetX() {
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
}
