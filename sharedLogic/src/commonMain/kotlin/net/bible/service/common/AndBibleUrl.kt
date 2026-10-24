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

package net.bible.service.common

/** The shareable link of a passage or document key (`https://read.andbible.org/<key>?document=..&v11n=..&ordinal=..`). */
fun andBibleUrl(
    keyStr: String,
    docInitials: String? = null,
    v11n: String? = null,
    ordinal: Int? = null,
): String {
    var url = "https://read.andbible.org/$keyStr"
    val queryParameters = mutableListOf<String>()
    if (docInitials != null) queryParameters.add("document=$docInitials")
    if (v11n != null) queryParameters.add("v11n=$v11n")
    if (ordinal != null) queryParameters.add("ordinal=$ordinal")
    if (queryParameters.isNotEmpty()) url += "?${queryParameters.joinToString("&")}"
    return url
}
