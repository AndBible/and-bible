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

package net.bible.android.database

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the schema identity hash of every Room database at its current version, so a Room or
 * codegen upgrade cannot silently change what a fresh install (and cross-platform sync) sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SchemaIdentityTest {
    // Literals copied from app/schemas/<db>/<current>.json at D1 start (2026-10-08). Do not regenerate.
    private val pinned = mapOf(
        "net.bible.android.database.BookmarkDatabase" to "2c61bc5841977a8107fe17de4db1713d",
        "net.bible.android.database.ReadingPlanDatabase" to "d465b2a4bc2012fff3a69d3eaff9b5ff",
        "net.bible.android.database.WorkspaceDatabase" to "59b8635a1eb5125e32e2789eedd02ab2",
        "net.bible.android.database.mydocument.MyDocumentDatabase" to "3f0946602099d896c8d47129233c1794",
        "net.bible.android.database.AiSettingsDatabase" to "c5b1820fd3dfb0390fa3122d2d6e139f",
        "net.bible.android.database.progress.ProgressDatabase" to "76330d8367020840e56e6b92d921522a",
        "net.bible.android.database.TemporaryDatabase" to "8642ce470ef18897b4a8ee82dbf1bf82",
        "net.bible.android.database.DocumentSyncDatabase" to "c18e038b74d4446605e2b99f1cee1533",
        "net.bible.android.database.RepoDatabase" to "c1bf74115c370f8176d77ec922e6a1f9",
        "net.bible.android.database.SettingsDatabase" to "766325557c50c33c7c4f6857d0cba8ad",
        "net.bible.android.database.EpubDatabase" to "b5106d138f8ff91075378a776b0cbb21",
    )

    @Test
    fun everyDatabaseUnderTestHasAPinnedHash() {
        assertEquals(DB_UNDER_TEST.map { it.schemaDir }.toSet(), pinned.keys)
    }

    @Test
    fun freshDatabasesCarryThePinnedIdentityHash() {
        for (db in DB_UNDER_TEST) {
            val name = "identity-${db.schemaDir}.sqlite3"
            application.deleteDatabase(name)
            val room = db.open(name)
            val hash = room.identityHash()   // Room 3 databases read it through useReaderConnection
            room.close()
            assertEquals(db.schemaDir, pinned.getValue(db.schemaDir), hash)
            assertEquals(db.schemaDir, pinned.getValue(db.schemaDir), SchemaExportFixtures.identityHash(db.schemaDir, db.currentVersion))
        }
    }
}
