package net.bible.android.control;

import net.bible.android.control.navigation.DocumentBibleBooksFactory;
import net.bible.service.sword.SwordDocumentFacade;

import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

/**
 * Support initialisation as i) do it now or ii) do this eventually
 */
@ApplicationScope
public class Initialisation {

	private final DocumentBibleBooksFactory documentBibleBooksFactory;

	private boolean isInitialised = false;
	
	private static final long INITIALISE_DELAY = 3000;
	
	@Inject
	public Initialisation(DocumentBibleBooksFactory documentBibleBooksFactory) {
		this.documentBibleBooksFactory = documentBibleBooksFactory;
	}

	/**
	 * Allow Splash screen to be displayed if starting from scratch, otherwise, if returning to an Activity then ensure all initialisation occurs eventually. 
	 */
	public void initialiseEventually() {
		TimerTask timerTask = new TimerTask() {
			public void run() {
				initialiseNow();
			}
		};

		Timer timer = new Timer();
		timer.schedule(timerTask, INITIALISE_DELAY);
	}

	/**
	 * Call any init routines that must be called at least once near the start of running the app e.g. start HistoryManager
	 */
	public synchronized void initialiseNow() {
		if (!isInitialised) {
	        // force Sword to initialise itself
	        SwordDocumentFacade.getInstance().getBibles();

	        // Now initialise other system features

	        // needs to register a listener
	        documentBibleBooksFactory.initialise();
	        
	        isInitialised = true;
		}
	}
}
