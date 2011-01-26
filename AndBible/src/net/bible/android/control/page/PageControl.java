package net.bible.android.control.page;

import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.versification.BibleInfo;

import android.util.Log;

public class PageControl {
	
	private static final String TAG = "PageControl";
	
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
