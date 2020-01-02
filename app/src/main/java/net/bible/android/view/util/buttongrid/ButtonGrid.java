/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.util.buttongrid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.view.util.buttongrid.LayoutDesigner.RowColLayout;

import java.util.List;

/** Show a grid of buttons to allow selection for navigation
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ButtonGrid extends TableLayout {

	public static class ButtonInfo {
		public int id;
		public String name;
		public int textColor = Color.WHITE;
		public boolean highlight = false;

		// used internally 
		private Button button;
		private int top;
		private int bottom;
		private int left;
		private int right;
		
		private int rowNo;
		private int colNo;
	}

	private ButtonInfo mCurrentPreview;
	private TextView mPreviewText;
	private PopupWindow mPreviewPopup;
	private int mPreviewOffset;
	private int mPreviewHeight;
	private static final int PREVIEW_HEIGHT_DIP = 70;
	
	private OnButtonGridActionListener onButtonGridActionListener;

	private List<ButtonInfo> buttonInfoList;
	private RowColLayout mRowColLayout;
	
	private ButtonInfo mPressed;
	private Context mContext;
	private boolean isInitialised = false;


	private static final String TAG = "ButtonGrid";

	public ButtonGrid(Context context) {
		this(context, null, 0);
	}

	public ButtonGrid(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		this.mContext = context;

		// use generic ViewGroup LayoutParams for Table because we don't know what the parent is
		setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		setStretchAllColumns(true);
	}

	public void clear() {
		removeAllViews();
		buttonInfoList = null;
		mRowColLayout = null;
		mPressed = null;
		isInitialised = false;
	}

	/** Called during initialisation to add the list of buttons to be laid out on the screen
	 *
	 * @param buttonInfoList
	 */
	public void addButtons(List<ButtonInfo> buttonInfoList) {
		this.buttonInfoList = buttonInfoList;
		int numButtons = buttonInfoList.size();
		int textSize = getResources().getInteger(R.integer.grid_cell_text_size_sp);

		// calculate the number of rows and columns so that the grid looks nice
		mRowColLayout = new LayoutDesigner(this).calculateLayout(buttonInfoList);
		
		LayoutParams rowInTableLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f);
		TableRow.LayoutParams cellInRowLp = new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f);

		for (int iRow=0; iRow<mRowColLayout.rows; iRow++) {
			TableRow row = new TableRow(mContext);
			addView(row, rowInTableLp);

			for (int iCol=0; iCol<mRowColLayout.cols; iCol++) {
				int buttonInfoIndex = getButtonInfoIndex(iRow, iCol);
				if (buttonInfoIndex<numButtons) {
					// create a graphical Button View object to show on the screen and link it to the ButtonInfo object
					ButtonInfo buttonInfo = buttonInfoList.get(buttonInfoIndex);
					Button button = new Button(mContext);
					button.setText(buttonInfo.name);
					button.setBackgroundResource(R.drawable.buttongrid_button_background);
					button.setTextColor(buttonInfo.textColor);
					if (buttonInfo.highlight) {
						button.setTypeface(Typeface.DEFAULT_BOLD);
						button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize+1);
						button.setPaintFlags(button.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
					} else {
						button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
					}
					// set pad to 0 prevents text being pushed off the bottom of buttons on small screens
					button.setPadding(0, 0, 0, 0);

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						button.setAllCaps(false);
					}

					buttonInfo.button = button;
					buttonInfo.rowNo = iRow;
					buttonInfo.colNo = iCol;
					
					row.addView(button, cellInRowLp);
				} else {
					TextView spacer = new TextView(mContext);
					row.addView(spacer, cellInRowLp);
				}
			}
		}

		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		mPreviewText = (TextView)inflater.inflate(R.layout.buttongrid_button_preview, null);
		mPreviewPopup = new PopupWindow(mPreviewText);
		mPreviewPopup.setContentView(mPreviewText);
		mPreviewPopup.setBackgroundDrawable(null);
		mPreviewPopup.setTouchable(false);
		mPreviewText.setCompoundDrawables(null, null, null, null);

		float scale = mContext.getResources().getDisplayMetrics().density;
		mPreviewHeight = (int)(PREVIEW_HEIGHT_DIP*scale);
	}

	/** Ensure longer runs by populating in longest direction ie columns if portrait and rows if landscape
	 *
	 * @param row
	 * @param col
	 * @return
	 */
	private int getButtonInfoIndex(int row, int col) {
		if (mRowColLayout.columnOrder) {
			return col*mRowColLayout.rows + row;
		} else {
			return row*mRowColLayout.cols + col;
		}
	}

	/* (non-Javadoc)
	 * @see android.view.ViewGroup#onInterceptTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		
		// wait until the columns have been layed out and adjusted before recording button positions
		if (!isInitialised) {
			synchronized (buttonInfoList) {
				if (!isInitialised) {
					recordButtonPositions();
					isInitialised = true;
				}
			}
		}
		
		/*
		 * This method JUST determines whether we want to intercept the motion.
		 * If we return true, onMotionEvent will be called and we do the actual
		 * scrolling there.
		 */
		return true;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN :
		case MotionEvent.ACTION_MOVE :
			ButtonInfo but = findButton((int)event.getX(), (int)event.getY());
			if (but!=null) {
				// show the button being pressed
				if (!but.equals(mPressed)) {
					if (mPressed!=null) {
						mPressed.button.setPressed(false);
					}
					but.button.setPressed(true);
					mPressed = but;
					showPreview(but);
				}
			}
			break;
		case MotionEvent.ACTION_UP :
			if (mPressed!=null) {
				buttonSelected(mPressed);
			}
			break;
		}

		return true; //super.onInterceptTouchEvent(ev);
	}

	private ButtonInfo findButton(int x, int y) {
		for (ButtonInfo but : buttonInfoList) {
			if (isInside(but, x, y)) {
				return but;
			}
		}
		return null;
	}

	private void buttonSelected(ButtonInfo selectedButton) {
		Log.i(TAG, "Selected:"+selectedButton.name);
		if (onButtonGridActionListener!=null) {
			onButtonGridActionListener.buttonPressed(selectedButton);
		}

		close();
	}
	
	private boolean isInside(ButtonInfo but, float x, float y) {
		return (but.top<y && but.bottom>y &&
			but.left<x && but.right>x);
	}

	private void showPreview(ButtonInfo buttonInfo) {
		try {
			if (!buttonInfo.equals(mCurrentPreview)) {
				Log.d(TAG, "Previewing "+buttonInfo.name);
				mCurrentPreview = buttonInfo;
				mPreviewText.setText(buttonInfo.name);
	
				int popupHeight = mPreviewHeight;
				mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), buttonInfo.button.getWidth() + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
	
				ViewGroup.LayoutParams lp = mPreviewText.getLayoutParams();
				if (lp != null) {
					lp.width = popupWidth;
					lp.height = popupHeight;
				}
	
				// where to place the popup
				int popupPreviewX;
				int popupPreviewY;
				if (buttonInfo.rowNo<2) {
					int horizontalOffset = (2*buttonInfo.button.getWidth());
					// if in top 2 rows then show off to right/left to avoid popup going off the screen
					if (buttonInfo.colNo<mRowColLayout.cols/2.0) {
						// key is on left so show to right of key
						popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft() + horizontalOffset;
					} else {
						// key is on right so show to right of key
						popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft() - horizontalOffset;
					}
					popupPreviewY = buttonInfo.bottom;
				} else {
					// show above the key above the one currently pressed
					popupPreviewX = buttonInfo.left - mPreviewText.getPaddingLeft();
					popupPreviewY = buttonInfo.top /*- popupHeight*/+ mPreviewOffset;
				}
				
				if (mPreviewPopup.isShowing()) {
					mPreviewPopup.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight);
				} else {
					mPreviewPopup.setWidth(popupWidth);
					mPreviewPopup.setHeight(popupHeight);
					mPreviewPopup.showAtLocation(this, Gravity.NO_GRAVITY, popupPreviewX, popupPreviewY);
				}
				mPreviewText.setVisibility(VISIBLE);
			} else {
				// could be returning to this view via Back or Finish and the user represses same button 
				if (mPreviewText.getVisibility()!=VISIBLE) { 
					mPreviewText.setVisibility(VISIBLE);
				}
			}
		} catch (Exception e) {
			// avoid very occasional NPE deep in Android code by catching and ignoring because showing preview is optional
			Log.w(TAG, "Error showing button grid preview", e);
		}
	}
	
	@Override
	protected void onDetachedFromWindow() {
		close();

		super.onDetachedFromWindow();
	}

	private void close() {
		// avoid errors on Lenovo tablet in dismiss
		try {
			if (mPreviewPopup.isShowing()) {
				mPreviewPopup.dismiss();
			}
		} catch (Exception e) {
			Log.w(TAG, "Error closing ButtonGrid preview");
		}
	}

	/** calculate button position relative to this table because MotionEvents are relative to this table
	 */
	private void recordButtonPositions() {
		for (ButtonInfo buttonInfo : buttonInfoList) {
			
			// get position of button within row
			Button button = buttonInfo.button;
			TableRow tableRow = (TableRow)button.getParent();
			
			buttonInfo.left += button.getLeft()+tableRow.getLeft();
			buttonInfo.top += button.getTop()+tableRow.getTop();
			buttonInfo.right += button.getRight()+tableRow.getLeft();
			buttonInfo.bottom += button.getBottom()+tableRow.getTop();
		}
		
		// calculate offset of 2 button heights so users can see the buttons surrounding the current button pressed
		if (buttonInfoList.size()>0) {
			ButtonInfo but1 = buttonInfoList.get(0);
			mPreviewOffset = but1.top - but1.bottom;
		}
	}
	/**
	 * @param onButtonGridActionListener the onButtonGridActionListener to set
	 */
	public void setOnButtonGridActionListener(
			OnButtonGridActionListener onButtonGridActionListener) {
		this.onButtonGridActionListener = onButtonGridActionListener;
	}
}
