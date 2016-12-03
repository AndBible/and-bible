package net.bible.android.control;

import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.bookmark.Bookmark;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.comparetranslations.CompareTranslationsControl;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.download.DownloadQueue;
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
import net.bible.android.view.activity.page.BibleJavascriptInterface;
import net.bible.android.view.activity.page.BibleView;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.activity.page.VerseActionModeMediator;
import net.bible.android.view.activity.page.VerseCalculator;
import net.bible.android.view.activity.page.VerseMenuCommandHandler;
import net.bible.service.download.RepoFactory;
import net.bible.service.font.FontControl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//TODO replace with ioc (maybe)
/** allow access to control layer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ControlFactory {
	private MainBibleActivity mainBibleActivity;
	private ResourceProvider resourceProvider;
	private EventManager eventManager;
	
	private WindowRepository windowRepository;
	private DocumentBibleBooksFactory documentBibleBooksFactory = new DocumentBibleBooksFactory();
	private BibleTraverser bibleTraverser = new BibleTraverser();
	private DocumentControl documentControl = new DocumentControl();
	private PageControl pageControl = new PageControl();
	private WindowControl windowControl;
	private final Map<Window, PageTiltScrollControl> screenPageTiltScrollControlMap = new HashMap<>();
	private LinkControl linkControl;
	private SearchControl searchControl = new SearchControl();
	private MyNote mynoteControl = new MyNoteControl();
	private DownloadControl downloadControl;
	private SpeakControl speakControl = new SpeakControl();
	private ReadingPlanControl readingPlanControl = new ReadingPlanControl();
	private CompareTranslationsControl compareTranslationsControl;
	private FootnoteAndRefControl footnoteAndRefControl;
	private BackupControl backupControl = new BackupControl();
	private Bookmark bookmarkControl;

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
		resourceProvider = new AndroidResourceProvider();
		eventManager = ABEventBus.getDefault();

		emailer = new EmailerImpl();
		errorReportControl = new ErrorReportControl(emailer); 

		bookmarkControl = new BookmarkControl(resourceProvider);
		
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

		final ExecutorService downloadExecutorService = Executors.newSingleThreadExecutor();
		downloadControl = new DownloadControl(new DownloadQueue(downloadExecutorService), RepoFactory.getInstance().getXiphosRepo(), FontControl.getInstance());
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

	public void provide(MainBibleActivity mainBibleActivity) {
		this.mainBibleActivity = mainBibleActivity;
	}

	public void inject(BibleView bibleView) {
		VerseActionModeMediator bibleViewVerseActionModeMediator = new VerseActionModeMediator(mainBibleActivity, bibleView, getPageControl(), new VerseMenuCommandHandler(mainBibleActivity, getPageControl()));

		BibleJavascriptInterface bibleJavascriptInterface = new BibleJavascriptInterface(bibleViewVerseActionModeMediator);

		bibleView.setBibleJavascriptInterface(bibleJavascriptInterface);
	}

	public void inject(BibleJavascriptInterface bibleJavascriptInterface) {
		bibleJavascriptInterface.setVerseCalculator(new VerseCalculator());
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
	
	public ErrorReportControl getErrorReportControl() {
		return errorReportControl;
	}
}
