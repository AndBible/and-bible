package net.bible.android.control.page.splitscreen;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.util.TouchDelegateView;
import net.bible.android.view.util.TouchOwner;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Separator extends View {

	private View parentLayout;
	private Screen screen1;
	private Screen screen2;
	private int numSplitScreens;
	
	private long aveScreenSize;
	
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
	private SplitScreenControl splitScreenControl;

	private static final String TAG = "Separator";
	
	public Separator(Context context, int width, View parentLayout, Screen screen, Screen nextScreen, int numSplitScreens, boolean isPortrait) {
		super(context);
		splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
		
		Resources res = BibleApplication.getApplication().getResources();
		SEPARATOR_COLOUR = res.getColor(R.color.split_separator_colour);
		SEPARATOR_DRAG_COLOUR = res.getColor(R.color.split_separator_drag_colour);
        setBackgroundColor(SEPARATOR_COLOUR);

        touchDelegateView1 = new TouchDelegateView(context, this);
        touchDelegateView2 = new TouchDelegateView(context, this);
        SEPARATOR_WIDTH = width;
        this.parentLayout = parentLayout;
        this.screen1 = screen;
        this.screen2 = nextScreen;
        this.numSplitScreens = numSplitScreens;
        this.isPortrait = isPortrait;
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
	        setBackgroundColor(SEPARATOR_DRAG_COLOUR);
	    	
	    	int[] rawParentLocation = new int[2]; 
	    	parentLayout.getLocationOnScreen(rawParentLocation);
	    	parentStartRawPx = isPortrait? rawParentLocation[1] : rawParentLocation[0];
	    	
	        startTouchPx = isPortrait? (int)event.getRawY() : (int)event.getRawX();
	        startWeight1 = view1LayoutParams.weight; //screen1.getWeight();
	        startWeight2 = view2LayoutParams.weight; //screen2.getWeight();
	        break;
	    case MotionEvent.ACTION_UP:
	    case MotionEvent.ACTION_POINTER_UP:
	    	Log.d(TAG, "Up x:"+event.getX()+" y:"+event.getY());
	        setBackgroundColor(SEPARATOR_COLOUR);
	    	screen1.setWeight(view1LayoutParams.weight);
	    	screen2.setWeight(view2LayoutParams.weight);
	    	splitScreenControl.setSeparatorMoving(false);
	    	touchOwner.releaseOwnership(this);
	        break;
	    case MotionEvent.ACTION_MOVE:
	    	if (System.currentTimeMillis()>lastTouchMoveEvent+DRAG_TOUCH_MOVE_FREQUENCY_MILLIS) {
		    	Log.d(TAG, "Touch move accepted");
		    	int parentDimensionPx = getParentDimensionPx();
		    	Log.d(TAG, "*** split parent dim:"+parentDimensionPx);
		    	// calculate y offset in pixels from top of parent layout
		    	float offsetFromEdgePx = (isPortrait? event.getRawY() : event.getRawX()) - parentStartRawPx;
		    	// if position has moved at least one px then redraw separator
		    	if ((int)offsetFromEdgePx != lastOffsetFromEdgePx) {
		    		int changePx = (int)offsetFromEdgePx-startTouchPx;
		    		float aveScreenSize = getAveScreenSize();
		    		float variationPercent = changePx/aveScreenSize; 
		    		
			    	// change the weights of both bible views to effectively move the separator
			    	// min prevents the separator going off screen at the bottom
//					float separatorPercentOfScreen = SEPARATOR_WIDTH/getParentDimensionPx();
			    	Log.d(TAG, "*** split parent dim:"+parentDimensionPx+" start px:"+startTouchPx+" offsetFromEdgepx:"+offsetFromEdgePx+" spe width:"+SEPARATOR_WIDTH+" var perc of screen:"+variationPercent);
			    	view1LayoutParams.weight = startWeight1+variationPercent;//Math.min(offsetFromEdgePx/parentDimensionPx, 1-separatorPercentOfScreen);
			    	view2LayoutParams.weight = startWeight2-variationPercent;
			    	Log.d(TAG, "split request layout weight 1:"+view1LayoutParams.weight+" weight 2:"+view2LayoutParams.weight+" offset:"+offsetFromEdgePx);
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
		return getParentDimensionPx()/numSplitScreens;
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
}
