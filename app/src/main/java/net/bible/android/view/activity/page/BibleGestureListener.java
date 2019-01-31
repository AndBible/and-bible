/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BibleGestureListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	// TODO: final int swipeMinDistance = vc.getScaledTouchSlop();
	// TODO: and other suggestions in http://stackoverflow.com/questions/937313/android-basic-gesture-detection
	private static final int DISTANCE_DIP = 40;
	private static final int SCROLL_DIP = 56; // should be at least toolbar height
	private int scaledMinimumDistance;
	private int scaledMinimumFullScreenScrollDistance;

	private int minScaledVelocity;
	private MainBibleActivity mainBibleActivity;

	public void setDisableSingleTapOnce(boolean disableSingleTapOnce) {
		this.disableSingleTapOnce = disableSingleTapOnce;
	}

	private boolean disableSingleTapOnce = false;

	public void setVerseSelectionMode(boolean verseSelectionMode) {
		this.verseSelectionMode = verseSelectionMode;
		if(!verseSelectionMode){
			disableSingleTapOnce = true;
		}
	}

	private boolean verseSelectionMode = false;
	
	private static final String TAG = "BibleGestureListener";
	
	public BibleGestureListener(MainBibleActivity mainBibleActivity) {
		super();
		this.mainBibleActivity = mainBibleActivity;
		scaledMinimumDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP);
		scaledMinimumFullScreenScrollDistance = CommonUtils.convertDipsToPx(SCROLL_DIP);
    	minScaledVelocity = ViewConfiguration.get(mainBibleActivity).getScaledMinimumFlingVelocity();
    	// make it easier to swipe
    	minScaledVelocity = (int)(minScaledVelocity*0.66);
		ABEventBus.getDefault().register(this);
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		// prevent interference with window separator drag - fast drags were causing a fling
		if (!TouchOwner.getInstance().isTouchOwned()) {
			// avoid NPE on Samsung devices
			if (e1!=null && e2!=null) {
				// get distance between points of the fling
				double vertical = Math.abs(e1.getY() - e2.getY());
				double horizontal = Math.abs(e1.getX() - e2.getX());

				Log.d(TAG, "onFling vertical:" + vertical + " horizontal:" + horizontal + " VelocityX" + velocityX);

				// test vertical distance, make sure it's a swipe
				if (vertical > scaledMinimumDistance) {
					return false;
				}
				// test horizontal distance and velocity
				else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
					// right to left swipe - sometimes velocity seems to have wrong sign so use raw positions to determine direction
					if (e1.getX() > e2.getX()) {
						mainBibleActivity.next();
					}
					// left to right swipe
					else {
						mainBibleActivity.previous();
					}
					return true;
				}
			}
		}
		return false;
	}

	public void onEvent(CurrentWindowChangedEvent event) {
		disableSingleTapOnce = true;
	}

	private MotionEvent ev = null;
	private boolean lastDirection = false;

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if(ev == null || e1.getEventTime() > ev.getEventTime()) {
			// New scroll event
			ABEventBus.getDefault().post(new BibleView.BibleViewTouched());
			ev = MotionEvent.obtain(e1);
		}
		if(e2.getEventTime() - ev.getEventTime() > 1000 ) {
			// Too slow motion
			ev = MotionEvent.obtain(e2);
		}

		boolean direction = distanceY > 0;
		if(lastDirection != direction) {
			ev = MotionEvent.obtain(e2);
			lastDirection = direction;
		}

		float dist = e2.getY()-ev.getY();
		if(!mainBibleActivity.getFullScreen() && dist < -scaledMinimumFullScreenScrollDistance) {
			mainBibleActivity.setFullScreen(true);
			ev = MotionEvent.obtain(e2);
		}
		if(mainBibleActivity.getFullScreen() && dist > scaledMinimumFullScreenScrollDistance) {
			mainBibleActivity.setFullScreen(false);
			ev = MotionEvent.obtain(e2);
		}
		return false;
	}


	@Override
	public boolean onDoubleTap(MotionEvent e) {
		mainBibleActivity.toggleFullScreen();
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		if (verseSelectionMode) {
			return false;
		}
		if(mainBibleActivity.getFullScreen()) {
			mainBibleActivity.toggleFullScreen();
			return true;
		}
		return false;
	}
}
