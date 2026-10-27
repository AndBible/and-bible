package net.bible.android.control.page.window

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.doesNotExist
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression for #3838: a missing module must not be replaced and synced back as a default. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceMissingDocumentsTest {
    @Test
    fun restoredWindowsKeepTheirSavedDocumentsAsModulesArriveOneByOne() {
        val fixture = Fixture()
        val registry = Books.installed()
        val originalBooks = registry.getBooks().toList()
        val factory = get<FakeBookFactory>(FakeBookFactory::class.java)
        val categories = listOf(
            DocumentCategory.BIBLE to BookCategory.BIBLE,
            DocumentCategory.COMMENTARY to BookCategory.COMMENTARY,
            DocumentCategory.DICTIONARY to BookCategory.DICTIONARY,
            DocumentCategory.GENERAL_BOOK to BookCategory.GENERAL_BOOK,
            DocumentCategory.MAPS to BookCategory.MAPS,
        )
        val books = categories.flatMap { (category, bookCategory) ->
            (1..2).map { index ->
                val initials = "Sync3838_${category.name}_$index"
                category to factory.createFakeRepoBook(
                    initials,
                    "[$initials]\nDescription=$initials\nCategory=${bookCategory.name}\nVersification=KJV",
                    "", bookCategory,
                )
            }
        }
        val workspace = WorkspaceEntities.Workspace("Synced before documents")
        val savedPages = books.map { (category, book) ->
            savedPage(IdType(), category, book.initials)
        }
        try {
            originalBooks.forEach { registry.removeBook(it) }
            runBlocking {
                workspaceDao.insertWorkspace(workspace)
                savedPages.forEachIndexed { index, page ->
                    workspaceDao.insertWindow(WorkspaceEntities.Window(
                        workspaceId = workspace.id,
                        isSynchronized = true,
                        isPinMode = false,
                        windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.name),
                        id = page.windowId,
                        orderNumber = index,
                    ))
                    workspaceDao.insertPageManager(page)
                }
                fixture.repo.loadFromDb(workspace.id)
            }
            val installed = mutableMapOf<String, Book>()
            fun assertWindowsAndSave(stage: String) {
                savedPages.forEachIndexed { index, saved ->
                    val (category, book) = books[index]
                    val manager = fixture.repo.getWindow(saved.windowId)!!.pageManager
                    // Repeated reads used to drop the placeholder because it is not in the registry.
                    repeat(3) {
                        assertEquals("$stage: window category", category, manager.currentPage.documentCategory)
                        val current = manager.currentPage.currentDocument
                        assertEquals("$stage: window document", book.initials, current?.initials)
                        if (book.initials in installed) {
                            assertSame("$stage: resolve the installed book", installed[book.initials], current)
                        } else {
                            assertTrue("$stage: keep the missing document", current?.doesNotExist == true)
                        }
                    }
                    // Force a real workspace write, as unrelated window changes would during sync.
                    manager.jsState = stage
                }
                fixture.repo.saveIntoDb(false)
                runBlocking {
                    fixture.persistence.awaitPersisted()
                    savedPages.forEachIndexed { index, saved ->
                        val stored = workspaceDao.pageManager(saved.windowId)!!
                        val (category, book) = books[index]
                        assertEquals("$stage: persisted category", category.name, stored.currentCategoryName)
                        assertEquals("$stage: persisted document", book.initials, document(stored, category))
                        assertEquals("$stage: the save actually ran", stage, stored.jsState)
                    }
                    fixture.repo.loadFromDb(workspace.id)
                }
            }
            assertWindowsAndSave("No documents installed")
            // Install the first book of each category before the second: other windows must wait.
            val installOrder = books.filterIndexed { index, _ -> index % 2 == 0 } +
                books.filterIndexed { index, _ -> index % 2 == 1 }.reversed()
            installOrder.forEach { (_, book) ->
                registry.addBook(book)
                installed[book.initials] = book
                assertWindowsAndSave("Installed ${book.initials}")
            }
        } finally {
            fixture.repo.clear(destroy = true)
            books.forEach { (_, book) ->
                if (registry.getBook(book.initials) != null) registry.removeBook(book)
            }
            originalBooks.forEach { if (registry.getBook(it.initials) == null) registry.addBook(it) }
            fixture.close()
        }
    }

    private fun savedPage(windowId: IdType, category: DocumentCategory, initials: String) =
        WorkspaceEntities.PageManager(
            windowId = windowId,
            biblePage = WorkspaceEntities.BiblePage(
                initials.takeIf { category == DocumentCategory.BIBLE }, WorkspaceEntities.Verse("KJV", 54, 3, 16),
            ),
            commentaryPage = WorkspaceEntities.CommentaryPage(initials.takeIf { category == DocumentCategory.COMMENTARY }, null, null),
            dictionaryPage = WorkspaceEntities.Page(initials, "entry", 0).takeIf { category == DocumentCategory.DICTIONARY },
            generalBookPage = WorkspaceEntities.Page(initials, "chapter", 0).takeIf { category == DocumentCategory.GENERAL_BOOK },
            mapPage = WorkspaceEntities.Page(initials, "map", 0).takeIf { category == DocumentCategory.MAPS },
            currentCategoryName = category.name,
            textDisplaySettings = null,
            jsState = null,
        )

    private fun document(page: WorkspaceEntities.PageManager, category: DocumentCategory): String? = when (category) {
        DocumentCategory.BIBLE -> page.biblePage.document
        DocumentCategory.COMMENTARY -> page.commentaryPage?.document
        DocumentCategory.DICTIONARY -> page.dictionaryPage?.document
        DocumentCategory.GENERAL_BOOK -> page.generalBookPage?.document
        DocumentCategory.MAPS -> page.mapPage?.document
        else -> error("Unexpected category: $category")
    }
}
