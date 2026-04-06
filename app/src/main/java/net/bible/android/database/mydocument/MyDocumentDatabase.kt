/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.mydocument

import androidx.room.Database
import androidx.room.TypeConverters
import net.bible.android.database.Converters
import net.bible.android.database.LogEntry
import net.bible.android.database.SyncConfiguration
import net.bible.android.database.SyncStatus
import net.bible.android.database.SyncableRoomDatabase

const val MY_DOCUMENT_DATABASE_VERSION = 4

@Database(
    entities = [
        MyDocument::class,
        MyDocumentPage::class,
        MyDocumentPageContent::class,
        AiPageCacheEntry::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    views = [
        MyDocumentPageWithContent::class,
        AiCachedPageWithContent::class,
    ],
    version = MY_DOCUMENT_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class MyDocumentDatabase : SyncableRoomDatabase() {
    abstract fun myDocumentDao(): MyDocumentDao

    companion object {
        const val dbFileName = "mydocuments.sqlite3"
    }
}
