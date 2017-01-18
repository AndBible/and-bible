package net.bible.android.control;

import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.email.Emailer;
import net.bible.android.control.email.EmailerImpl;
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
import net.bible.android.control.report.ErrorReportControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.control.versification.BibleTraverser;

import java.util.HashMap;
import java.util.Map;

/** allow access to control layer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ControlFactory {
	private EventManager eventManager;
	
	private WindowRepository windowRepository;
	private DocumentBibleBooksFactory documentBibleBooksFactory = new DocumentBibleBooksFactory();
	private BibleTraverser bibleTraverser = new BibleTraverser();
	private DocumentControl documentControl = new DocumentControl();

	//TODO delete because already injected
	private PageControl pageControl = new PageControl();
	private BookmarkControl bookmarkControl = new BookmarkControl(new AndroidResourceProvider());

	private WindowControl windowControl;
	private final Map<Window, PageTiltScrollControl> screenPageTiltScrollControlMap = new HashMap<>();
	private LinkControl linkControl;
	private SearchControl searchControl = new SearchControl();
	private MyNote mynoteControl = new MyNoteControl();
	private SpeakControl speakControl = new SpeakControl();
	private ReadingPlanControl readingPlanControl = new ReadingPlanControl();
	private CompareTranslationsControl compareTranslationsControl;
	private FootnoteAndRefControl footnoteAndRefControl;

	private Emailer emailer;
	private ErrorReportControl errorReportControl;

	private NavigationControl navigationControl = new NavigationControl();
	
	private boolean initialised = false;
	
	private static ControlFactory singleton;
	
	public static ControlFactory getInstance() {
		if (singleton==null) {
			synchronized(ControlFactory.class) {
				if (singleton==null) {
					final ControlFactory controlFactory = new ControlFactory();
					controlFactory.createAll();
					singleton = controlFactory;
				}
			}
		}
		return singleton;
	}
	public static void setInstance(ControlFactory controlFactory) {
		singleton = controlFactory;
	}
	
	protected ControlFactory() {
	}
	
	protected void createAll() {
		eventManager = ABEventBus.getDefault();

		emailer = new EmailerImpl();
		errorReportControl = new ErrorReportControl(emailer); 

		// inject dependencies
		readingPlanControl.setSpeakControl(this.speakControl);
		
		navigationControl.setPageControl(this.pageControl);
		navigationControl.setDocumentBibleBooksFactory(documentBibleBooksFactory);
		searchControl.setDocumentBibleBooksFactory(documentBibleBooksFactory);
		
		bibleTraverser.setDocumentBibleBooksFactory(documentBibleBooksFactory);

		compareTranslationsControl = new CompareTranslationsControl(bibleTraverser);
		footnoteAndRefControl = new FootnoteAndRefControl(bibleTraverser);

		windowRepository = new WindowRepository();
		windowControl = new WindowControl(windowRepository, eventManager);
		
		linkControl = new LinkControl(windowControl);
	}
	
	protected void ensureAllInitialised() {
		if (!initialised) {
			synchronized(this) {
				if (!initialised) {
					windowRepository.initialise(eventManager);
					initialised = true;
				}
			}
		}
	}
	
	public DocumentControl getDocumentControl() {
		ensureAllInitialised();

		return documentControl;		
	}

	public DocumentBibleBooksFactory getDocumentBibleBooksFactory() {
		return documentBibleBooksFactory;
	}

	public PageControl getPageControl() {
		ensureAllInitialised();
		return pageControl;		
	}

	public WindowControl getWindowControl() {
		ensureAllInitialised();
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
		ensureAllInitialised();
		Window activeWindow = windowControl.getActiveWindow();
		return activeWindow.getPageManager();		
	}

	public LinkControl getLinkControl() {
		return linkControl;
	}

	public MyNote getMyNoteControl() {
		return mynoteControl;
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

	public NavigationControl getNavigationControl() {
		return navigationControl;
	}

	public BibleTraverser getBibleTraverser() {
		return bibleTraverser;
	}
	
	public ErrorReportControl getErrorReportControl() {
		return errorReportControl;
	}

	public BookmarkControl getBookmarkControl() {
		return bookmarkControl;
	}
}
