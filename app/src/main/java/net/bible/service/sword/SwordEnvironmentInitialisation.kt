/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.sword

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils.ensureDirExists
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.isAndroid
import net.bible.service.common.Logger
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

private const val FIVE_MINUTES_MS: Long = 1000 * 60 * 5
/**
 * Create directories required by JSword and set required JSword configuration.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SwordEnvironmentInitialisation {
    private var isSwordLoaded = false
    private val log = Logger(SwordEnvironmentInitialisation::class.java.name)
    fun initialiseJSwordFolders() {
        try {
            if (isAndroid && !isSwordLoaded) { // ensure required module directories exist and register them with jsword
				// This folder we can always access freely without any extra permissions.
                val moduleDir = SharedConstants.modulesDir

                // main module dir
                ensureDirExists(moduleDir)
                // mods.d
                ensureDirExists(File(moduleDir, SwordConstants.DIR_CONF))
                // modules
                ensureDirExists(File(moduleDir, SwordConstants.DIR_DATA))
                // indexes
                ensureDirExists(File(moduleDir, LuceneIndexManager.DIR_LUCENE))
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
                // 5 sec should be more than enough? (was 20 seconds but I can't understand why)
                WebResource.setTimeout(5000)
                // because the above line causes initialisation set the is initialised flag here
                isSwordLoaded = true
                BookInstallWatcher.startListening()
            }
        } catch (e: Exception) {
            log.error("Error initialising", e)
        }
    }

    @Throws(BookException::class)
    fun enableDefaultAndManualInstallFolder() {
        Log.i("SwordInit", "enableDefaultAndManualInstallFolder: manual folders enabled!")
        CWProject.setHome("jsword.home", SharedConstants.modulesDir.absolutePath, SharedConstants.manualInstallDir.absolutePath)
        // the following causes Sword to initialise itself and can take quite a few seconds
		// add manual install dir to this list
        SwordBookPath.setAugmentPath(arrayOf(SharedConstants.manualInstallDir, SharedConstants.manualInstallDir2))
    }

    private val messageShownLast = LruCache<String, Long>(100);

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
                val msg = when {
                    ev == null -> getResourceString(R.string.error_occurred)
                    !StringUtils.isEmpty(ev.message) -> ev.message
                    ev.exception != null && !StringUtils.isEmpty(ev.exception.message) ->
                        getResourceString(R.string.error_occurred_with_link, ev.exception.message!!)
                    else -> getResourceString(R.string.error_occurred)
                }
                val lastShown = messageShownLast.get(msg)
                messageShownLast.put(msg, System.currentTimeMillis())
                if (lastShown != null && System.currentTimeMillis() - lastShown < FIVE_MINUTES_MS) {
                    return // Error message has been shown recently, let's not spam user!
                }
                // convert Throwable to Exception for Dialogs
                val e: Exception = if (ev != null) {
                    val th = ev.exception
                    if (th is Exception) th else Exception("Jsword Exception", th)
                } else {
                    Exception("JSword Exception")
                }
                Dialogs.showErrorMsg(msg, e)
            }
        })
    }
}
