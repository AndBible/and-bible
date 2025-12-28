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

import org.apache.commons.lang3.StringUtils

object ABStringUtils : StringUtils() {
    /** It doesn't make sense to say a string is not all uppercase just becasue it contains characters like numbers that can't be uppercase
     *
     * @param cs
     * @return
     */
    fun isAllUpperCaseWherePossible(cs: CharSequence): Boolean {
        if (isEmpty(cs)) {
            return false
        }
        val sz = cs.length
        for (i in 0 until sz) {
            val ch = cs[i]
            if (Character.isLowerCase(ch)) {
                return false
            }
        }
        return true
    }
}
