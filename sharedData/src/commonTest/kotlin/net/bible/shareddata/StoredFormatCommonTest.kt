package net.bible.shareddata

import kotlinx.coroutines.test.runTest
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.Converters
import net.bible.android.database.DbDate
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.PromptContext
import net.bible.shareddata.fixtures.rawBlob
import net.bible.shareddata.fixtures.rawExec
import net.bible.shareddata.fixtures.rawLong
import net.bible.shareddata.fixtures.rawText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reads the raw columns with SQL, bypassing the converters, so a change of a stored value (epoch millis, negative
 * ARGB Int, 16 id bytes, JSON enum sets) fails here even if write and read stay symmetric.
 */
class StoredFormatCommonTest {
    @Test
    fun dateColourIdAndOrdinalAreStoredBitIdentical() = runTest {
        withDb<BookmarkDatabase> { db ->
            val dao = db.bookmarkDao()
            dao.insert(
                BookmarkEntities.Label(
                    id = FIXED_ID, name = "L", color = NEGATIVE_ARGB, displayStyle = BookmarkDisplayStyle.UNDERLINE,
                    displayStyleWholeVerse = BookmarkDisplayStyle.HIDDEN,
                )
            )
            dao.insert(
                BookmarkEntities.BibleBookmark(
                    kjvOrdinalStart = 1, kjvOrdinalEnd = 1, ordinalStart = 1, ordinalEnd = 1, v11n = KJVA,
                    playbackSettings = null, id = OTHER_ID, createdAt = FIXED_DATE, startOffset = null, endOffset = null,
                    lastUpdatedOn = DbDate(-5L), customIcon = null,
                )
            )

            // IdType: exactly the 16 big-endian bytes, as a BLOB.
            assertContentEquals(
                ByteArray(16) { (it * 0x11).toByte() },
                db.rawBlob("SELECT id FROM Label"),
            )
            assertEquals(FIXED_ID_HEX, db.rawText("SELECT hex(id) FROM Label"))
            assertEquals("blob", db.rawText("SELECT typeof(id) FROM Label"))
            // Colour: the negative signed Int, not an unsigned value.
            assertEquals(NEGATIVE_ARGB.toLong(), db.rawLong("SELECT color FROM Label"))
            assertEquals(true, NEGATIVE_ARGB < 0)
            // BookmarkDisplayStyle: its ordinal.
            assertEquals(1L, db.rawLong("SELECT displayStyle FROM Label"))
            assertEquals(3L, db.rawLong("SELECT displayStyleWholeVerse FROM Label"))
            // Dates: epoch millis as INTEGER (negative values keep their sign).
            assertEquals(FIXED_DATE_MILLIS, db.rawLong("SELECT createdAt FROM BibleBookmark"))
            assertEquals("integer", db.rawText("SELECT typeof(createdAt) FROM BibleBookmark"))
            assertEquals(-5L, db.rawLong("SELECT lastUpdatedOn FROM BibleBookmark"))
            // Versification: its name.
            assertEquals("KJVA", db.rawText("SELECT v11n FROM BibleBookmark"))
        }
    }

    @Test
    fun workspaceColourAndJsonColumnsKeepTheirStoredShape() = runTest {
        withDb<WorkspaceDatabase> { db ->
            val settings = WorkspaceEntities.WorkspaceSettings(
                autoAssignLabels = mutableSetOf(FIXED_ID), workspaceColor = NEGATIVE_ARGB,
                studyPadCursors = mutableMapOf(FIXED_ID to 4),
            )
            db.workspaceDao().insertWorkspace(WorkspaceEntities.Workspace("W", id = FIXED_ID, workspaceSettings = settings))
            assertEquals(NEGATIVE_ARGB.toLong(), db.rawLong("SELECT workspace_settings_workspaceColor FROM Workspace"))
            assertEquals(
                "[\"00112233-4455-6677-8899-aabbccddeeff\"]",
                db.rawText("SELECT workspace_settings_autoAssignLabels FROM Workspace"),
            )
            assertEquals(
                "{\"00112233-4455-6677-8899-aabbccddeeff\":4}",
                db.rawText("SELECT workspace_settings_studyPadCursors FROM Workspace"),
            )
        }
    }

