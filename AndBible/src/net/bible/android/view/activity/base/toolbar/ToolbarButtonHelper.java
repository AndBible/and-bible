package net.bible.android.view.activity.base.toolbar;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.widget.Button;

public class ToolbarButtonHelper {
	
	void updateQuickButton(Book suggestedBook, Button quickButton, boolean canShow) {
		if (suggestedBook!=null) {
        	quickButton.setText(suggestedBook.getInitials());
		}
   		quickButton.setVisibility(canShow && suggestedBook!=null? View.VISIBLE: View.GONE);
	}

    /** number of buttons varies depending on screen size and orientation
     */
    public static int numButtonsToShow() {
    	return BibleApplication.getApplication().getResources().getInteger(R.integer.number_of_quick_buttons);
    }

}
