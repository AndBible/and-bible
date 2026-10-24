package net.bible.shareddata

import kotlinx.coroutines.test.runTest
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.BooleanSetting
import net.bible.android.database.CachedCloudDocument
import net.bible.android.database.CustomRepository
import net.bible.android.database.DocumentSearch
import net.bible.android.database.DocumentSyncDatabase
import net.bible.android.database.DoubleSetting
import net.bible.android.database.EpubDatabase
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.EpubMeta
import net.bible.android.database.IdType
import net.bible.android.database.LongSetting
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.StringSetting
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.database.TemporaryDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.android.database.progress.ChapterReadHistory
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.database.progress.ReadingSource
import net.bible.android.database.readingplan.ReadingPlanEntities
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.PermissionMode
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One test per current database: writes an entity that uses the converter types of that database through its
 * DAO and reads it back. The bundled driver and the generated Room code are the production ones.
 */
class DaoRoundTripCommonTest {
    private val range = VerseRangeFactory.fromString(KJVA, "Gen.1.1-Gen.1.3")
    private val playback = PlaybackSettings(speed = 130, bookId = "KJV", bookmarkWasCreated = true, verseRange = range)

    @Test
    fun bookmarkDatabase() = runTest {
        withDb<BookmarkDatabase> { db ->
            val dao = db.bookmarkDao()
            val label = BookmarkEntities.Label(
                id = FIXED_ID, name = "Label äö 🙂", color = NEGATIVE_ARGB,
                displayStyle = BookmarkDisplayStyle.MARKER, displayStyleWholeVerse = null,
            )
            dao.insert(label)
            val v11n = Versifications.instance().getVersification("NRSV")!!
            val bookmark = BookmarkEntities.BibleBookmark(
                kjvOrdinalStart = 10, kjvOrdinalEnd = 12, ordinalStart = 11, ordinalEnd = 13, v11n = v11n,
                playbackSettings = playback, id = OTHER_ID, createdAt = FIXED_DATE, book = null,
                startOffset = null, endOffset = 5, primaryLabelId = FIXED_ID, lastUpdatedOn = FIXED_DATE,
                wholeVerse = true, type = BookmarkType.EXAMPLE, customIcon = null,
            )
            dao.insert(bookmark)
            dao.insert(BookmarkEntities.BibleBookmarkNotes(OTHER_ID, "note"))

            val back = assertNotNull(dao.labelById(FIXED_ID))
            assertEquals(label.name, back.name)
            assertEquals(NEGATIVE_ARGB, back.color)
            assertEquals(BookmarkDisplayStyle.MARKER, back.displayStyle)
            assertNull(back.displayStyleWholeVerse)

            val read = assertNotNull(dao.bibleBookmarkById(OTHER_ID))
            assertEquals("NRSV", read.v11n.name)
            assertEquals(FIXED_DATE_MILLIS, read.createdAt.getTime())
            assertEquals(FIXED_DATE_MILLIS, read.lastUpdatedOn.getTime())
            assertEquals(playback, read.playbackSettings)
            assertEquals(FIXED_ID, read.primaryLabelId)
            assertEquals(BookmarkType.EXAMPLE, read.type)
            assertEquals("note", read.notes)
            assertTrue(read.wholeVerse)
            assertNull(read.book)
        }
    }

