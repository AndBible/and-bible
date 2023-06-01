/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.android

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseCategory

import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseDefinition
import net.bible.service.db.DatabasePatching
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Test
import java.io.File

@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabasePatchingTests {
    @Test
    fun basicDatabaseTest() {
        DatabaseContainer.ready = true
        DatabaseContainer.instance
        val bmarkFile = File.createTempFile("bookmarks1-", ".sqlite3", CommonUtils.tmpDir)
        var bmarkDb = DatabaseContainer.instance.getBookmarkDb(bmarkFile.absolutePath)
        DatabasePatching.createBookmarkTriggers(bmarkDb.openHelper.writableDatabase)

        bmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 1"))
        bmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "label 2"))
        assertThat(bmarkDb.syncDao().allLogEntries().size, equalTo(2))
        val dbDef = DatabaseDefinition(
            bmarkDb,
            {DatabaseContainer.instance.getBookmarkDb(it)},
            {
                bmarkDb.close()
                bmarkDb = DatabaseContainer.instance.getBookmarkDb(bmarkFile.absolutePath)
                bmarkDb
            },
            bmarkFile,
            DatabaseCategory.BOOKMARKS,
        )
        val patchFile = DatabasePatching.createPatchForDatabase(dbDef)
    }
}
