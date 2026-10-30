package net.bible.android.control.page

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MissingCommentaryDocumentTest {
    private lateinit var manager: CurrentPageManager
    private lateinit var factory: FakeBookFactory
    private lateinit var installedCommentaries: List<Book>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> args!![1]
                else -> error("Unexpected settings call: ${method.name}")
            }
        } as AppSettings
        val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
            method.name
        } as CoreStrings
        factory = FakeBookFactory(strings)
        startKoin { modules(module {
            single<AppSettings> { settings }
            single { factory }
        }) }
        installedCommentaries = Books.installed().getBooks { it.bookCategory == BookCategory.COMMENTARY }
        installedCommentaries.forEach { Books.installed().removeBook(it) }
        manager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), blank<WindowControl>())
        manager.window = blank<Window>()
        manager.currentBible.onlySetCurrentDocument(specialBook("TestBible", "Biblical Texts"))
    }

    @AfterTest
    fun tearDown() {
        manager.destroy()
        installedCommentaries.forEach { Books.installed().addBook(it) }
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun restoredCommentaryWithoutDocumentReturnsToBible() {
        manager.restoreFrom(savedCommentary())

        assertSame(manager.currentBible, manager.currentPage)
    }

    @Test
    fun passageNavigationFallsBackToBibleWhenCommentaryDisappears() {
        val commentary = specialBook("TestCommentary", "Commentaries")
        Books.installed().addBook(commentary)
        try {
            manager.restoreFrom(savedCommentary(commentary.initials))
            // The document was available when the window was restored, then became unavailable.
            Books.installed().removeBook(commentary)
            assertNull(manager.currentCommentary.currentDocument)

            assertSame(manager.currentBible.currentDocument, manager.currentPassageDocument)
            assertSame(manager.currentBible, manager.currentVersePage)
        } finally {
            if (Books.installed().getBook(commentary.initials) != null) Books.installed().removeBook(commentary)
        }
    }

    @Test
    fun availableCommentaryRemainsThePassageNavigationDocument() {
        val commentary = specialBook("TestCommentary", "Commentaries")
        Books.installed().addBook(commentary)
        try {
            manager.restoreFrom(savedCommentary(commentary.initials))

            assertSame(manager.currentCommentary, manager.currentPage)
            assertSame(commentary, manager.currentPassageDocument)
            assertSame(manager.currentCommentary, manager.currentVersePage)
        } finally {
            Books.installed().removeBook(commentary)
        }
    }

    @Test
    fun missingNamedCommentaryKeepsItsDownloadPlaceholder() {
        manager.restoreFrom(savedCommentary("UninstalledCommentary"))

        assertSame(manager.currentCommentary, manager.currentPage)
        assertSame(factory.giveDoesNotExist("UninstalledCommentary"), manager.currentPassageDocument)
    }

    @Test
    fun swordDocumentFollowsItsReplacementAfterTheModuleIsReinstalled() {
        val root = java.nio.file.Files.createTempDirectory("sword-replace").toFile()
        val initials = "ReplacedDict"
        fun register(): Book {
            val dir = java.io.File(root, "modules/lexdict/rawld/x").apply { mkdirs() }
            java.io.File(dir, "test.dat").writeText("x")
            java.io.File(dir, "test.idx").writeText("x")
            val conf = java.io.File(root, "mods.d/replaced.conf").apply {
                parentFile.mkdirs()
                writeText("[$initials]\nDataPath=./modules/lexdict/rawld/x/test\nModDrv=RawLD\nEncoding=UTF-8\nDescription=Replaced\n")
            }
            org.crosswire.jsword.book.sword.SwordBookDriver.registerNewBook(
                org.crosswire.jsword.book.sword.SwordBookMetaDataJvm.create(conf, root.toURI()).apply {
                    setDriver(org.crosswire.jsword.book.sword.SwordBookDriver.instance())
                })
            return Books.installed().getBook(initials)!!
        }
        try {
            val old = register()
            manager.currentDictionary.onlySetCurrentDocument(old)
            assertSame(old, manager.currentDictionary.currentDocument)
            val replacement = register()
            assertNotSame(old, replacement)
            assertSame(replacement, manager.currentDictionary.currentDocument)
        } finally {
            Books.installed().getBooks().filter { it.initials == initials }.forEach { Books.installed().removeBook(it) }
            root.deleteRecursively()
        }
    }

    private fun savedCommentary(document: String? = null) = WorkspaceEntities.PageManager(
        windowId = IdType(),
        biblePage = WorkspaceEntities.BiblePage(null, WorkspaceEntities.Verse("KJV", 54, 1, 1)),
        commentaryPage = WorkspaceEntities.CommentaryPage(document, null, null),
        dictionaryPage = null,
        generalBookPage = null,
        mapPage = null,
        currentCategoryName = "COMMENTARY",
        textDisplaySettings = null,
        jsState = null,
    )

    private fun specialBook(initials: String, category: String): Book = factory.createFakeRepoBook(
        initials, "[$initials]\nDescription=$initials\nCategory=$category\nVersification=KJV" +
            if (category == "Biblical Texts") "\nAndBibleSpecial=1" else "", "",
    )

    /** These ownership collaborators are not used by page restoration or passage-document resolution. */
    private inline fun <reified T : Any> blank(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
