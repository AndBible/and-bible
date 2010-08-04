package net.bible.android.view;

import net.bible.android.CurrentPassage;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.webkit.WebView;

/** The WebView component that shows teh main bible and commentary text
 * 
 * @author denha1m
 *
 */
public class BibleView extends WebView {
	
	private VerseCalculator verseCalculator;
	
	private static final String TAG = "BibleView";
	
	/**
     * Constructor.  This version is only needed if you will be instantiating
     * the object manually (not from a layout XML file).
     * @param context
     */
	public BibleView(Context context) {
		super(context);
		initialise();
	}

    /**
     * Construct object, initializing with any attributes we understand from a
     * layout file. These attributes are defined in
     * SDK/assets/res/any/classes.xml.
     * 
     * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
     */
	public BibleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialise();
	}
	public BibleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialise();		
	}
	
	private void initialise() {
		verseCalculator = new VerseCalculator();
	}
	
	private long lastHandledTrackballEventTime = 0;

	/** handle right/left trackpad movement by going next/prev page
	 */
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		Log.d(TAG, "onTrackBallEvent:"+event+" down:"+event.getDownTime()+" time:"+event.getEventTime());
		boolean isHandled = false;
		try {
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				// The relative movement of the trackball since the last event can be retrieve with MotionEvent.getX() and MotionEvent.getY(). These are normalized so that a movement of 1 corresponds to the user pressing one DPAD key (so they will often be fractional values, representing the more fine-grained movement information available from a trackball).
				float xChangeSinceLastEvent = event.getX();
				float yChangeSinceLastEvent = event.getY();
				boolean xMovement = Math.abs(xChangeSinceLastEvent) > Math.abs(yChangeSinceLastEvent);
				Log.d(TAG, "XMov:"+xMovement+" xch:"+xChangeSinceLastEvent+" ych:"+yChangeSinceLastEvent);
				if (xMovement) {
					Log.d(TAG, "is xmov");
					if (xChangeSinceLastEvent < -0.3) {
						Log.d(TAG, "<.3");
						if (event.getEventTime()-lastHandledTrackballEventTime>1000) {
							Log.i(TAG, "Move Back");
							CurrentPassage.getInstance().previous();
							lastHandledTrackballEventTime = event.getEventTime();
						} else {
							Log.i(TAG, "Already handled");
						}
						isHandled = true;
					} else if (xChangeSinceLastEvent > 0.3) {
						Log.d(TAG, ">.3");
						if (event.getEventTime()-lastHandledTrackballEventTime>1000) {
							Log.i(TAG, "Move Forward");
							CurrentPassage.getInstance().next();
							lastHandledTrackballEventTime = event.getEventTime();
						} else {
							Log.i(TAG, "Already handled");
						}
						isHandled = true;
					}
				}
			}
			if (!isHandled) {
				isHandled = super.onTrackballEvent(event);
				
				// trackballs that don't change page probably scroll page so update current verse 
				updateVerseCalculator();
			}
		} catch (Exception e) {
			Log.e(TAG, "Error changing page", e);
		}
		return isHandled;
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		Log.d(TAG, "scrollchanged:"+l+" "+t);
		super.onScrollChanged(l, t, oldl, oldt);
		updateVerseCalculator();
	}


	private void updateVerseCalculator() {
		int verticalscrollRange = computeVerticalScrollRange();

		Log.d(TAG, "updating verse calculator:"+verticalscrollRange+" height:"+getHeight());
		if (verticalscrollRange>0 && verseCalculator!=null) {
			Log.d(TAG, "setting vertical scroll range:"+verticalscrollRange);
			// the y posn never actually reaches the bottom of the total scroll range it stops one page height up from bottom so need to adjust for that
			int maxScrollRange = verticalscrollRange-getHeight();
			verseCalculator.setMaxScrollRange(maxScrollRange);
		}

		int y = getScrollY();
		verseCalculator.newPosition(y);
	}	
}
