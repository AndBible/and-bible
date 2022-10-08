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

import net.bible.service.common.TitleSplitter
import org.apache.commons.lang3.StringUtils
import java.lang.StringBuilder

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class TitleSplitter {
    /**
     * Split text to enable 2 parts to be shown as title on left of action bar
     */
    fun split(text: String): Array<String> {
        // this is normally used for verses e.g. '1Cor 2:1' -> '1Cor','2:1'
        // Explained: there must be at least 3 chars before a space to split
        var parts: Array<String> = text.split("(?<=... )").toTypedArray()

        // this is normally used for module names e.g. 'GodsWord' -> 'Gods','Word'
        if (parts.size == 1) {
            parts = text.split("(?<=[a-z])(?=[A-Z0-9])").toTypedArray()
        }
        checkMaximumPartLength(parts)
        return parts
    }

    /**
     * Shorten camel case words uniformly e.g. StrongsRealGreek -> StReGr
     * Used to create short action bar text for document names
     *
     * @param text        Text to shorten
     * @param maxLength    Max length of final string
     * @return            Shortened text
     */
    fun shorten(text: String, maxLength: Int): String {
        if (text.length <= maxLength) {
            return text
        }
        // take characters from the end of each part until required length obtained
        val parts = text.split("(?<=[a-z])(?=[A-Z0-9 ])").toTypedArray()
        val numParts = parts.size
        if (numParts == 1) {
            return text.substring(0, maxLength)
        }

        // basicLength will be a bit short if the length of all parts is not going to be the same
        val basicSplitLength = maxLength / numParts
        val remaining = maxLength % numParts
        // add remaining to end parts because they are more specific 
        val startToAddRemainingFrom = numParts - remaining
        val result = StringBuilder()
        for (i in parts.indices) {
            var partLen = basicSplitLength
            if (i >= startToAddRemainingFrom) {
                partLen++
            }
            result.append(StringUtils.left(parts[i], partLen))
        }
        return result.toString()
    }

    private fun checkMaximumPartLength(parts: Array<String>, maximumLength: Int = MAX_PART_LENGTH): Array<String> {
        for (i in parts.indices) {
            if (parts[i].length > maximumLength) {
                parts[i] = StringUtils.left(parts[i], maximumLength)
            }
        }
        return parts
    }

    companion object {
        private const val MAX_PART_LENGTH = 6
    }
}
