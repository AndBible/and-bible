package net.bible.android.control.comparetranslations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.android.control.versification.ConvertibleVerse;
import net.bible.service.common.CommonUtils;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
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
	
	private SwordDocumentFacade swordDocumentFacade = SwordDocumentFacade.getInstance();
	private SwordContentFacade swordContentFacade = SwordContentFacade.getInstance();

	private BibleTraverser bibleTraverser;

	private static final String TAG = "CompareTranslationsCtrl";

	public CompareTranslationsControl(BibleTraverser bibleTraverser) {
		this.bibleTraverser = bibleTraverser;
	}

	public String getTitle(Verse verse) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean wasFullBookname = BookName.isFullBookName();
		BookName.setFullBookName(false);

		stringBuilder.append(BibleApplication.getApplication().getString(R.string.compare_translations))
					 .append(": ")
					 .append(CommonUtils.getKeyDescription(verse));

		BookName.setFullBookName(wasFullBookname);
		return stringBuilder.toString();
	}
	
	public void setVerse(Verse verse) {
		getCurrentPageManager().getCurrentBible().doSetKey(verse);
	}

	public Verse getDefaultVerse() {
		return getCurrentPageManager().getCurrentBible().getSingleKey();
	}

	/** Calculate next verse
	 */
	public Verse getNextVerse(Verse verse) {
		 return bibleTraverser.getNextVerse(getCurrentPageManager().getCurrentPassageDocument(), verse);
	}

	/** Calculate next verse
	 */
	public Verse getPreviousVerse(Verse verse) {
		return bibleTraverser.getPrevVerse(getCurrentPageManager().getCurrentPassageDocument(), verse);
	}

	/** return the list of verses to be displayed
	 */
	public List<TranslationDto> getAllTranslations(Verse verse) {
		List<TranslationDto> retval = new ArrayList<>();
		List<Book> books = swordDocumentFacade.getBibles();
		FontControl fontControl = FontControl.getInstance();
		
		ConvertibleVerse convertibleVerse = new ConvertibleVerse(verse);
		
		for (Book book : books) {
			try {
				String text = swordContentFacade.getPlainText(book, convertibleVerse.getVerse(((AbstractPassageBook)book).getVersification()), 1);
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
				Log.d(TAG, verse+" not in "+book);
			}
		}

		return retval;		
	}
	
	public void showTranslationForVerse(TranslationDto translationDto, Verse verse) {
		getCurrentPageManager().setCurrentDocumentAndKey(translationDto.getBook(), verse);
	}

	public CurrentPageManager getCurrentPageManager() {
		return ControlFactory.getInstance().getCurrentPageControl();
	}
}
