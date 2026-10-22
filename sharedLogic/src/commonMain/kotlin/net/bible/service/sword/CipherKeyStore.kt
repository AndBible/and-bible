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

import net.bible.android.database.SwordDocumentInfo
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Book

/**
 * Persists the passphrase the user gave for an enciphered [book], so [net.bible.service.startup.ReadingAppInit.unlockStoredCipherKeys]
 * can unlock it in the next process.
 *
 * The row is created when missing: an installed module can have none (database reset or restored while the modules stayed),
 * and a key written only to an existing row was silently lost. An existing row keeps its other fields.
 */
suspend fun persistCipherKey(book: Book, key: String) {
    val dao = DatabaseContainer.openForUse().repoDb.swordDocumentInfoDao()
    val existing = dao.getBook(book.initials)
    if (existing != null) {
        dao.update(existing.apply { cipherKey = key })
    } else {
        dao.insert(SwordDocumentInfo(book.initials, book.name, book.abbreviation, book.language.name, "", key))
    }
}
