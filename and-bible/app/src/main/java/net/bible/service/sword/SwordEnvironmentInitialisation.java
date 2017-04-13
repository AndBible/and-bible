package net.bible.service.sword;

import net.bible.android.SharedConstants;
import net.bible.android.control.versification.VersificationMappingInitializer;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;

import org.crosswire.common.util.CWProject;
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

				// 10 sec is too low, 15 may do but put it at 20 secs
				WebResource.setTimeout(20000);

				// because the above line causes initialisation set the is initialised flag here
				isSwordLoaded = true;

				new VersificationMappingInitializer().startListening();

				log.debug(("Main JSword path:"+CWProject.instance().getWritableProjectDir()));
			}

		} catch (Exception e) {
			log.error("Error initialising", e);
		}
	}
}
