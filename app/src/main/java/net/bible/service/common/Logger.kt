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
package net.bible.service.common

import android.util.Log
import net.bible.service.common.TestUtils
import java.lang.Exception

/** Not sure whether to use Log or jdk logger or log4j.
 * Log requires the android classes and is used for front end classes but these classes really belong in the back end
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Logger(private val name: String) {
    fun debug(s: String) {
        if (isAndroid) {
            Log.i(name, s)
        } else {
            println("$name:$s")
        }
    }

    fun info(s: String) {
        if (isAndroid) {
            Log.i(name, s)
        } else {
            println("$name:$s")
        }
    }

    fun warn(s: String) {
        if (isAndroid) {
            Log.w(name, s)
        } else {
            println("$name:$s")
        }
    }

    fun warn(s: String, e: Exception) {
        if (isAndroid) {
            Log.e(name, s, e)
        } else {
            println("$name:$s")
            e.printStackTrace()
        }
    }

    fun error(s: String) {
        if (isAndroid) {
            Log.e(name, s)
        } else {
            println("$name:$s")
        }
    }

    fun error(s: String, e: Exception) {
        if (isAndroid) {
            Log.e(name, s, e)
        } else {
            println("$name:$s")
            e.printStackTrace()
        }
    }

    companion object {
        private val isAndroid = TestUtils.isAndroid
    }
}
