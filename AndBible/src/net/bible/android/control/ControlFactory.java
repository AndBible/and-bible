package net.bible.android.control;

import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.footnoteandref.FootnoteAndRefControl;
import net.bible.android.control.link.LinkControl;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.speak.SpeakControl;

//TODO replace with ioc (maybe)
/** allow access to control layer
 *
 * @author denha1m
 *
 */
public class ControlFactory {
	//TODO move instance creation here
	private DocumentControl documentControl = new DocumentControl();
	private PageControl pageControl = new PageControl();
	private SplitScreenControl splitScreenControl = new SplitScreenControl();
	private PageTiltScrollControl pageTiltScrollControlSplitScreen1 = new PageTiltScrollControl();
	private PageTiltScrollControl pageTiltScrollControlSplitScreen2 = new PageTiltScrollControl();
	private LinkControl linkControl = new LinkControl();
	private SearchControl searchControl = new SearchControl();
	private Bookmark bookmarkControl = new BookmarkControl();
	private MyNote mynoteControl = new MyNoteControl();
	private DownloadControl downloadControl = new DownloadControl();
	private SpeakControl speakControl = new SpeakControl();
	private ReadingPlanControl readingPlanControl = new ReadingPlanControl();
	private CompareTranslationsControl compareTranslationsControl = new CompareTranslationsControl();
	private FootnoteAndRefControl footnoteAndRefControl = new FootnoteAndRefControl();
	private BackupControl backupControl = new BackupControl();
	
	private static ControlFactory singleton = new ControlFactory();
	
	public static ControlFactory getInstance() {
		return singleton;
	}
	
	private ControlFactory() {
		// inject dependencies
		readingPlanControl.setSpeakControl(this.speakControl);
	}
	
	public DocumentControl getDocumentControl() {
		return documentControl;		
	}

	public PageControl getPageControl() {
		return pageControl;		
	}

	public SplitScreenControl getSplitScreenControl() {
		return splitScreenControl;
	}

	public PageTiltScrollControl getPageTiltScrollControl(Screen screen) {
		if (Screen.SCREEN_1==screen) {
			return pageTiltScrollControlSplitScreen1;
		} else {
			return pageTiltScrollControlSplitScreen2;
		}
	}

	public SearchControl getSearchControl() {
		return searchControl;		
	}

	public CurrentPageManager getCurrentPageControl() {
		return CurrentPageManager.getInstance();		
	}

	public LinkControl getLinkControl() {
		return linkControl;
	}

	/**
	 * @return the bookmarkControl
	 */
	public Bookmark getBookmarkControl() {
		return bookmarkControl;
	}
	
	public MyNote getMyNoteControl() {
		return mynoteControl;
	}

	public DownloadControl getDownloadControl() {
		return downloadControl;
	}

	public SpeakControl getSpeakControl() {
		return speakControl;
	}

	public ReadingPlanControl getReadingPlanControl() {
		return readingPlanControl;
	}

	public CompareTranslationsControl getCompareTranslationsControl() {
		return compareTranslationsControl;
	}

	public FootnoteAndRefControl getFootnoteAndRefControl() {
		return footnoteAndRefControl;
	}

	public BackupControl getBackupControl() {
		return backupControl;
	}
}
