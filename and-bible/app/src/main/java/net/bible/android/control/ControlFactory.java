package net.bible.android.control;

import net.bible.android.BibleApplication;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.email.Emailer;
import net.bible.android.control.email.EmailerImpl;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.mynote.MyNote;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.report.ErrorReportControl;

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
	
	private DocumentControl documentControl = new DocumentControl();

	private final Map<Window, PageTiltScrollControl> screenPageTiltScrollControlMap = new HashMap<>();
	private MyNote mynoteControl = new MyNoteControl();

	private Emailer emailer;
	private ErrorReportControl errorReportControl;

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
//		eventManager = ABEventBus.getDefault();

		emailer = new EmailerImpl();
		errorReportControl = new ErrorReportControl(emailer); 

		// inject dependencies

		//TODO already added Inject
		//linkControl = new LinkControl(windowControl, searchControl);
	}
	
//	protected void ensureAllInitialised() {
//		if (!initialised) {
//			synchronized(this) {
//				if (!initialised) {
//					windowRepository.initialise(eventManager);
//					initialised = true;
//				}
//			}
//		}
//	}
	
	public DocumentControl getDocumentControl() {
//		ensureAllInitialised();

		return documentControl;		
	}

	//TODO check initialisation
//	public PageControl getPageControl() {
//		ensureAllInitialised();
//		return pageControl;
//	}

	public WindowControl getWindowControl() {
//		ensureAllInitialised();
		return BibleApplication.getApplication().getControllerComponent().windowControl();
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

	public CurrentPageManager getCurrentPageControl() {
//		ensureAllInitialised();
		Window activeWindow = getWindowControl().getActiveWindow();
		return activeWindow.getPageManager();
	}


	public MyNote getMyNoteControl() {
		return mynoteControl;
	}

	public ErrorReportControl getErrorReportControl() {
		return errorReportControl;
	}
}
