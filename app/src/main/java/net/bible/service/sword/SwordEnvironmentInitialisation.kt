/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.sword

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.versification.VersificationMappingInitializer
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils.ensureDirExists
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.isAndroid
import net.bible.service.common.Logger
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.Reporter
import org.crosswire.common.util.ReporterEvent
import org.crosswire.common.util.ReporterListener
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.index.lucene.LuceneIndexManager
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.PassageType
import java.io.File

/**
 * Create directories required by JSword and set required JSword configuration.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SwordEnvironmentInitialisation {
    private var isSwordLoaded = false
    private val log = Logger(SwordDocumentFacade::class.java.name)
    fun initialiseJSwordFolders() {
        try {
            if (isAndroid && !isSwordLoaded) { // ensure required module directories exist and register them with jsword
				// This folder we can always access freely without any extra permissions.
                val moduleDir = SharedConstants.MODULE_DIR
                // main module dir
                ensureDirExists(moduleDir)
                // mods.d
                ensureDirExists(File(moduleDir, SwordConstants.DIR_CONF))
                // modules
                ensureDirExists(File(moduleDir, SwordConstants.DIR_DATA))
                // indexes
                ensureDirExists(File(moduleDir, LuceneIndexManager.DIR_LUCENE))
                //fonts
                ensureDirExists(SharedConstants.FONT_DIR)
                // Optimize for less memory
                PassageKeyFactory.setDefaultType(PassageType.MIX)
                // the following are required to set the read and write dirs for module properties, initialised during the following call to setHome
                System.setProperty("jsword.home", moduleDir.absolutePath)
                CWProject.instance().setFrontendName("and-bible")
                // Permission is requested at MainBibleActivitiy.checkSdcardReadPermission and app is restarted if permission is granted.
                if (ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    enableDefaultAndManualInstallFolder()
                } else {
                    CWProject.setHome("jsword.home", moduleDir.absolutePath, null)
                }
                log.debug("Main JSword path:" + CWProject.instance().writableProjectDir)
                // 10 sec is too low, 15 may do but put it at 20 secs
                WebResource.setTimeout(20000)
                // because the above line causes initialisation set the is initialised flag here
                isSwordLoaded = true
                VersificationMappingInitializer().startListening()
            }
        } catch (e: Exception) {
            log.error("Error initialising", e)
        }
    }

    @Throws(BookException::class)
    fun enableDefaultAndManualInstallFolder() {
        CWProject.setHome("jsword.home", SharedConstants.MODULE_DIR.absolutePath, SharedConstants.MANUAL_INSTALL_DIR.absolutePath)
        // the following causes Sword to initialise itself and can take quite a few seconds
		// add manual install dir to this list
        SwordBookPath.setAugmentPath(arrayOf(SharedConstants.MANUAL_INSTALL_DIR, SharedConstants.MANUAL_INSTALL_DIR2, SharedConstants.INTERNAL_MODULE_DIR))
    }

    /** JSword calls back to this listener in the event of some types of error
     *
     */
    fun installJSwordErrorReportListener() {
        Reporter.addReporterListener(object : ReporterListener {
            override fun reportException(ev: ReporterEvent) {
                showMsg(ev)
            }

            override fun reportMessage(ev: ReporterEvent) {
                showMsg(ev)
            }

            private fun showMsg(ev: ReporterEvent?) {
                val msg: String?
                msg = if (ev == null) {
                    getResourceString(R.string.error_occurred)
                } else if (!StringUtils.isEmpty(ev.message)) {
                    ev.message
                } else if (ev.exception != null && StringUtils.isEmpty(ev.exception.message)) {
                    ev.exception.message
                } else {
                    getResourceString(R.string.error_occurred)
                }
                // convert Throwable to Exception for Dialogs
                val e: Exception
                e = if (ev != null) {
                    val th = ev.exception
                    if (th is Exception) th else Exception("Jsword Exception", th)
                } else {
                    Exception("JSword Exception")
                }
                Dialogs.instance.showErrorMsg(msg, e)
            }
        })
    }
}
