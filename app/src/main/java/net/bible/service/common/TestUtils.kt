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

import net.bible.service.common.TestUtils

/** support junit tests
 *
 * @author denha1m
 */
object TestUtils {
    private var _isAndroid = false
    private var isAndroidCheckDone = false

    /** return true id running in an Android vm
     *
     * @return
     */
	val isAndroid: Boolean get(){
        if (!isAndroidCheckDone) {
            try {
                Class.forName("net.bible.test.TestEnvironmentFlag")
                _isAndroid = false
                println("Running as test")
            } catch (cnfe: ClassNotFoundException) {
                _isAndroid = true
                println("Running on Android")
            }
            isAndroidCheckDone = true
        }
        return _isAndroid
    }
}
