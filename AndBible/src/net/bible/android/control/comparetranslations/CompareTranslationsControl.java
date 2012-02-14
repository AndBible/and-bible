package net.bible.android.control.comparetranslations;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

/** Support the Compare Translations screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CompareTranslationsControl {
	
	private static final String TAG = "CompareTranslationsControl";

	private CurrentPageManager currentPageManager; // injected
	
	private SwordDocumentFacade swordDocumentFacade = SwordDocumentFacade.getInstance();
	private SwordContentFacade swordContentFacade = SwordContentFacade.getInstance();
	
	public String getTitle() {
		StringBuilder stringBuilder = new StringBuilder();
		Key currentVerse = currentPageManager.getCurrentBible().getSingleKey();

		stringBuilder.append(BibleApplication.getApplication().getString(R.string.compare_translations))
					 .append(" ")
					 .append(currentVerse.getName());
		return stringBuilder.toString();
	}
	
	public List<TranslationDto> getAllTranslations() {
		List<TranslationDto> retval = new ArrayList<TranslationDto>();
		Key currentVerse = currentPageManager.getCurrentBible().getSingleKey(); 
		List<Book> books = swordDocumentFacade.getBibles();
		for (Book book : books) {
			try {
				String text = swordContentFacade.getPlainText(book, currentVerse.getOsisID(), 1);
				if (text.length()>0) {
					retval.add(new TranslationDto(book, text));
				}
			} catch (Exception nske) {
				Log.d(TAG, currentVerse+" not in "+book);
			}
		}

		return retval;		
	}
	
	public void showTranslation(TranslationDto translationDto) {
		currentPageManager.setCurrentDocument(translationDto.getBook());
	}

	public void setCurrentPageManager(CurrentPageManager currentPageManager) {
		this.currentPageManager = currentPageManager;
	}
}
