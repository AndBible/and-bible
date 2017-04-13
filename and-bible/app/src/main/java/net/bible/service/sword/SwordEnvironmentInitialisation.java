package net.bible.service.sword;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.versification.VersificationMappingInitializer;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.Reporter;
import org.crosswire.common.util.ReporterEvent;
import org.crosswire.common.util.ReporterListener;
import org.crosswire.common.util.WebResource;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;
import org.crosswire.jsword.index.lucene.LuceneIndexManager;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.PassageType;

import java.io.File;

/**
 * Create directories required by JSword and set required JSword configuration.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class SwordEnvironmentInitialisation {

	private static boolean isSwordLoaded;

	private static final Logger log = new Logger(SwordDocumentFacade.class.getName());

	public static void initialiseJSwordFolders() {
		try {
			if (CommonUtils.isAndroid() && !isSwordLoaded) {
				// ensure required module directories exist and register them with jsword
				File moduleDir = SharedConstants.MODULE_DIR;

				// main module dir
				CommonUtils.ensureDirExists(moduleDir);
				// mods.d
				CommonUtils.ensureDirExists(new File(moduleDir, SwordConstants.DIR_CONF));
				// modules
				CommonUtils.ensureDirExists(new File(moduleDir, SwordConstants.DIR_DATA));
				// indexes
				CommonUtils.ensureDirExists(new File(moduleDir, LuceneIndexManager.DIR_LUCENE));
				//fonts
				CommonUtils.ensureDirExists(SharedConstants.FONT_DIR);

				// Optimize for less memory
				PassageKeyFactory.setDefaultType(PassageType.MIX);

				// the following are required to set the read and write dirs for module properties, initialised during the following call to setHome
				System.setProperty("jsword.home", moduleDir.getAbsolutePath());
				CWProject.instance().setFrontendName("and-bible");

				// the second value below is the one which is used in effectively all circumstances
				CWProject.setHome("jsword.home", moduleDir.getAbsolutePath(), SharedConstants.MANUAL_INSTALL_DIR.getAbsolutePath());

				// the following causes Sword to initialise itself and can take quite a few seconds
				SwordBookPath.setAugmentPath(new File[] {SharedConstants.MANUAL_INSTALL_DIR});  // add manual install dir to this list

				log.debug(("Main JSword path:"+CWProject.instance().getWritableProjectDir()));

				// 10 sec is too low, 15 may do but put it at 20 secs
				WebResource.setTimeout(20000);

				// because the above line causes initialisation set the is initialised flag here
				isSwordLoaded = true;

				new VersificationMappingInitializer().startListening();
			}

		} catch (Exception e) {
			log.error("Error initialising", e);
		}
	}

	/** JSword calls back to this listener in the event of some types of error
	 *
	 */
	public static void installJSwordErrorReportListener() {
		Reporter.addReporterListener(new ReporterListener() {
			@Override
			public void reportException(final ReporterEvent ev) {
				showMsg(ev);
			}

			@Override
			public void reportMessage(final ReporterEvent ev) {
				showMsg(ev);
			}

			private void showMsg(ReporterEvent ev) {
				String msg;
				if (ev==null) {
					msg = CommonUtils.getResourceString(R.string.error_occurred);
				} else if (!StringUtils.isEmpty(ev.getMessage())) {
					msg = ev.getMessage();
				} else if (ev.getException()!=null && StringUtils.isEmpty(ev.getException().getMessage())) {
					msg = ev.getException().getMessage();
				} else {
					msg = CommonUtils.getResourceString(R.string.error_occurred);
				}

				// convert Throwable to Exception for Dialogs
				Exception e;
				if (ev!=null) {
					Throwable th = ev.getException();
					e = th instanceof Exception ? (Exception)th : new Exception("Jsword Exception", th);
				} else {
					e = new Exception("JSword Exception");
				}

				Dialogs.getInstance().showErrorMsg(msg, e);
			}
		});
	}


}
