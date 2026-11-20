package net.bible.shareddata

import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonCollectionModule
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonCollectionRow
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonLabelLocalState
import net.bible.android.database.bookmarks.addonLabelIds
import net.bible.service.db.openSqlite
import net.bible.shareddata.fixtures.CURRENT_DB_SPECS
import net.bible.shareddata.fixtures.createFromExport
import net.bible.shareddata.fixtures.rawRows
import net.bible.shareddata.fixtures.userVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bookmark schema 13 -> 14 adds the unsynced add-on collection tables and keeps the user's rows. */
class AddonCollectionSchemaTest {
    private val fqn = "net.bible.android.database.BookmarkDatabase"

    @Test
    fun migration13to14CreatesAddonTablesAndKeepsData() = runTest {
        val dir = testDatabaseDir()
        val name = "addon-schema.sqlite3"
        createFromExport(fqn, 13, "$dir/$name")
        openSqlite("$dir/$name").let { c ->
            try {
                c.execSQL("INSERT INTO Label (id,name,color) VALUES (X'01','Mine',5)")
            } finally { c.close() }
        }
        val db = CURRENT_DB_SPECS.first { it.fqn == fqn }.open(dir, name) as BookmarkDatabase
        try {
            assertEquals(14, db.userVersion())
            assertEquals(listOf(listOf("Mine")), db.rawRows("SELECT name FROM Label", 1))
            val tables = db.rawRows("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'Addon%' ORDER BY name", 1).map { it[0] }
            assertEquals(listOf("AddonCollectionModule", "AddonCollectionRow", "AddonLabelLocalState", "AddonLinkLocalState"), tables)

            val dao = db.addonCollectionDao()
            val label = IdType()
            val bookmark = IdType()
            dao.upsertModule(AddonCollectionModule("Manna", "1.0", "abc", 7))
            dao.insertRows(listOf(
                AddonCollectionRow("Manna", "Label", label, null),
                AddonCollectionRow("Manna", "BibleBookmark", bookmark, null),
                AddonCollectionRow("Manna", "BibleBookmarkToLabel", bookmark, label),
            ))
            assertTrue(dao.isAddonLabel(label))
            assertFalse(dao.isAddonLabel(bookmark))
            assertTrue(dao.isAddonBookmark(bookmark))
            assertTrue(dao.isAddonLink("BibleBookmarkToLabel", bookmark, label))
            assertFalse(dao.isAddonLink("BibleBookmarkToLabel", bookmark, IdType()))
            assertEquals("Manna", dao.moduleOfLabel(label))
            assertNull(dao.moduleOfLabel(IdType()))
            assertEquals(setOf(label), dao.addonLabelIds())
            assertEquals("1.0", dao.module("Manna")!!.version)

            dao.upsertLabelLocalState(AddonLabelLocalState(label, favourite = true))
            assertEquals(listOf(label), dao.favouriteAddonLabelIds())

            dao.deleteRowsOf("Manna")
            dao.deleteModule("Manna")
            assertTrue(dao.rowsOf("Manna").isEmpty())
            assertTrue(dao.allModules().isEmpty())
        } finally {
            db.close()
        }
    }
}
