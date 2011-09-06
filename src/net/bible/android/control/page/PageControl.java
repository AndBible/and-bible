package net.bible.android.control.page;

import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.ABStringUtils;
import org.apache.commons.lang.WordUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.BibleInfo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.ClipboardManager;
import android.util.Log;

public class PageControl {
	
	private CurrentPageManager mCurrentPageManager;
	
	private static final String TAG = "PageControl";
	
	/** paste the current verse to the system clipboard
	 */
	public void copyToClipboard() {
		try {
			Book book = mCurrentPageManager.getCurrentPage().getCurrentDocument();
			Key key = mCurrentPageManager.getCurrentPage().getSingleKey();
			
			String text = key.getName()+"\n"+SwordContentFacade.getInstance().getCanonicalText(book, key);
			ClipboardManager clipboard = (ClipboardManager)BibleApplication.getApplication().getSystemService(Activity.CLIPBOARD_SERVICE);
			clipboard.setText(text);
		} catch (Exception e) {
			Log.e(TAG, "Error pasting to clipboard", e);
			Dialogs.getInstance().showErrorMsg("Error copying to clipboard");
		}
	}

	/** send the current verse via SMS
	 */
	/** send the current verse via social applications installed on user's device
	 */
	public void shareVerse() {
		try {
			Book book = mCurrentPageManager.getCurrentPage().getCurrentDocument();
			Key key = mCurrentPageManager.getCurrentPage().getSingleKey();
			
			String text = key.getName()+"\n"+SwordContentFacade.getInstance().getCanonicalText(book, key);
			
			Intent sendIntent  = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("text/plain");

			sendIntent.putExtra(Intent.EXTRA_TEXT, text);
			// subject is used when user chooses to send verse via e-mail
			sendIntent.putExtra(Intent.EXTRA_SUBJECT, BibleApplication.getApplication().getText(R.string.share_verse_subject));

			Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
			activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share_verse))); 
			
		} catch (Exception e) {
			Log.e(TAG, "Error sharing verse", e);
			Dialogs.getInstance().showErrorMsg("Error sharing verse");
		}
	}
	
	/** This is only called after the very first bible download to attempt to ensure the first page is not 'Verse not found' 
	 * go through a list of default verses until one is found in the first/only book installed
	 */
	public void setFirstUseDefaultVerse() {
		try {
			Verse[] defaultVerses = new Verse[] { new Verse(BibleBook.GEN,1,1),
												  new Verse(BibleBook.JOHN,3,16),
												  new Verse(BibleBook.PS,1,1)};
	    	List<Book> bibles = SwordDocumentFacade.getInstance().getBibles();
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
	/** get page title including info about current doc
	 * 
	 * @return
	 */
	public String getCurrentDocumentTitle() {
		boolean isPortrait = CommonUtils.isPortrait();
	
		StringBuilder title = new StringBuilder();
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		if (currentPage!=null) {
			if (currentPage.getCurrentDocument()!=null) {
				title.append(currentPage.getCurrentDocument()).append(" ");
			}
		}
		
		int maxLength = isPortrait ? 4 : 12;
		String retVal = shorten(title.toString(), maxLength);

		return retVal;
	}

	/** get page title including info about key/verse
	 * 
	 * @return
	 */
	public String getCurrentPageTitle() {
		boolean fullBookNameSave = BibleInfo.isFullBookName();
		
		boolean isPortrait = CommonUtils.isPortrait();
		
		// show short book name to save space if Portrait
		BibleInfo.setFullBookName(!isPortrait);
		
		StringBuilder title = new StringBuilder();
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		if (currentPage!=null) {
			if (currentPage.getSingleKey()!=null) {
				 title.append(currentPage.getSingleKey().getName());
			}
		}
		// restore full book name setting
		BibleInfo.setFullBookName(fullBookNameSave);
		
		int maxLength = isPortrait ? 11 : 26;
		String retVal = shorten(title.toString(), maxLength);
		// favour correct capitalisation because it looks better and is narrower so more fits in
		if (ABStringUtils.isAllUpperCaseWherePossible(retVal)) {
			// Books like INSTITUTES need corrected capitalisation
			retVal = WordUtils.capitalizeFully(retVal);
		}		
		return retVal;
	}

	/** font size may be adjusted for certain fonts e.g. SBLGNT
	 */
	public int getDocumentFontSize() {
		// get base font size
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		int fontSize = preferences.getInt("text_size_pref", 16);

		// if book has a special font it may require an adjusted font size
		Book book = mCurrentPageManager.getCurrentPage().getCurrentDocument();
		String font = FontControl.getInstance().getFontForBook(book);
		int fontSizeAdjustment = FontControl.getInstance().getFontSizeAdjustment(font);
		
		return fontSize+fontSizeAdjustment;		
	}
	
	private String shorten(String title, int maxLength) {
		String retVal = null;
		if (title.length()>maxLength) {
			retVal = title.substring(0, maxLength);
		} else {
			retVal = title;
		}
		return retVal;
	}
	
	public void setCurrentPageManager(CurrentPageManager currentPageManager) {
		mCurrentPageManager = currentPageManager;
	}
}
