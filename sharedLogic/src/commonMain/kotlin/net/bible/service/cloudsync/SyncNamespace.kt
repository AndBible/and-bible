/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync

/**
 * The sync namespace shared by every release distribution of the common code base.
 *
 * Per the I4 release design (spec §5.6), from the first common beta the Android standard, Android discrete,
 * Accrescent and iOS builds all sync into one folder on each shared backend, so a user can move between
 * distributions without losing data. The namespace is Android's current id, so existing Android standard users
 * keep their folder names. A release id maps to [SHARED]; an id made of a release id plus a dot-separated suffix
 * (`.debug`, `.dev`, `.next`, ...) maps to [SHARED] plus the same suffix, so developer and tester builds stay in
 * their own folder and can still share with each other across platforms. Any other id is its own namespace.
 */
object SyncNamespace {
    /** The namespace all release distributions share. */
    const val SHARED = "net.bible.android.activity"

    /** App ids of the release distributions that use [SHARED]. */
    val RELEASE_IDS: List<String> = listOf(
        "net.bible.android.activity",
        "com.app.calculator",
        "org.andbible.andbible",
        "org.andbible.ios",
    )

    /**
     * Returns the sync namespace for an app id: [SHARED] for a release id, [SHARED] plus the suffix for a release
     * id with a `.suffix`, and the id itself otherwise. The longest matching release id wins.
     */
    fun forAppId(appId: String): String {
        for (releaseId in RELEASE_IDS.sortedByDescending { it.length }) {
            if (appId == releaseId) return SHARED
            if (appId.startsWith("$releaseId.")) return SHARED + appId.substring(releaseId.length)
        }
        return appId
    }
}
