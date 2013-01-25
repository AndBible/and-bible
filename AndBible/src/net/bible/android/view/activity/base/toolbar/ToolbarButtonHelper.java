package net.bible.android.view.activity.base.toolbar;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.widget.Button;

public class ToolbarButtonHelper {
	
	public void updateQuickButton(final Book suggestedBook, final Button quickButton, final boolean canShow) {
		if (quickButton!=null) {
			
			quickButton.post(new Runnable() {
				@Override
				public void run() {
					if (suggestedBook!=null) {
			        	updateButtonText(suggestedBook.getInitials(), quickButton);
					}
			   		quickButton.setVisibility(canShow && suggestedBook!=null? View.VISIBLE: View.GONE);
				}
			});
		}
	}

	public void updateButtonText(String buttonText, Button quickButton) {
		if (buttonText!=null && quickButton!=null) {
        	quickButton.setText(buttonText);
		}
	}

	/** number of buttons varies depending on screen size and orientation
     */
    public static int numQuickButtonsToShow() {
    	return BibleApplication.getApplication().getResources().getInteger(R.integer.number_of_quick_buttons);
    }

}
