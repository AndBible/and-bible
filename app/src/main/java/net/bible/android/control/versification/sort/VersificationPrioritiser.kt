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
package net.bible.android.control.versification.sort

import org.crosswire.jsword.versification.Versification
import java.util.*

/**
 * Using the list of preferred v11ns calculated from the total list of verses passed in
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
internal class VersificationPrioritiser(verseRangeUsers: List<VerseRangeUser>) {
    val prioritisedVersifications: List<Versification> = prioritiseVersifications(getVersifications(verseRangeUsers))

    private fun getVersifications(verseRangeUsers: List<VerseRangeUser>): List<Versification> {
        val versifications: MutableList<Versification> = ArrayList()
        for (cvru in verseRangeUsers) {
            try {
                versifications.add(cvru.verseRange.versification)
            } catch (e: KotlinNullPointerException) {
                versifications.add(Versification())
            }
        }
        return versifications
    }

    private fun prioritiseVersifications(versifications: List<Versification>): List<Versification> {
        val map: MutableMap<Versification, Int> = HashMap()

        // count the occurrences of each versification
        for (versification in versifications) {
            val count = map[versification]
            map[versification] = if (count == null) 1 else count + 1
        }

        // sort by occurrences
        val entries: List<Map.Entry<Versification, Int>> = ArrayList<Map.Entry<Versification, Int>>(map.entries)
        Collections.sort(entries) { o1, o2 -> o2.value - o1.value }

        // extract v11ns
        val sortedVersifications: MutableList<Versification> = ArrayList()
        for ((key) in entries) {
            sortedVersifications.add(key)
        }
        return sortedVersifications
    }
}
