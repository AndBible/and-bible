/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword

import androidx.appcompat.app.AppCompatActivity
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Book

/**
 * Slice 8 spec §4: the reading view never starts without at least one UNLOCKED Bible. This is the ONE
 * predicate; its three gates are `StartupActivity`'s start-route choice, the nav host's in-graph
 * Welcome -> reading transition, and the nav host's start-route / `onNewIntent` resolution. Without it the
 * reading view crashes: `WindowControl.defaultBibleDoc` falls back to `firstBibleDoc`, a `.first {}` over
 * the installed Bibles (finding M7).
 */
fun hasUsableBible(bibles: List<Book> = SwordDocumentFacade.bibles): Boolean = bibles.any { !it.isLocked }

/**
 * `StartupActivity.gotoMainBibleActivity`'s unlock attempt, shared by gates (a) and (b): when no Bible is
 * usable, ask to unlock each locked one. Suspends while each unlock dialog is up.
 */
suspend fun unlockLockedBiblesIfNoneUsable(
    activity: AppCompatActivity,
    usable: () -> Boolean = { hasUsableBible() },
    bibles: () -> List<Book> = { SwordDocumentFacade.bibles },
) {
    if (usable()) return
    for (bible in bibles().filter { it.isLocked }) {
        CommonUtils.unlockDocument(activity, bible)
    }
}
