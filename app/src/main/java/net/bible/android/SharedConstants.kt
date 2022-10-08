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
import java.io.File

/** Not used much yet but need to move the some of the more generic constants here
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SharedConstants {
    const val REQUIRED_MEGS_FOR_DOWNLOADS: Long = 50

    private const val MANUAL_INSTALL_SUBDIR = "jsword"
    private const val MANUAL_INSTALL_SUBDIR2 = "sword"
    val RECOMMENDED_JSON = "recommended_documents_v2.json"
    val DEFAULT_JSON = "default_documents_v2.json"
    val PSEUDO_BOOKS = "pseudo_books.json"
    val MODULE_DIR = moduleDir
    val MANUAL_INSTALL_DIR get() = manualInstallDir
    val MANUAL_INSTALL_DIR2 get() = File(Environment.getExternalStorageDirectory(), MANUAL_INSTALL_SUBDIR2)

    private const val FONT_SUBDIR_NAME = "fonts"
    val FONT_DIR = File(MODULE_DIR, FONT_SUBDIR_NAME)
    val MANUAL_FONT_DIR = File(MANUAL_INSTALL_DIR, FONT_SUBDIR_NAME)
    const val READINGPLAN_DIR_NAME = "readingplan"
    val MANUAL_READINGPLAN_DIR = File(MANUAL_INSTALL_DIR, READINGPLAN_DIR_NAME)
    val LINE_SEPARATOR = System.getProperty("line.separator")

    val INTERNAL_MODULE_DIR get() = File(application.filesDir, "modules")

    private val moduleDir: File
        get() = application.getExternalFilesDir(null) ?: INTERNAL_MODULE_DIR


    private val manualInstallDir: File
        get() {
            val sdcard = Environment.getExternalStorageDirectory()
            return File(sdcard, MANUAL_INSTALL_SUBDIR)
        }

}