    @Test
    fun workspaceDatabase() = runTest {
        withDb<WorkspaceDatabase> { db ->
            val dao = db.workspaceDao()
            val speak = SpeakSettings(playbackSettings = playback, sleepTimer = 7, lastSleepTimer = 20, queue = false, repeat = true)
            val settings = WorkspaceEntities.WorkspaceSettings(
                speakSettings = speak,
                recentLabels = mutableListOf(WorkspaceEntities.RecentLabel(FIXED_ID, 99L)),
                autoAssignLabels = mutableSetOf(FIXED_ID, OTHER_ID),
                autoAssignPrimaryLabel = OTHER_ID,
                studyPadCursors = mutableMapOf(FIXED_ID to 4),
                hideCompareDocuments = mutableSetOf("KJV", "שלום"),
                workspaceColor = NEGATIVE_ARGB,
            )
            val ws = WorkspaceEntities.Workspace(name = "W äö", id = FIXED_ID, workspaceSettings = settings)
            dao.insertWorkspace(ws)
            val window = WorkspaceEntities.Window(
                workspaceId = FIXED_ID, isSynchronized = true, isPinMode = false,
                windowLayout = WorkspaceEntities.WindowLayout("SPLIT", 0.25f), id = OTHER_ID,
            )
            dao.insertWindow(window)
            dao.insertHistoryItems(listOf(WorkspaceEntities.HistoryItem(OTHER_ID, FIXED_DATE, "KJV", "Gen.1.1", 5)))
            dao.insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(FIXED_ID, OTHER_ID, 2))

            val back = assertNotNull(dao.workspace(FIXED_ID))
            assertEquals(ws.name, back.name)
            assertEquals(settings, back.workspaceSettings)
            assertEquals(NEGATIVE_ARGB, back.workspaceSettings!!.workspaceColor)
            val w = dao.windows(FIXED_ID).single()
            assertEquals("SPLIT", w.windowLayout.state)
            assertEquals(0.25f, w.windowLayout.weight)
            val h = dao.historyItems(OTHER_ID).single()
            assertEquals(FIXED_DATE_MILLIS, h.createdAt.getTime())
            assertEquals(2, dao.labelOverrides(FIXED_ID).single().overrideMode)
        }
    }

    @Test
    fun readingPlanDatabase() = runTest {
        withDb<ReadingPlanDatabase> { db ->
            val dao = db.readingPlanDao()
            dao.updatePlan(ReadingPlanEntities.ReadingPlan("plan-ä", FIXED_DATE, 3, FIXED_ID))
            dao.addPlanStatus(ReadingPlanEntities.ReadingPlanStatus("plan-ä", 3, "{\"read\":[1,2]}", OTHER_ID))
            val plan = assertNotNull(dao.getPlan("plan-ä"))
            assertEquals(FIXED_DATE_MILLIS, plan.planStartDate.getTime())
            assertEquals(3, plan.planCurrentDay)
            assertEquals(FIXED_ID, plan.id)
            assertEquals("{\"read\":[1,2]}", assertNotNull(dao.getStatus("plan-ä", 3)).readingStatus)
        }
    }

    @Test
    fun myDocumentDatabase() = runTest {
        withDb<MyDocumentDatabase> { db ->
            val dao = db.myDocumentDao()
            dao.insert(MyDocument(id = FIXED_ID, name = "Doc", initials = "MyDoc_a", createdAt = FIXED_DATE_MILLIS, sourcePromptId = OTHER_ID))
            dao.insert(
                MyDocumentPage(
                    id = OTHER_ID, documentId = FIXED_ID, title = "T", pageKey = "k",
                    contentType = MyDocumentContentType.HTML, orderNumber = 1, languageCode = "fi",
                )
            )
            dao.insertOrUpdateContent(MyDocumentPageContent(OTHER_ID, "sisältö"))
            val doc = assertNotNull(dao.documentById(FIXED_ID))
            assertEquals(OTHER_ID, doc.sourcePromptId)
            assertEquals(FIXED_DATE_MILLIS, doc.createdAt)
            val page = assertNotNull(dao.pageByIdWithContent(OTHER_ID))
            assertEquals(MyDocumentContentType.HTML, page.contentType)
            assertEquals("sisältö", page.content)
        }
    }

    @Test
    fun aiSettingsDatabase() = runTest {
        withDb<AiSettingsDatabase> { db ->
            val provider = LlmProviderConfig(FIXED_ID, "CUSTOM", "Mine", "https://x", ApiFormat.ANTHROPIC, 2)
            db.llmProviderConfigDao().insert(provider)
            assertEquals(provider, db.llmProviderConfigDao().getById(FIXED_ID))

            val prompt = AgentPrompt(
                id = OTHER_ID, name = "P", promptTemplate = "t",
                showIn = linkedSetOf(PromptContext.VERSE_SELECTION, PromptContext.NOTE_EDITOR),
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = linkedSetOf(AgentTool.GET_VERSE_CONTENT, AgentTool.FINISH_WITHOUT_DOCUMENT),
                deniedTools = emptySet(), createdAt = FIXED_DATE_MILLIS,
            )
            db.agentPromptDao().insert(prompt)
            val back = assertNotNull(db.agentPromptDao().promptById(OTHER_ID))
            assertEquals(prompt.showIn, back.showIn)
            assertEquals(PermissionMode.ASK_ONCE_PER_RUN, back.permissionMode)
            assertEquals(prompt.allowedTools, back.allowedTools)
            assertEquals(emptySet(), back.deniedTools)
            assertEquals(FIXED_DATE_MILLIS, back.createdAt)
        }
    }

    @Test
    fun progressDatabase() = runTest {
        withDb<ProgressDatabase> { db ->
            val dao = db.progressDao()
            dao.insertMemorizedVerse(MemorizedVerse(FIXED_ID, 1234, FIXED_DATE_MILLIS))
            dao.insertChapterReadHistory(
                ChapterReadHistory(OTHER_ID, kjvBookOrdinal = 1, chapter = 2, cycle = 3, readAt = FIXED_DATE_MILLIS, bookInitials = "KJV", source = ReadingSource.AUTO_TTS)
            )
            val verse = dao.allMemorizedVerses().single()
            assertEquals(FIXED_ID, verse.id)
            assertEquals(FIXED_DATE_MILLIS, verse.memorizedAt)
            val read = dao.getChapterReadHistory(1, 2, 3).single()
            assertEquals(ReadingSource.AUTO_TTS, read.source)
            assertEquals(OTHER_ID, read.id)
        }
    }

    @Test
    fun temporaryDatabase() = runTest {
        withDb<TemporaryDatabase> { db ->
            val dao = db.documentSearchDao()
            dao.insertDocuments(listOf(DocumentSearch("KJV", "KJV", "King James Ä", "en", "Crosswire")))
            assertEquals(1L, dao.count())
            assertEquals(listOf("KJV"), dao.search("James"))
        }
    }

    @Test
    fun documentSyncDatabase() = runTest {
        withDb<DocumentSyncDatabase> { db ->
            val doc = CachedCloudDocument("KJV", "Näme", "BIBLE", "1.0", 123456789012L, "fi", "cat", "dev", FIXED_DATE_MILLIS, null, true)
            db.cloudDocumentCacheDao().insertAll(listOf(doc))
            assertEquals(listOf(doc), db.cloudDocumentCacheDao().all())
        }
    }

    @Test
    fun repoDatabase() = runTest {
        withDb<RepoDatabase> { db ->
            val repo = CustomRepository(name = "R ä", type = "HTTP", host = "h", manifestUrl = null)
            db.customRepositoryDao().insert(repo)
            val back = db.customRepositoryDao().all().single()
            assertEquals("R ä", back.name)
            assertTrue(back.id > 0)
            val info = SwordDocumentInfo("KJV", "King James", "KJV", "en", "Crosswire", cipherKey = "key")
            db.swordDocumentInfoDao().insert(info)
            assertEquals(info, db.swordDocumentInfoDao().getBook("KJV"))
        }
    }

    @Test
    fun settingsDatabase() = runTest {
        withDb<SettingsDatabase> { db ->
            db.booleanSettingDao().insertOrUpdate(BooleanSetting("b", true))
            db.longSettingDao().insertOrUpdate(LongSetting("l", Long.MIN_VALUE))
            db.stringSettingDao().insertOrUpdate(StringSetting("s", "äö 🙂"))
            db.doubleSettingDao().insertOrUpdate(DoubleSetting("d", -0.1))
            assertEquals(true, db.booleanSettingDao().byKey("b")!!.value)
            assertEquals(Long.MIN_VALUE, db.longSettingDao().byKey("l")!!.value)
            assertEquals("äö 🙂", db.stringSettingDao().byKey("s")!!.value)
            assertEquals(-0.1, db.doubleSettingDao().byKey("d")!!.value)
        }
    }

    @Test
    fun epubDatabase() = runTest {
        withDb<EpubDatabase> { db ->
            val dao = db.epubDao()
            val id = dao.insert(EpubFragment("orig", 1, 9)).single()
            dao.insert(EpubHtmlToFrag("ch1.html#a", id))
            dao.insert(EpubMeta(0, 4242))
            val frag = assertNotNull(dao.getFragment("ch1.html#a"))
            assertEquals(id, frag.id)
            assertEquals(9, frag.ordinalEnd)
            assertEquals(4242, dao.getMeta()!!.totalCharacters)
        }
    }
}
