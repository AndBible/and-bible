package net.bible.shareddata

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import net.bible.android.database.OldMonolithicAppDatabase
import net.bible.android.database.migrations.oldMonolithicAppDatabaseMigrations
import net.bible.shareddata.fixtures.CURRENT_DB_SPECS
import net.bible.shareddata.fixtures.createFromExport
import net.bible.shareddata.fixtures.rawRows
import net.bible.shareddata.fixtures.userVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/** Seed exported schemas directly so Room must migrate real, populated files. */
class MigrationDataTest {
    private suspend fun migrated(
        fqn: String,
        version: Int,
        seed: List<String>,
        verify: suspend (RoomDatabase) -> Unit,
    ) {
        val dir = testDatabaseDir()
        val name = "data.sqlite3"
        val path = "$dir/$name"
        createFromExport(fqn, version, path)
        val connection = BundledSQLiteDriver().open(path)
        try {
            seed.forEach { connection.execSQL(it) }
        } finally {
            connection.close()
        }
        val spec = CURRENT_DB_SPECS.find { it.fqn == fqn }
        val db = if (spec != null) spec.open(dir, name) else
            openTestDatabase<OldMonolithicAppDatabase>(dir, name, *oldMonolithicAppDatabaseMigrations)
        try {
            assertEquals(spec?.currentVersion ?: 69, db.userVersion())
            verify(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun bookmarkV1TextAndRelationshipsSurvive() = runTest {
        migrated("net.bible.android.database.BookmarkDatabase", 1, listOf(
            "INSERT INTO Label (id,name,color,markerStyle,underlineStyleWholeVerse) VALUES (X'01','Personal',123,1,1)",
            "INSERT INTO Bookmark (id,kjvOrdinalStart,kjvOrdinalEnd,ordinalStart,ordinalEnd,v11n,createdAt,book,primaryLabelId,notes) VALUES (X'02',10,12,20,22,'KJVA',100,'KJV',X'01','My note')",
            "INSERT INTO BookmarkToLabel VALUES (X'02',X'01',7,2,1)",
            "INSERT INTO StudyPadTextEntry VALUES (X'03',X'01','My study text',8,3)",
        )) { db ->
            assertEquals(listOf(listOf("02", "10", "12", "20", "22", "KJVA", "KJV", "01", "My note")), db.rawRows("SELECT hex(b.id), b.kjvOrdinalStart, b.kjvOrdinalEnd, b.ordinalStart, b.ordinalEnd, b.v11n, b.book, hex(b.primaryLabelId), n.notes FROM BibleBookmark b JOIN BibleBookmarkNotes n ON n.bookmarkId=b.id", 9))
            assertEquals(listOf(listOf("02", "01", "7", "2", "1")), db.rawRows("SELECT hex(bookmarkId),hex(labelId),orderNumber,indentLevel,expandContent FROM BibleBookmarkToLabel", 5))
            assertEquals(listOf(listOf("03", "01", "My study text", "8", "3")), db.rawRows("SELECT hex(e.id),hex(e.labelId),t.text,e.orderNumber,e.indentLevel FROM StudyPadTextEntry e JOIN StudyPadTextEntryText t ON t.studyPadTextEntryId=e.id", 5))
            assertEquals(listOf(listOf("Personal", "123", "2", "1")), db.rawRows("SELECT name,color,displayStyle,displayStyleWholeVerse FROM Label", 4))
        }
    }

    @Test
    fun bookmarkV3GenericBookAndOrdinalsSurvive() = runTest {
        migrated("net.bible.android.database.BookmarkDatabase", 3, listOf(
            "INSERT INTO GenericBookmark (id,`key`,createdAt,book,ordinalStart,ordinalEnd,startOffset,endOffset) VALUES (X'04','chapter 2',42,'COMMENTARY',13,19,3,9)",
        )) { db ->
            assertEquals(listOf(listOf("04", "chapter 2", "42", "COMMENTARY", "13", "19", "3", "9")), db.rawRows("SELECT hex(id),`key`,createdAt,bookInitials,ordinalStart,ordinalEnd,startOffset,endOffset FROM GenericBookmark", 8))
        }
    }

    @Test
    fun bookmarkV11SpecialLabelsMergeWithoutLosingRelations() = runTest {
        migrated("net.bible.android.database.BookmarkDatabase", 11, listOf(
            "INSERT INTO Label (id,name,color,markerStyle,markerStyleWholeVerse,underlineStyle,underlineStyleWholeVerse,hideStyle,hideStyleWholeVerse,favourite) VALUES (X'000000000000AB1E00005BEA400001A1','__SPEAK_LABEL__',55,1,1,1,0,0,0,1)",
            "INSERT INTO Label (id,name,color) VALUES (X'11','__SPEAK_LABEL__',99)",
            "INSERT INTO BibleBookmark (id,kjvOrdinalStart,kjvOrdinalEnd,ordinalStart,ordinalEnd,v11n,createdAt,primaryLabelId) VALUES (X'12',1,2,1,2,'KJVA',100,X'11')",
            "INSERT INTO GenericBookmark (id,`key`,createdAt,bookInitials,ordinalStart,ordinalEnd,primaryLabelId) VALUES (X'13','section',101,'DOC',4,5,X'11')",
            "INSERT INTO BibleBookmarkToLabel VALUES (X'12',X'000000000000AB1E00005BEA400001A1',7,1,1)",
            "INSERT INTO BibleBookmarkToLabel VALUES (X'12',X'11',9,2,0)",
            "INSERT INTO GenericBookmarkToLabel VALUES (X'13',X'11',8,3,1)",
            "INSERT INTO StudyPadTextEntry (id,labelId,orderNumber,indentLevel) VALUES (X'14',X'11',6,2)",
            "INSERT INTO StudyPadTextEntryText VALUES (X'14','retained')",
        )) { db ->
            val id = "000000000000AB1E00005BEA400001A1"
            assertEquals(listOf(listOf(id, "55", "2", null, "1")), db.rawRows("SELECT hex(id),color,displayStyle,displayStyleWholeVerse,favourite FROM Label WHERE name='__SPEAK_LABEL__'", 5))
            assertEquals(listOf(listOf("12", id, "7", "1", "1")), db.rawRows("SELECT hex(bookmarkId),hex(labelId),orderNumber,indentLevel,expandContent FROM BibleBookmarkToLabel", 5))
            assertEquals(listOf(listOf("13", id, "8", "3", "1")), db.rawRows("SELECT hex(bookmarkId),hex(labelId),orderNumber,indentLevel,expandContent FROM GenericBookmarkToLabel", 5))
            assertEquals(listOf(listOf(id)), db.rawRows("SELECT hex(primaryLabelId) FROM BibleBookmark", 1))
            assertEquals(listOf(listOf(id)), db.rawRows("SELECT hex(primaryLabelId) FROM GenericBookmark", 1))
            assertEquals(listOf(listOf("14", id, "retained")), db.rawRows("SELECT hex(e.id),hex(e.labelId),t.text FROM StudyPadTextEntry e JOIN StudyPadTextEntryText t ON t.studyPadTextEntryId=e.id", 3))
        }
    }

    @Test
    fun workspaceV1StateAndSettingsSurvive() = runTest {
        migrated("net.bible.android.database.WorkspaceDatabase", 1, listOf(
            "INSERT INTO Workspace (id,name,contentsText,orderNumber,text_display_settings_strongsMode,text_display_settings_fontSize,text_display_settings_fontFamily,text_display_settings_showFootNotes) VALUES (X'21','Study','personal',4,3,16,'serif',0)",
            "INSERT INTO Window (id,workspaceId,isSynchronized,isPinMode,isLinksWindow,orderNumber,window_layout_state,window_layout_weight) VALUES (X'22',X'21',1,0,0,2,'NORMAL',0.5)",
            "INSERT INTO PageManager (windowId,currentCategoryName,bible_document,bible_verse_versification,bible_verse_bibleBook,bible_verse_chapterNo,bible_verse_verseNo,text_display_settings_strongsMode,text_display_settings_fontSize) VALUES (X'22','BIBLE','KJV','KJVA',43,3,16,3,22)",
            "INSERT INTO HistoryItem (id,windowId,createdAt,document,`key`,anchorOrdinal) VALUES (7,X'22',123,'KJV','John 3:16',42)",
        )) { db ->
            assertEquals(listOf(listOf("21", "Study", "personal", "4", null, null, "serif", "0")), db.rawRows("SELECT hex(id),name,contentsText,orderNumber,text_display_settings_strongsMode,text_display_settings_fontSize,text_display_settings_fontFamily,text_display_settings_showFootNotes FROM Workspace", 8))
            assertEquals(listOf(listOf("22", "21", "2", "NORMAL", "0.5")), db.rawRows("SELECT hex(id),hex(workspaceId),orderNumber,window_layout_state,window_layout_weight FROM Window", 5))
            assertEquals(listOf(listOf("22", "BIBLE", "KJV", "KJVA", "43", "3", "16", "0", "22")), db.rawRows("SELECT hex(windowId),currentCategoryName,bible_document,bible_verse_versification,bible_verse_bibleBook,bible_verse_chapterNo,bible_verse_verseNo,text_display_settings_strongsMode,text_display_settings_fontSize FROM PageManager", 9))
            assertEquals(listOf(listOf("7", "22", "123", "KJV", "John 3:16", "42")), db.rawRows("SELECT id,hex(windowId),createdAt,document,`key`,anchorOrdinal FROM HistoryItem", 6))
        }
    }

    @Test
    fun workspaceV15GlobalSettingsRebuildPreservesValues() = runTest {
        migrated("net.bible.android.database.WorkspaceDatabase", 15, listOf(
            "INSERT INTO GlobalTextDisplaySettings (id,text_display_settings_strongsMode,text_display_settings_fontSize,text_display_settings_fontFamily,text_display_settings_showFootNotes,text_display_settings_colors_dayBackground) VALUES (1,2,24,'serif',0,12345)",
        )) { db ->
            assertEquals(listOf(listOf("00000000000000000000000000000001", "2", "24", "serif", "0", "12345", null)), db.rawRows("SELECT hex(id),text_display_settings_strongsMode,text_display_settings_fontSize,text_display_settings_fontFamily,text_display_settings_showFootNotes,text_display_settings_colors_dayBackground,text_display_settings_colors_nightBackground FROM GlobalTextDisplaySettings", 7))
        }
    }

    @Test
    fun progressV1PersonalRecordsSurvive() = runTest {
        migrated("net.bible.android.database.progress.ProgressDatabase", 1, listOf(
            "INSERT INTO MemorizedVerse VALUES (X'31',321,456)",
            "INSERT INTO ChapterReadingRecord VALUES (X'32',43,3,2,789,'MANUAL')",
        )) { db ->
            assertEquals(listOf(listOf("31", "321", "456")), db.rawRows("SELECT hex(id),kjvOrdinal,memorizedAt FROM MemorizedVerse", 3))
            assertEquals(listOf(listOf("43", "3", "2", "789", "", "MANUAL")), db.rawRows("SELECT kjvBookOrdinal,chapter,cycle,readAt,bookInitials,source FROM ChapterReadHistory", 6))
        }
    }

    @Test
    fun aiV1PromptAndProviderSurvive() = runTest {
        migrated("net.bible.android.database.AiSettingsDatabase", 1, listOf(
            "INSERT INTO LlmProviderConfig (id,providerType,displayName,endpoint,apiFormat,defaultModel,isDefault,orderNumber) VALUES (X'41','CUSTOM','My provider','https://example.invalid','OPENAI','model',1,3)",
            "INSERT INTO AgentPrompt (id,name,description,promptTemplate,showIn,orderNumber,createdAt,providerConfigId,modelOverride) VALUES (X'42','My prompt','description','Explain this','BIBLE',4,555,X'41','model')",
        )) { db ->
            assertEquals(listOf(listOf("41", "CUSTOM", "My provider", "https://example.invalid", "OPENAI", "3")), db.rawRows("SELECT hex(id),providerType,displayName,endpoint,apiFormat,orderNumber FROM LlmProviderConfig", 6))
            assertEquals(listOf(listOf("42", "My prompt", "description", "Explain this", "BIBLE", "4", "555", null)), db.rawRows("SELECT hex(id),name,description,promptTemplate,showIn,orderNumber,createdAt,configuredModelId FROM AgentPrompt", 8))
        }
    }

    @Test
    fun legacyV54PersonalDataSurvivesThroughV69() = runTest {
        migrated("net.bible.android.database.OldMonolithicAppDatabase", 54, listOf(
            "INSERT INTO Label (id,name,color) VALUES (6,'Personal',123)",
            "INSERT INTO Bookmark (id,kjvOrdinalStart,kjvOrdinalEnd,ordinalStart,ordinalEnd,v11n,createdAt,book,primaryLabelId,notes) VALUES (7,10,12,10,12,'KJVA',100,'KJV',6,'Personal note')",
            "INSERT INTO readingplan (_id,plan_code,plan_start_date,plan_current_day) VALUES (8,'plan-a',1234,5)",
            "INSERT INTO readingplan_status (_id,plan_code,plan_day,reading_status) VALUES (9,'plan-a',4,'READ')",
        )) { db ->
            assertEquals(listOf(listOf("7", "10", "12", "KJVA", "100", "KJV", "6", "Personal note")), db.rawRows("SELECT id,kjvOrdinalStart,kjvOrdinalEnd,v11n,createdAt,book,primaryLabelId,notes FROM Bookmark", 8))
            assertEquals(listOf(listOf("8", "plan-a", "1234", "5")), db.rawRows("SELECT _id,plan_code,plan_start_date,plan_current_day FROM readingplan", 4))
            assertEquals(listOf(listOf("9", "plan-a", "4", "READ")), db.rawRows("SELECT _id,plan_code,plan_day,reading_status FROM readingplan_status", 4))
        }
    }
}
