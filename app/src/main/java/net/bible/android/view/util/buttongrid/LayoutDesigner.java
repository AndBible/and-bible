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

import android.content.res.Configuration;
import android.util.Log;
import android.view.View;

import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** Calculate the number of columns and rows to be used to layout a grid of bible books, numbers, or whatever
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class LayoutDesigner {

	private static int MIN_COLS = 5;
	private static int MIN_COLS_LAND = 8;
	private final View view;

	public LayoutDesigner(View view) {
		this.view = view;
	}

	static class RowColLayout {
		int rows;
		int cols;
		
		/** column order if portrait mode to provide longer 'runs' */ 
		boolean columnOrder;
	}

	private static final String TAG = "LayoutDesigner";
	
	private static RowColLayout BIBLE_BOOK_LAYOUT = new RowColLayout();
	private static RowColLayout BIBLE_BOOK_LAYOUT_LAND = new RowColLayout();
	static {
		BIBLE_BOOK_LAYOUT.rows = 11;
		BIBLE_BOOK_LAYOUT.cols = 6;

		BIBLE_BOOK_LAYOUT_LAND.rows = 6;
		BIBLE_BOOK_LAYOUT_LAND.cols = 11;
	}
	
	RowColLayout calculateLayout(List<ButtonInfo> buttonInfoList) {
		RowColLayout rowColLayout = new RowColLayout();
		int numButtons = buttonInfoList.size();
		
		// is it the list of bible books
		if (buttonInfoList.size()==66 && !StringUtils.isNumeric(buttonInfoList.get(0).name)) {
			// bible books
			if (isPortrait()) {
				rowColLayout = BIBLE_BOOK_LAYOUT;
			} else {
				rowColLayout = BIBLE_BOOK_LAYOUT_LAND;
			}
		} else {
			// a list of chapters or verses
			if (numButtons<=50) {
				if (isPortrait()) {
					rowColLayout.rows = 10;
				} else {
					rowColLayout.rows = 5;
				}
			} else if (numButtons<=100){
				rowColLayout.rows = 10;
			} else {
				if (isPortrait()) {
					rowColLayout.rows = 15;
				} else {
					rowColLayout.rows = 10;
				}
			}
			rowColLayout.cols = (int)Math.ceil(((float)numButtons)/rowColLayout.rows);
			
			// if there are too few buttons/rows you just see a couple of large buttons on the screen so ensure there are enough rows to look nice 
			int minCols = isPortrait() ? MIN_COLS : MIN_COLS_LAND;
			rowColLayout.cols = Math.max(minCols, rowColLayout.cols);
		}

		rowColLayout.columnOrder = isPortrait();
		
		Log.d(TAG, "Rows:"+rowColLayout.rows+" Cols:"+rowColLayout.cols);
		return rowColLayout;		
	}
	

	private boolean isPortrait() {
		return view.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
	}
}
