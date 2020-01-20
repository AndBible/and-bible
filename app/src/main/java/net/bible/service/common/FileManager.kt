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

package net.bible.service.common
import android.util.Log
import net.bible.android.BibleApplication

import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.IOUtil

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

/**
 * File related utility methods
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object FileManager {

    private val DOT_PROPERTIES = ".properties"

    private val log = Logger(FileManager::class.java.name)

    fun copyFile(filename: String, fromDir: File, toDir: File): Boolean {
        log.debug("Copying:$filename")
        val ok: Boolean

        val fromFile = File(fromDir, filename)
        val targetFile = File(toDir, filename)

        ok = copyFile(fromFile, targetFile)

        return ok
    }

    fun copyFile(fromFile: File, toFile: File): Boolean {
        var ok = false
        try {
            // don't worry if tofile exists, allow overwrite
            if (fromFile.exists()) {
                //ensure the target dir exists or FileNotFoundException is thrown creating dst FileChannel
                val toDir = toFile.parentFile
                toDir.mkdir()

                val fromFileSize = fromFile.length()
                log.debug("Source file length:$fromFileSize")
                if (fromFileSize > CommonUtils.getFreeSpace(toDir.path)) {
                    // not enough room on SDcard
                    log.error("Not enough room on SD card")
                    ok = false
                } else {
                    // move the file
                    val src = FileInputStream(fromFile)
                    val dest = FileOutputStream(toFile, false)
                    try {
                        // Transfer bytes from in to out
                        val buf = ByteArray(1024)
                        var len: Int = src.read(buf)
                        while (len > 0) {
                            dest.write(buf, 0, len)
							len = src.read(buf)
                        }
                        ok = true
                    } finally {
                        src.close()
                        dest.close()
                    }
                }
            } else {
                // fromfile does not exist
                ok = false
            }
        } catch (e: Exception) {
            log.error("Error moving file", e)
        }

        return ok
    }

    /* Open a properties file from the assets folder
	 */
    fun readPropertiesFile(folder: String, filename: String): Properties {
        var filename = filename
        val returnProperties = Properties()

        val resources = BibleApplication.application.resources
        val assetManager = resources.assets
        if (!filename.endsWith(DOT_PROPERTIES)) {
            filename = filename + DOT_PROPERTIES
        }
        if (StringUtils.isNotEmpty(folder)) {
            filename = folder + File.separator + filename
        }

        // Read from the /assets directory
        var inputStream: InputStream? = null
        try {
            // check to see if a user has created his own reading plan with this name
            inputStream = assetManager.open(filename)

            returnProperties.load(inputStream)
            log.debug("The properties are now loaded from: $filename")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open property file:$filename")
            e.printStackTrace()
        } finally {
            IOUtil.close(inputStream)
        }
        return returnProperties
    }

    private const val TAG = "FileManager"
}
