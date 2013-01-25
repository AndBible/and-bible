package net.bible.android.control.comparetranslations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BookName;

import android.util.Log;

/** Support the Compare Translations screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CompareTranslationsControl {
	
	private static final String TAG = "CompareTranslationsControl";

	private SwordDocumentFacade swordDocumentFacade = SwordDocumentFacade.getInstance();
	private SwordContentFacade swordContentFacade = SwordContentFacade.getInstance();
	
	public String getTitle() {
		StringBuilder stringBuilder = new StringBuilder();
		boolean wasFullBookname = BookName.isFullBookName();
		BookName.setFullBookName(false);

		stringBuilder.append(BibleApplication.getApplication().getString(R.string.compare_translations))
					 .append(": ")
					 .append(getVerse().getName());

		BookName.setFullBookName(wasFullBookname);
		return stringBuilder.toString();
	}
	
	public void setVerse(Verse verse) {
		getCurrentPageManager().getCurrentBible().doSetKey(verse);
	}

	public Verse getVerse() {
		return getCurrentPageManager().getCurrentBible().getSingleKey();
	}
	
	/** go to previous verse
	 */
	public Verse next() {
		 getCurrentPageManager().getCurrentBible().doNextVerse();
		 return getVerse();
	}
	
	/** go to next verse
	 */
	public Verse previous() {
		 getCurrentPageManager().getCurrentBible().doPreviousVerse();
		 return getVerse();
	}
	
	/** return the list of verses to be displayed
	 */
	public List<TranslationDto> getAllTranslations() {
		List<TranslationDto> retval = new ArrayList<TranslationDto>();
		List<Book> books = swordDocumentFacade.getBibles();
		FontControl fontControl = FontControl.getInstance();
		for (Book book : books) {
			try {
				String text = swordContentFacade.getPlainText(book, getVerse().getOsisID(), 1);
				if (text.length()>0) {
					
					// does this book require a custom font to display it
					File fontFile = null;
					String fontForBook = fontControl.getFontForBook(book);
					if (StringUtils.isNotEmpty(fontForBook)) {
						fontFile = fontControl.getFontFile(fontForBook);
					}
					
					// create DTO with all required info to display this Translation text
					retval.add(new TranslationDto(book, text, fontFile));
				}
			} catch (Exception nske) {
				Log.d(TAG, getVerse()+" not in "+book);
			}
		}

		return retval;		
	}
	
	public void showTranslation(TranslationDto translationDto) {
		getCurrentPageManager().setCurrentDocument(translationDto.getBook());
	}

	/** IOC */
	public CurrentPageManager getCurrentPageManager() {
		return CurrentPageManager.getInstance();
	}
}
