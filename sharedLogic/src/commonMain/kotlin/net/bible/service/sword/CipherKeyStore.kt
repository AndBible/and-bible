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
package net.bible.service.sword

import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SwordDocumentInfo
import org.crosswire.jsword.book.Book

/**
 * Persists the passphrase the user gave for an enciphered [book], so [net.bible.service.startup.ReadingAppInit.unlockStoredCipherKeys]
 * can unlock it in the next process. The only writer of `SwordDocumentInfo.cipherKey` from an unlock dialog (Android and iOS).
 *
 * The row is created when missing: an installed module can have none (database reset or restored while the modules stayed),
 * and a key written only to an existing row was silently lost. An existing row keeps its other fields.
 *
 * Decision: the write goes through [BookInstallWatcher.updateRow], not a direct DAO read-then-update. A direct write could run
 * before a still-queued install insert (row missing at that moment, key lost) or race the reinstall's snapshot of the retained
 * fields; the watcher's ordered `book-install` queue is the documented way to amend a row after an install
 * (see [BookInstallWatcher.launchRowUpdate]).
 */
suspend fun persistCipherKey(book: Book, key: String, watcher: BookInstallWatcher) {
    watcher.updateRow(book.initials) { existing ->
        existing?.copy(cipherKey = key)
            ?: SwordDocumentInfo(book.initials, book.name, book.abbreviation, book.language.name, "", key)
    }
}
