/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedlogic.platform.host

import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.Path
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.index.fts.SearchFields
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.PassageType

/**
 * iOS counterpart of `SwordEnvironmentInitialisation.initialiseJSwordFolders`: creates the module folders and
 * points JSword at them. Runs before JSword is touched; a repeated call is a no-op. There are no manually installed
 * (external storage) folders on iOS.
 */
object HostSwordEnvironment {
    @kotlin.concurrent.Volatile
    private var loaded = false

    /** True once [initialiseJSwordFolders] has set JSword up. */
    val isLoaded: Boolean get() = loaded

    /** Creates the module folder and its `mods.d`, `modules` and `search` subfolders (idempotent); no JSword state is touched. */
    fun createModuleFolders(modulesDir: Path, internalFilesDir: Path? = null) {
        val files = FileSystem.SYSTEM
        net.bible.service.installzip.recoverInterruptedSwordInstalls(modulesDir, internalFilesDir)
        listOf(modulesDir, modulesDir / SwordConstants.DIR_CONF, modulesDir / SwordConstants.DIR_DATA, modulesDir / SearchFields.DIR_SEARCH)
            .forEach { files.createDirectories(it) }
    }

    /**
     * Sets JSword up. A failure is logged and startup goes on without books (the iOS behaviour) unless
     * [rethrowFailures] is true (desktop, which shows a startup failure screen instead).
     */
    fun initialiseJSwordFolders(modulesDir: Path, rethrowFailures: Boolean = false, internalFilesDir: Path? = null) {
        if (loaded) return
        try {
            createModuleFolders(modulesDir, internalFilesDir)
            // Optimize for less memory
            PassageKeyFactory.setDefaultType(PassageType.MIX)
            CWProject.instance().setFrontendName("and-bible")
            CWProject.setHome("jsword.home", modulesDir.toString(), null)
            Log.d(TAG, "Main JSword path: ${CWProject.instance().writableProjectDir}")
            // This host owns its module store. Default discovery also migrates the CWProject home to ~/.sword,
            // moving installed modules out of Documents/sword (and outside the fresh-install reset).
            // Set the download path before registration: setAugmentPath immediately scans the driver.
            SwordBookPath.scanDefaultLocations = false
            SwordBookPath.setDownloadDir(modulesDir)
            SwordBookPath.setAugmentPath(arrayOf(modulesDir))
            WebResource.setTimeout(5000)
            loaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising JSword folders", e)
            if (rethrowFailures) throw e
        }
    }

    private const val TAG = "IosSwordEnvironment"
}
