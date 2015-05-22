package net.bible.android.control;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.footnoteandref.FootnoteAndRefControl;
import net.bible.android.control.link.LinkControl;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.navigation.DocumentBibleBooksFactory;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.page.window.WindowRepository;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.control.versification.BibleTraverser;

//TODO replace with ioc (maybe)
/** allow access to control layer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ControlFactory {
	private ResourceProvider resourceProvider;
	private EventManager eventManager;
	
	private WindowRepository windowRepository;
	private DocumentBibleBooksFactory documentBibleBooksFactory = new DocumentBibleBooksFactory();
	private BibleTraverser bibleTraverser = new BibleTraverser();
	private DocumentControl documentControl = new DocumentControl();
	private PageControl pageControl = new PageControl();
	private WindowControl windowControl;
	private Map<Window, PageTiltScrollControl> screenPageTiltScrollControlMap = new HashMap<>();
	private LinkControl linkControl;
	private SearchControl searchControl = new SearchControl();
	private MyNote mynoteControl = new MyNoteControl();
	private DownloadControl downloadControl = new DownloadControl();
	private SpeakControl speakControl = new SpeakControl();
	private ReadingPlanControl readingPlanControl = new ReadingPlanControl();
	private CompareTranslationsControl compareTranslationsControl = new CompareTranslationsControl();
	private FootnoteAndRefControl footnoteAndRefControl = new FootnoteAndRefControl();
	private BackupControl backupControl = new BackupControl();
	private Bookmark bookmarkControl;

	private NavigationControl navigationControl = new NavigationControl();
	
	private static ControlFactory singleton = new ControlFactory();
	
	public static ControlFactory getInstance() {
		return singleton;
	}
	public static void setInstance(ControlFactory controlFactory) {
		singleton = controlFactory;
	}
	
	protected ControlFactory() {
		createAll();
	}
	
	protected void createAll() {
		resourceProvider = new AndroidResourceProvider();
		eventManager = ABEventBus.getDefault();
		
		bookmarkControl = new BookmarkControl(resourceProvider);
		
		// inject dependencies
		readingPlanControl.setSpeakControl(this.speakControl);
		
		navigationControl.setPageControl(this.pageControl);
		navigationControl.setDocumentBibleBooksFactory(documentBibleBooksFactory);
		searchControl.setDocumentBibleBooksFactory(documentBibleBooksFactory);
		
		bibleTraverser.setDocumentBibleBooksFactory(documentBibleBooksFactory);

		windowRepository = new WindowRepository(eventManager);
		windowControl = new WindowControl(windowRepository, eventManager);
		
		linkControl = new LinkControl(windowControl);
	}
	
	public DocumentControl getDocumentControl() {
		return documentControl;		
	}

	public DocumentBibleBooksFactory getDocumentBibleBooksFactory() {
		return documentBibleBooksFactory;
	}

	public PageControl getPageControl() {
		return pageControl;		
	}

	public WindowControl getWindowControl() {
		return windowControl;
	}

	public PageTiltScrollControl getPageTiltScrollControl(Window window) {
		PageTiltScrollControl pageTiltScrollControl = screenPageTiltScrollControlMap.get(window);
		if (pageTiltScrollControl==null) {
			synchronized(screenPageTiltScrollControlMap) {
				pageTiltScrollControl = screenPageTiltScrollControlMap.get(window);
				if (pageTiltScrollControl==null) {
					pageTiltScrollControl = new PageTiltScrollControl();
					screenPageTiltScrollControlMap.put(window, pageTiltScrollControl);
				}
			}
		}
		return pageTiltScrollControl;
	}

	public SearchControl getSearchControl() {
		return searchControl;		
	}

	public CurrentPageManager getCurrentPageControl() {
		return windowControl.getActiveWindow().getPageManager();		
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

	public NavigationControl getNavigationControl() {
		return navigationControl;
	}

	public BibleTraverser getBibleTraverser() {
		return bibleTraverser;
	}
}
