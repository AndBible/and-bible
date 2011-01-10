package net.bible.android.view.util.buttongrid;

import java.util.List;

import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;
import android.util.Log;
import example.tablekeyboard.CommonUtils;

public class LayoutDesigner {

	private static int MIN_ROWS = 6;

	static class RowColLayout {
		int rows;
		int cols;
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
		
		if (buttonInfoList.get(0).name.startsWith("Gen")) {
			// bible books
			if (isPortrait()) {
				rowColLayout = BIBLE_BOOK_LAYOUT;
			} else {
				rowColLayout = BIBLE_BOOK_LAYOUT_LAND;
			}
		} else {
			// Numbers
			if (numButtons<=50) {
				if (isPortrait()) {
					rowColLayout.cols = 5;
				} else {
					rowColLayout.cols = 10;
				}
			} else if (numButtons<=100){
				rowColLayout.cols = 10;
			} else {
				if (isPortrait()) {
					rowColLayout.cols = 10;
				} else {
					rowColLayout.cols = 15;
				}
			}
			rowColLayout.rows = (int)Math.ceil(((float)numButtons)/rowColLayout.cols);
			rowColLayout.rows = Math.max(MIN_ROWS, rowColLayout.rows);
		}
		Log.d(TAG, "Rows:"+rowColLayout.rows+" Cols:"+rowColLayout.cols);
		return rowColLayout;		
	}
	

	private boolean isPortrait() {
		return CommonUtils.isPortrait();
	}
}
