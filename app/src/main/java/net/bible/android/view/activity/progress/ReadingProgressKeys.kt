/*
 * Copyright (c) 2024-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.progress

/**
 * Reading-progress intent keys, lifted out of the about-to-be-deleted `ReadingProgressActivity`'s
 * companion (Batch Z-late phase 1) because `ReadingProgressComposeActivity` and
 * `BibleJavascriptInterface` both read them at the time.
 *
 * **Neither reader is left.** nav-graph 3/5/6 moved the screen into the Compose navigation graph,
 * where the tab travels IN the route (`NavRoutes.readingProgress(tab)`) rather than in an intent
 * extra: Task 8 rewrote `BibleJavascriptInterface.openReadingProgress` onto that route and Task 9
 * deleted `ReadingProgressComposeActivity` along with this import. As of Task 9 [EXTRA_TAB] has no
 * live reader or writer anywhere in the repo; its only consumer is `IntentKeysTest:43`, which pins
 * the key STRING so that a stored intent written by an older install still decodes to the same
 * name.
 *
 * The file is kept on purpose rather than deleted by a cleanup task: whether a key object with no
 * producer and no consumer should outlive the migration is a call for the maintainer at the
 * migration's epilogue, once the whole graph has landed — not one for the task that emptied it.
 */
object ReadingProgressKeys {
    const val EXTRA_TAB = "tab"
}
