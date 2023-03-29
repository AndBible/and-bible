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
package net.bible.android

import android.os.Environment
import net.bible.android.BibleApplication.Companion.application
import net.bible.service.common.BuildVariant
import java.io.File

/** Not used much yet but need to move the some of the more generic constants here
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SharedConstants {
    val LINE_SEPARATOR = System.getProperty("line.separator")
    const val REQUIRED_MEGS_FOR_DOWNLOADS: Long = 50
    const val BAD_DOCS_JSON = "bad_documents.json"
    const val RECOMMENDED_JSON = "recommended_documents_v2.json"
    const val DEFAULT_JSON = "default_documents_v2.json"
    const val PSEUDO_BOOKS = "pseudo_books.json"
    const val READINGPLAN_DIR_NAME = "readingplan"

    private const val MANUAL_INSTALL_SUBDIR = "jsword"
    private const val MANUAL_INSTALL_SUBDIR2 = "sword"

    val internalFilesDir: File get() = application.filesDir
    private val internalModulesDir: File get() = File(internalFilesDir, "modules")

    val modulesDir: File get() =
        if (BuildVariant.Appearance.isDiscrete) {
            internalModulesDir
        } else {
            application.getExternalFilesDir(null) ?: internalModulesDir
        }

    val manualReadingPlanDir: File get() = File(manualInstallDir, READINGPLAN_DIR_NAME)
    val manualInstallDir: File get() = File(Environment.getExternalStorageDirectory(), MANUAL_INSTALL_SUBDIR)
    val manualInstallDir2: File get() = File(Environment.getExternalStorageDirectory(), MANUAL_INSTALL_SUBDIR2)
}
