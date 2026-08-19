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

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The repo's first migration test. It builds a real version-12 `bookmarks.sqlite3` from the
 * checked-in Room schema export, seeds every combination the six old style booleans could hold,
 * opens it through Room (which runs the 12->13 migration and then validates the resulting schema
 * against the v13 entities), and checks what the two enum columns ended up holding.
 *
 * Why the whole v12 schema and not just the Label table: Room validates the complete post-migration
 * schema and throws "Migration didn't properly handle" on any mismatch. Feeding it a partial
 * database would skip exactly the check that catches a wrong column type or default.
 *
 * Why all 64 combinations and not the four canonical triples: the precedence rule
 * (`hide > marker > underline > highlight`) now lives in the migration's SQL, and only the
 * non-canonical rows — a dominated tick classic greyed out but never cleared — exercise it. The
 * whole 8x8 cross is cheap and leaves no combination a real database could hold untested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarkDatabaseMigration12To13Test {
    private lateinit var dbFile: File
    private var db: BookmarkDatabase? = null

    /** All eight (hide, marker, underline) triples one v12 axis could hold. */
    private val allTriples: List<Triple<Int, Int, Int>> = buildList {
        for (hide in 0..1) for (marker in 0..1) for (underline in 0..1) add(Triple(hide, marker, underline))
    }

    /**
     * The reader's precedence (`bibleview-js/src/composables/bookmarks.ts:583-604`), restated here
     * deliberately rather than shared with the migration, so this test pins the mapping instead of
     * agreeing with the migration's own CASE cascade.
     */
    private fun expectedStyle(hide: Boolean, marker: Boolean, underline: Boolean) = when {
        hide -> BookmarkDisplayStyle.HIDDEN
        marker -> BookmarkDisplayStyle.MARKER
        underline -> BookmarkDisplayStyle.UNDERLINE
        else -> BookmarkDisplayStyle.HIGHLIGHT
    }

    private fun rowName(selection: Triple<Int, Int, Int>, wholeVerse: Triple<Int, Int, Int>) =
        "sel${selection.first}${selection.second}${selection.third}" +
            "-wv${wholeVerse.first}${wholeVerse.second}${wholeVerse.third}"

    @Before
    fun setUp() {
        dbFile = File.createTempFile("bookmarks-v12-", ".sqlite3").also { it.delete() }
        createVersion12Database(dbFile)
    }

    @After
    fun tearDown() {
        db?.close()
        dbFile.delete()
    }

    /** Executes every CREATE from the committed v12 schema export and stamps user_version = 12. */
    private fun createVersion12Database(file: File) {
        // Unit tests run with the module directory (app/) as the working directory — the same
        // assumption HeatGridColumnsTest and ImePaddingPredicateDriftTest already rely on.
        val schema = Json.parseToJsonElement(
            File("schemas/net.bible.android.database.BookmarkDatabase/12.json").readText()
        ).jsonObject["database"]!!.jsonObject
        val statements = buildList {
            schema["entities"]!!.jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                add(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { index ->
                    add(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]?.jsonArray?.forEach { view ->
                val v = view.jsonObject
                add(v["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", v["viewName"]!!.jsonPrimitive.content))
            }
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            statements.forEach { raw.execSQL(it) }
            allTriples.forEach { selection ->
                allTriples.forEach { wholeVerse ->
                    insertLabel(raw, rowName(selection, wholeVerse), selection, wholeVerse)
                }
            }
            raw.execSQL("PRAGMA user_version = 12")
        }
    }

    private fun insertLabel(raw: SQLiteDatabase, name: String, selection: Triple<Int, Int, Int>, wholeVerse: Triple<Int, Int, Int>) {
        raw.execSQL(
            """
            INSERT INTO Label (id, name, color, markerStyle, markerStyleWholeVerse, underlineStyle,
                               underlineStyleWholeVerse, hideStyle, hideStyleWholeVerse, favourite, type, customIcon)
            VALUES (randomblob(16), ?, 0, ?, ?, ?, ?, ?, ?, 0, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any?>(name, selection.second, wholeVerse.second, selection.third,
                    wholeVerse.third, selection.first, wholeVerse.first),
        )
    }

    private fun openMigrated(): BookmarkDatabase =
        Room.databaseBuilder(application, BookmarkDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .addMigrations(*bookmarkMigrations)
            .build()
            .also { db = it; it.openHelper.writableDatabase }

    private fun styles(): Map<String, Pair<Int, Int?>> {
        val out = mutableMapOf<String, Pair<Int, Int?>>()
        db!!.openHelper.readableDatabase
            .query("SELECT name, displayStyle, displayStyleWholeVerse FROM Label").use { c ->
                while (c.moveToNext()) {
                    out[c.getString(0)] = c.getInt(1) to if (c.isNull(2)) null else c.getInt(2)
                }
            }
        return out
    }

    @Test
    fun `every legacy flag combination migrates to the style the reader already drew`() {
        openMigrated()
        val rows = styles()
        assertEquals(64, rows.size)
        for (selection in allTriples) {
            for (wholeVerse in allTriples) {
                val key = rowName(selection, wholeVerse)
                val expectedSelection =
                    expectedStyle(selection.first == 1, selection.second == 1, selection.third == 1)
                val expectedWholeVerse =
                    expectedStyle(wholeVerse.first == 1, wholeVerse.second == 1, wholeVerse.third == 1)
                // NULL means "inherit the selection style", which is exactly the case where the two
                // axes drew the same thing — so it must appear for precisely those rows.
                val expectedColumn: Int? =
                    if (expectedWholeVerse == expectedSelection) null else expectedWholeVerse.ordinal
                assertEquals("displayStyle for $key", expectedSelection.ordinal, rows[key]!!.first)
                assertEquals("displayStyleWholeVerse for $key", expectedColumn, rows[key]!!.second)
            }
        }
    }

    @Test
    fun `a dominated flag collapses to what the reader already drew`() {
        openMigrated()
        val rows = styles()
        // hide + underline was drawn as hidden; marker + underline was drawn as a marker. Classic
        // greyed the dominated tick out but never cleared it, so real databases contain such rows.
        val hideRow = rowName(Triple(1, 0, 1), Triple(1, 0, 1))
        assertEquals(BookmarkDisplayStyle.HIDDEN.ordinal, rows[hideRow]!!.first)
        assertNull(rows[hideRow]!!.second)
        val markerRow = rowName(Triple(0, 1, 1), Triple(0, 1, 1))
        assertEquals(BookmarkDisplayStyle.MARKER.ordinal, rows[markerRow]!!.first)
        assertNull(rows[markerRow]!!.second)
    }

    @Test
    fun `the six legacy columns are gone and the new ones carry the right shape`() {
        openMigrated()
        val columns = mutableMapOf<String, Pair<Int, String?>>()   // name -> notnull, default
        db!!.openHelper.readableDatabase.query("PRAGMA table_info(Label)").use { c ->
            while (c.moveToNext()) {
                columns[c.getString(1)] = c.getInt(3) to c.getString(4)
            }
        }
        listOf("markerStyle", "markerStyleWholeVerse", "underlineStyle",
               "underlineStyleWholeVerse", "hideStyle", "hideStyleWholeVerse").forEach {
            assertFalse("$it should have been dropped", columns.containsKey(it))
        }
        assertEquals(1 to "0", columns["displayStyle"])
        assertEquals(0 to "1", columns["displayStyleWholeVerse"])
        assertEquals(13, db!!.openHelper.readableDatabase.version)
    }
}
