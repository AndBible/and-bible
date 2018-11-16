package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleViewGestureListener extends SimpleOnGestureListener {

	private BibleView bibleView;

	private static final String TAG = "BibleGestureListener";

	public BibleViewGestureListener(BibleView bibleView) {
		super();
		this.bibleView = bibleView;
	}

//	/** WebView does not handle long presses automatically via onCreateContextMenu so do it here
//	 */
//	@Override
//	public void onLongPress(MotionEvent e) {
//		Log.d(TAG, "onLongPress");
//
//		bibleView.onLongPress(e.getX(), e.getY());
//	}
}
