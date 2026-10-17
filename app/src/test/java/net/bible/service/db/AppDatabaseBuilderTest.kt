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

package net.bible.service.db

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SETTINGS_DATABASE_VERSION
import net.bible.android.database.SettingsDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Characterizes the context-less Room 3 builder that [buildAppDatabase] uses against the context builder it
 * replaces, and the file-system behaviour the container's open order relies on (D2 Task 2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AppDatabaseBuilderTest {
    private lateinit var originalHost: DatabaseHost
    private val scratchDirs = mutableListOf<File>()

    @Before fun setUp() {
        originalHost = DatabaseContainer.host
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        if (::originalHost.isInitialized) DatabaseContainer.installHost(originalHost)
        scratchDirs.forEach { it.deleteRecursively() }
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    private fun scratchDir(): File = File(application.cacheDir, "builder-test-${System.nanoTime()}").also { scratchDirs += it }

    @Test fun contextLessBuilderUsesTheSameFileAsTheContextBuilder() {
        val name = "builder-characterization.sqlite3"
        val file = application.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val viaContext = Room.databaseBuilder<SettingsDatabase>(application, name).productionConfig().build()
        runBlocking { viaContext.stringSettingDao().set("k", "v") }
        viaContext.close()

        val viaPath = Room.databaseBuilder<SettingsDatabase>(name = file.path).productionConfig().build()
        try {
            assertEquals("v", runBlocking { viaPath.stringSettingDao().get("k", null) })
        } finally {
            viaPath.close()
        }
        assertEquals(SETTINGS_DATABASE_VERSION, readUserVersion(file))
        assertTrue("Room's lock file sits beside the database", File(file.path + ".lck").exists())
    }

    @Test fun buildAppDatabaseCreatesAMissingParentDirectory() {
        val dir = File(scratchDir(), "missing/databases")
        assertFalse(dir.exists())
        DatabaseContainer.installHost(object : DatabaseHost by originalHost {
            override fun databasePath(name: String) = File(dir, name).path
        })
        val db = buildAppDatabase<SettingsDatabase>("x.sqlite3")
        try {
            runBlocking { db.stringSettingDao().get("none", null) }
        } finally {
            db.close()
        }
        assertTrue(File(dir, "x.sqlite3").isFile)
    }

    @Test fun buildingOpensNothing() {
        val dir = scratchDir()
        DatabaseContainer.installHost(object : DatabaseHost by originalHost {
            override fun databasePath(name: String) = File(dir, name).path
        })
        val db = buildAppDatabase<SettingsDatabase>("lazy.sqlite3")
        try {
            assertFalse(File(dir, "lazy.sqlite3").exists())
        } finally {
            db.close()
        }
    }
}
