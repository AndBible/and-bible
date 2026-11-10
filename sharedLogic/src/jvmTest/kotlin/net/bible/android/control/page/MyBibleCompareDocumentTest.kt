package net.bible.android.control.page

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.mybible.SqliteBackend
import net.bible.service.sword.mybible.addMyBibleBook
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import okio.Path.Companion.toPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookFilters
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Exercises the installed MyBible reader and the same compare path used by restored windows (#4017). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MyBibleCompareDocumentTest {
    @Test
    fun installedMyBibleIsComparedAfterOpeningAndRestoringTheComparePage() = runBlocking {
        val root = Files.createTempDirectory("mybible-compare").toFile()
        val installedBibles = Books.installed().getBooks(BookFilters.getBibles())
        val added = mutableListOf<Book>()
        val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
            method.name
        } as CoreStrings
        val factory = FakeBookFactory(strings)
        Dispatchers.setMain(Dispatchers.Unconfined)
        startKoin { modules(module {
            single<net.bible.sharedcore.platform.AppDirectories> {
                object : net.bible.sharedcore.platform.AppDirectories {
                    override val internalFilesDir = root.resolve("internal").path.toPath()
                    override val modulesDir = root.path.toPath()
                    override val manualInstallDir = modulesDir
                    override val manualInstallDir2 = modulesDir
                    override val manualReadingPlanDir = modulesDir
                }
            }
            single<CoreStrings> { strings }
            single { factory }
            single<ReadOnlyModuleDbOpener> { HostReadOnlyModuleDbOpener() }
        }) }
        try {
            installedBibles.forEach { Books.installed().removeBook(it) }
            val file = root.resolve("CompareFixture.SQLite3")
            openSqlite(file.path).use { db ->
                db.exec("CREATE TABLE info (name TEXT, value TEXT)")
                db.exec("INSERT INTO info VALUES ('description', 'Compare fixture')")
                db.exec("INSERT INTO info VALUES ('language', 'en')")
                db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
                db.exec("INSERT INTO verses VALUES (470, 16, 16, 'You are the Christ')")
            }
            addMyBibleBook(file.path.toPath())
            val bible = Books.installed().getBook("MyBible-CompareFixture")!!
            added.add(bible)
            assertIs<SwordBook>(bible)
            assertEquals("KJVA", bible.versification.name)

            // A general book can declare a Bible category in its conf; this used to enter Compare too.
            val metadata = SwordBookMetaData(
                "[CompareTreeFixture]\nModDrv=RawGenBook\nCategory=Biblical Texts\nDescription=Tree fixture\n".encodeToByteArray(),
                "compare-tree.conf",
            )
            val tree = SwordGenBook(metadata, NullBackend())
            Books.installed().addBook(tree)
            added.add(tree)
            val missing = SwordBook(
                SwordBookMetaData("[CompareMissingFixture]\nModDrv=RawText\nDescription=Missing verse\n".encodeToByteArray(), "missing.conf"),
                NullBackend(),
            )
            Books.installed().addBook(missing)
            added.add(missing)

            val manager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), blank<WindowControl>())
            try {
                val page = manager.currentCommentary
                page.onlySetCurrentDocument(factory.compareDocument)
                page.originalVerseRange = VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Matt.16.16")
                assertCompareContent(page)

                val saved = page.entity
                val restored = CurrentCommentaryPage(manager.currentBibleVerse, blank<BibleTraverser>(), manager)
                restored.restoreFrom(saved)
                // The Bible page restores the shared verse holder before the commentary page at startup.
                manager.currentBibleVerse.restoreFrom(WorkspaceEntities.Verse("KJV", BibleBook.MATT.ordinal, 16, 16))
                assertCompareContent(restored)
            } finally {
                manager.destroy()
            }
        } finally {
            added.forEach { book ->
                SwordContentFacade.evictBook(book.initials)
                Books.installed().removeBook(book)
                Activator.deactivate(book)
                ((book as? SwordBook)?.backend as? SqliteBackend)?.state?.close()
            }
            installedBibles.forEach { Books.installed().addBook(it) }
            stopKoin()
            Dispatchers.resetMain()
            root.deleteRecursively()
        }
    }

    private suspend fun assertCompareContent(page: CurrentCommentaryPage) {
        val document = assertIs<MultiFragmentDocument>(page.currentPageContent())
        val fragments = document.asHashMap.getValue("osisFragments") as String
        assertEquals("true", document.asHashMap.getValue("compare"))
        assertContains(fragments, "MyBible-CompareFixture")
        assertContains(fragments, "You are the Christ")
        assertFalse(fragments.contains("CompareTreeFixture"))
        assertFalse(fragments.contains("CompareMissingFixture"))
    }

    /** These ownership collaborators are not used by compare content or restoration. */
    private inline fun <reified T : Any> blank(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
