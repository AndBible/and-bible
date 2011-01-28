package net.bible.android.control.page;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;
import org.crosswire.jsword.versification.BibleNames;

import android.util.Log;

public class PageControl {
	
	private static final String TAG = "PageControl";
	
	/** This is only called after the very first bible download to attempt to ensure the first page is not 'Verse not found' 
	 * go through a list of default verses until one is found in the first/only book installed
	 */
	public void setFirstUseDefaultVerse() {
		try {
			Verse[] defaultVerses = new Verse[] { new Verse(BibleNames.GENESIS,1,1),
												  new Verse(BibleNames.JOHN,3,16),
												  new Verse(BibleNames.PSALMS,1,1)};
	    	List<Book> bibles = SwordApi.getInstance().getBibles();
	        if (bibles.size()==1) {
	        	Book bible = bibles.get(0);
	        	for (Verse verse : defaultVerses) {
		        	if (bible.contains(verse)) {
		        		ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().setKey(verse);
		        		return;
		        	}
	        	}
	        }
			
		} catch (NoSuchVerseException e) {
			Log.e(TAG, "Verse error");
		}
	}
	/** get page title including info about current doc and key/verse
	 * 
	 * @return
	 */
	public String getTitle() {
		boolean fullBookNameSave = BibleInfo.isFullBookName();
		
		// show short book name to save space if Portrait
		BibleInfo.setFullBookName(!CommonUtils.isPortrait());
		
		StringBuilder title = new StringBuilder();
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		if (currentPage!=null) {
			if (currentPage.getCurrentDocument()!=null) {
				title.append(currentPage.getCurrentDocument()).append(" ");
			}
			if (currentPage.getSingleKey()!=null) {
				 title.append(currentPage.getSingleKey().getName());
			}
		}
		// restore full book name setting
		BibleInfo.setFullBookName(fullBookNameSave);
		
		return title.toString();
	}
}
