package net.bible.android.view.util.buttongrid;

import android.util.Log;

import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** Calculate the number of columns and rows to be used to layout a grid of bible books, numbers, or whatever
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class LayoutDesigner {

    private static int MIN_COLS = 5;
    private static int MIN_COLS_LAND = 8;

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
        return CommonUtils.isPortrait();
    }
}
