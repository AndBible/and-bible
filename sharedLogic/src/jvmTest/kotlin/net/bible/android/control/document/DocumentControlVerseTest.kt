package net.bible.android.control.document

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same verse lists feed every scoped quick sheet and both toolbar implementations (#4085). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DocumentControlVerseTest {
    private val job = SupervisorJob()
    private val added = mutableListOf<Book>()
    private val original = mutableListOf<Book>()
    private lateinit var window: Window
    private lateinit var manager: CurrentPageManager
    private lateinit var control: DocumentControl
    private val kjv get() = Versifications.instance().getVersification("KJV")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val strings = proxy<CoreStrings> { method, _ -> method }
        val factory = FakeBookFactory(strings)
        startKoin { modules(module {
            single { factory }
            single { net.bible.android.control.page.window.WindowStateServiceImpl() }
            single<net.bible.android.control.page.GlobalTextDisplaySettingsSource> {
                object : net.bible.android.control.page.GlobalTextDisplaySettingsSource {
                    override val current = WorkspaceEntities.TextDisplaySettings.default
                }
            }
        }) }
        original += Books.installed().getBooks().filter {
            it.bookCategory == BookCategory.BIBLE || it.bookCategory == BookCategory.COMMENTARY
        }
        original.forEach { Books.installed().removeBook(it) }
        val windowControl = WindowControl()
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined + job))
        manager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), windowControl)
        manager.currentBible.onlySetCurrentDocument(book("Current", "Gen.1.1", "Rom.8.1"))
        window = Window(WorkspaceEntities.Window(IdType(), false, false, windowLayout = WorkspaceEntities.WindowLayout("VISIBLE")), manager, repository)
        windowControl.windowRepository = repository
        repository.activeWindow = window
        control = DocumentControl(factory, windowControl, proxy<AppSettings> { method, _ -> error(method) },
            proxy<UserNotifier> { method, _ -> error(method) }, strings, AppCoroutineScope(Dispatchers.Unconfined + job),
            { error("unused") }, { error("unused") }, {})
    }

    @AfterTest
    fun tearDown() {
        if (::window.isInitialized) window.destroy()
        runBlocking { job.cancelAndJoin() }
        added.forEach { Books.installed().removeBook(it) }
        original.forEach { Books.installed().addBook(it) }
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun newTestamentVerseExcludesOldTestamentOnlyBibleButKeepsBibleContainingVerse() {
        install(book("OldTestamentOnly", "Gen.1.1"))
        install(book("FullBible", "Gen.1.1", "Rom.8.1"))
        manager.currentBible.doSetKey(Verse(kjv, BibleBook.ROM, 8, 1))
        assertEquals(listOf("FullBible"), control.biblesForVerse.map { it.initials })
        manager.currentBible.doSetKey(Verse(kjv, BibleBook.GEN, 1, 1))
        assertEquals(setOf("OldTestamentOnly", "FullBible"), control.biblesForVerse.map { it.initials }.toSet())
    }

    @Test
    fun excludesBibleWhoseVersificationDoesNotHaveTheCurrentBook() {
        install(book("HebrewBible", "Gen.1.1", versification = "Leningrad"))
        install(book("FullBible", "Rom.8.1"))
        manager.currentBible.doSetKey(Verse(kjv, BibleBook.ROM, 8, 1))
        assertEquals(listOf("FullBible"), control.biblesForVerse.map { it.initials })
    }

    @Test
    fun checksActualVersePresenceNotJustTheBookOrTestament() {
        install(book("OtherVerse", "Rom.8.2"))
        install(book("ThisVerse", "Rom.8.1"))
        manager.currentBible.doSetKey(Verse(kjv, BibleBook.ROM, 8, 1))
        assertEquals(listOf("ThisVerse"), control.biblesForVerse.map { it.initials })
    }

    @Test
    fun commentaryVerseListAlsoExcludesDocumentsWithoutTheVerseAndRetainsPseudoDocuments() {
        install(book("OtherCommentary", "Gen.1.1", category = "Commentaries"))
        install(book("ThisCommentary", "Rom.8.1", category = "Commentaries"))
        manager.currentBible.doSetKey(Verse(kjv, BibleBook.ROM, 8, 1))
        val rows = control.commentariesForVerse
        assertEquals(listOf("ThisCommentary"), rows.filter { it in added }.map { it.initials })
        // The pseudo-documents do not use a SWORD verse index and must remain available.
        assertEquals(true, rows.any { it.initials == "Compare" })
    }

    private fun install(book: Book) {
        Books.installed().addBook(book)
        added += book
    }

    private fun book(initials: String, vararg verses: String, versification: String = "KJV", category: String = "Biblical Texts") =
        SwordBook(SwordBookMetaData(
            "[$initials]\nModDrv=RawText\nDescription=$initials\nCategory=$category\nVersification=$versification\n".encodeToByteArray(),
            "$initials.conf",
        ), object : NullBackend() {
            override fun contains(key: Key?) = key?.getOsisID() in verses
        })

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T

    /** Unused ownership dependencies; the real pages and window supply the active verse. */
    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
