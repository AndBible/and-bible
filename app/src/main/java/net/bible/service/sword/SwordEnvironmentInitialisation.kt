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
import net.bible.sharedcore.log.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.platform.SuspendHttpTransport
import net.bible.sharedcore.http.DownloadHttp
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils.ensureDirExists
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.isAndroid
import org.apache.commons.lang3.StringUtils
import org.koin.java.KoinJavaComponent.get
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.Reporter
import org.crosswire.common.util.ReporterEvent
import org.crosswire.common.util.ReporterListener
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.fts.FtsIndexManager
import org.crosswire.jsword.index.fts.SearchFields
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.PassageType
import java.io.File
import kotlin.concurrent.thread

private const val FIVE_MINUTES_MS: Long = 1000 * 60 * 5
/**
 * Create directories required by JSword and set required JSword configuration.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SwordEnvironmentInitialisation {
    private var isSwordLoaded = false
    private val sweepStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val tag = SwordEnvironmentInitialisation::class.java.name
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
                ensureDirExists(File(moduleDir, SearchFields.DIR_SEARCH))
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
                Log.d(tag, "Main JSword path:" + CWProject.instance().writableProjectDir)
                // 5 sec should be more than enough? (was 20 seconds but I can't understand why)
                WebResource.setTimeout(5000)
                // because the above line causes initialisation set the is initialised flag here
                isSwordLoaded = true
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initialising", e)
        }
    }

    /**
     * Deletes the legacy Lucene index directories (`lucene/<driver>/<initials>`) of documents that are no longer
     * installed, once, on a background thread. Directories of installed documents are deliberately kept: they tell
     * the app that the search engine changed and the document's search index must be created again; each one is
     * deleted when that rebuild finishes (inside `FtsIndexManager`) or when the document is deleted.
     * A no-op if JSword was never initialised, and runs at most once per process.
     *
     * Ordering: must be called only after EVERY installed book is registered (the SWORD books, the manually installed
     * MyBible/MySword/eSword/EPUB/TTF/CSV books and the my-documents), otherwise those books look like orphans and
     * lose their legacy directory. `CommonUtils.initializeApp` / `initializeAppCoroutine` call it right after the last
     * registration; `BibleApplication.onCreate` is too early.
     */
    fun deleteOrphanLegacyIndexesInBackground() {
        if (!isSwordLoaded || !sweepStarted.compareAndSet(false, true)) return
        thread(name = "legacy-index-sweep", isDaemon = true) {
            try {
                val manager = IndexManagerFactory.getIndexManager() as? FtsIndexManager ?: return@thread
                val deleted = manager.deleteOrphanLegacyLuceneIndexes(Books.installed().getBooks())
                if (deleted > 0) Log.i(tag, "Deleted $deleted orphan legacy Lucene index directories")
            } catch (e: Exception) {
                Log.e(tag, "Legacy Lucene index sweep failed", e)
            }
        }
    }

    /** Installs the shared download transport after Koin starts, before any download consumer is resolved. */
    fun installDownloadTransport() {
        WebResource.transport = SuspendHttpTransport(get<DownloadHttp>(DownloadHttp::class.java))
    }

    /**
     * Registers the book install/uninstall listener (backup db + sync). Must run AFTER Koin has started
     * (the watcher is Koin-resolved) and after [initialiseJSwordFolders]; a no-op if JSword was never
     * initialised. Idempotent: [BookInstallWatcher.startListening] never registers twice.
     */
    fun startBookInstallWatcher() {
        if (!isSwordLoaded) return
        try {
            get<BookInstallWatcher>(BookInstallWatcher::class.java).startListening()
        } catch (e: Exception) {
            // Not rethrown (must not stop app startup), but never silent: without it no installed book is backed up.
            Log.e(tag, "BookInstallWatcher failed to start; book installs will not be tracked", e)
        }
    }

    @Throws(BookException::class)
    fun enableDefaultAndManualInstallFolder() {
        Log.i("SwordInit", "enableDefaultAndManualInstallFolder: manual folders enabled!")
        CWProject.setHome("jsword.home", SharedConstants.modulesDir.absolutePath, SharedConstants.manualInstallDir.absolutePath)
        // the following causes Sword to initialise itself and can take quite a few seconds
		// add manual install dir to this list
        SwordBookPathJvm.setAugmentPath(arrayOf(SharedConstants.manualInstallDir, SharedConstants.manualInstallDir2))
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
                    !StringUtils.isEmpty(ev.exception?.message) ->
                        getResourceString(R.string.error_occurred_with_link, ev.exception?.message!!)
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
