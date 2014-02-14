package net.bible.android.control.page;

import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
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
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.BookName;
import org.crosswire.jsword.versification.Versification;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.ClipboardManager;
import android.util.Log;

/**
 * SesionFacade for CurrentPage used by View classes
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PageControl {
	
	private static final String TAG = "PageControl";
	
	/** paste the current verse to the system clipboard
	 */
	public void copyToClipboard() {
		try {
			Book book = getCurrentPageManager().getCurrentPage().getCurrentDocument();
			Key key = getCurrentPageManager().getCurrentPage().getSingleKey();
			
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
			Book book = getCurrentPageManager().getCurrentPage().getCurrentDocument();
			Key key = getCurrentPageManager().getCurrentPage().getSingleKey();
			
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
			Versification versification = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getVersification();
			Verse[] defaultVerses = new Verse[] { new Verse(versification, BibleBook.GEN,1,1),
												  new Verse(versification, BibleBook.JOHN,3,16),
												  new Verse(versification, BibleBook.PS,1,1)};
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
			
		} catch (Exception e) {
			Log.e(TAG, "Verse error");
		}
	}
	/** get page title including info about current doc
	 * 
	 * @return
	 */
	public String getCurrentDocumentTitle() {
	
		StringBuilder title = new StringBuilder();
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		if (currentPage!=null) {
			if (currentPage.getCurrentDocument()!=null) {
				title.append(currentPage.getCurrentDocument()).append(" ");
			}
		}
		// ActionBar truncates very long text automatically
		int maxLength = 12; //isPortrait ? 8 : 12;
		String retVal = shorten(title.toString(), maxLength);

		return retVal;
	}

	/** get page title including info about key/verse
	 * 
	 * @return
	 */
	public String getCurrentPageTitle() {
		boolean fullBookNameSave = BookName.isFullBookName();
		
		boolean isPortrait = CommonUtils.isPortrait();
		
		// show short book name to save space if Portrait
		BookName.setFullBookName(!isPortrait);
		String retVal="";
		try {
			StringBuilder title = new StringBuilder();
			CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
			if (currentPage!=null) {
				if (currentPage.getSingleKey()!=null) {
					title.append(CommonUtils.getKeyDescription(currentPage.getSingleKey()));
				}
			}
			
			int maxLength = isPortrait ? 11 : 26;
			retVal = shorten(title.toString(), maxLength);
			// favour correct capitalisation because it looks better and is narrower so more fits in
			if (ABStringUtils.isAllUpperCaseWherePossible(retVal)) {
				// Books like INSTITUTES need corrected capitalisation
				retVal = WordUtils.capitalizeFully(retVal);
			}		
		} catch (Exception e) {
			Log.e(TAG, "Error getting page title", e);
		} finally {
			// restore full book name setting
			BookName.setFullBookName(fullBookNameSave);
		}
		return retVal;
	}

	public String getCurrentBibleBookName() {
		CurrentBiblePage page = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible();
		Verse verse = page.getSingleKey();
		BibleBook book = verse.getBook();
		
		String name;
//		if (CommonUtils.isPortrait()) {
			name = page.getVersification().getShortName(book);
//		} else {
//			name = page.getVersification().getLongName(book);
//		}
		return name;
	}

	public Verse getCurrentBibleVerse() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getSingleKey();
	}
	
	/** font size may be adjusted for certain fonts e.g. SBLGNT
	 */
	public int getDocumentFontSize(Screen splitScreenNo) {
		// get base font size
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		int fontSize = preferences.getInt("text_size_pref", 16);

		// if book has a special font it may require an adjusted font size
		Book book = CurrentPageManager.getInstance(splitScreenNo).getCurrentPage().getCurrentDocument();
		String font = FontControl.getInstance().getFontForBook(book);
		int fontSizeAdjustment = FontControl.getInstance().getFontSizeAdjustment(font, book);
		
		return fontSize+fontSizeAdjustment;		
	}
	
	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return isStrongsRelevant() && 
			   CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	public boolean isStrongsRelevant() {
		return ControlFactory.getInstance().getDocumentControl().isStrongsInBook();
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
	
	public CurrentPageManager getCurrentPageManager() {
		return CurrentPageManager.getInstance();
	}
}