    @Test
    fun enumSetsAreStoredAsJsonNameArrays() = runTest {
        withDb<AiSettingsDatabase> { db ->
            db.agentPromptDao().insert(
                AgentPrompt(
                    id = FIXED_ID, name = "P", promptTemplate = "t",
                    showIn = linkedSetOf(PromptContext.TEXT_SELECTION, PromptContext.WINDOW_MENU),
                    allowedTools = linkedSetOf(AgentTool.SEARCH_BIBLE),
                )
            )
            assertEquals("[\"TEXT_SELECTION\",\"WINDOW_MENU\"]", db.rawText("SELECT showIn FROM AgentPrompt"))
            assertEquals("[\"SEARCH_BIBLE\"]", db.rawText("SELECT allowedTools FROM AgentPrompt"))
            assertNull(db.rawText("SELECT deniedTools FROM AgentPrompt"))
            assertNull(db.rawText("SELECT permissionMode FROM AgentPrompt"))
        }
    }

    @Test
    fun convertersToleratesNoInstalledBooks() {
        val c = Converters()
        assertNull(c.strToBook("NoSuchModuleInstalled"))
        assertNull(c.strToBook2("NoSuchModuleInstalled"))
        assertNull(c.strToBook(null))
        assertNull(c.bookToStr(null))
        assertNull(c.bookToStr2(null))
    }

    @Test
    fun unicodeNotesRoundTrip() = runTest {
        val unicode = "Hyvää päivää, äiti! שלום עולם " +
            "Ἀρχὴ τοῦ εὐαγγελίου 🙂📖🇫🇮"
        // At least 64 KB of UTF-8, with 2- and 4-byte sequences throughout.
        val big = buildString { while (toString().encodeToByteArray().size < 64 * 1024) append(unicode).append('\n') }
        withDb<BookmarkDatabase> { db ->
            val dao = db.bookmarkDao()
            dao.insert(
                BookmarkEntities.BibleBookmark(
                    kjvOrdinalStart = 1, kjvOrdinalEnd = 1, ordinalStart = 1, ordinalEnd = 1, v11n = KJVA,
                    playbackSettings = null, id = OTHER_ID, startOffset = null, endOffset = null, customIcon = null,
                )
            )
            for ((i, text) in listOf(unicode, big).withIndex()) {
                dao.upsert(BookmarkEntities.BibleBookmarkNotes(OTHER_ID, text))
                assertEquals(text, assertNotNull(dao.bibleBookmarkById(OTHER_ID)).notes, "note $i")
                // Stored UTF-8 bytes, not UTF-16 units: the column must not be truncated or re-encoded.
                assertEquals(
                    text.encodeToByteArray().size.toLong(),
                    db.rawLong("SELECT length(CAST(notes AS BLOB)) FROM BibleBookmarkNotes"), "bytes $i",
                )
            }
            assertTrue(big.encodeToByteArray().size >= 64 * 1024)
        }
    }

    @Test
    fun corruptSerializedColumnsFallBackToDefaults() = runTest {
        withDb<AiSettingsDatabase> { db ->
            val dao = db.agentPromptDao()
            dao.insert(
                AgentPrompt(
                    id = FIXED_ID, name = "P", promptTemplate = "t", showIn = setOf(PromptContext.NOTE_EDITOR),
                    allowedTools = setOf(AgentTool.SEARCH_BIBLE), deniedTools = setOf(AgentTool.DELETE_LABEL),
                )
            )
            // An unknown enum name (e.g. a tool removed in a later release) and syntactically invalid JSON.
            db.rawExec("UPDATE AgentPrompt SET allowedTools = '[\"NO_SUCH_TOOL\"]', deniedTools = '{not json', showIn = '[\"NOPE\"' ")
            val back = assertNotNull(dao.promptById(FIXED_ID))
            assertNull(back.allowedTools)
            assertNull(back.deniedTools)
            assertEquals(emptySet(), back.showIn)
            assertEquals("P", back.name)
        }
    }
}
