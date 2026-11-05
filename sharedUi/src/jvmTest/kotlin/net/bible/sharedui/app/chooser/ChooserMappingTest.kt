package net.bible.sharedui.app.chooser

import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.download.FakeBookFactory
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBookMetaData
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import org.crosswire.jsword.book.Books
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import net.bible.sharedlogic.platform.PlatformLocales
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ChooserMappingTest {
    private val registered = mutableListOf<Book>()
    private val previous = mutableListOf<Book>()
    private val managers = mutableListOf<CurrentPageManager>()
    @BeforeTest fun start() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        listOf("K4RawText", "K4RawCom", "K4GenBook").forEach { id ->
            Books.installed().getBook(id)?.let { previous += it; Books.installed().removeBook(it) }
        }
        val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
            when (method.name) {
                "getMyNotesDescription", "getMyNotesAbbreviation" -> "My Notes"
                else -> error("Unexpected core string: ${method.name}")
            }
        } as CoreStrings
        startKoin { modules(module {
            single { FakeBookFactory(strings) }
            single<PlatformLocales> { object : PlatformLocales {
                override val defaultLanguage = "en"
                override fun iso3ByIso2() = mapOf("en" to "eng")
            } }
        }) }
    }
    @AfterTest fun stop() {
        managers.forEach { it.destroy() }
        registered.forEach { org.crosswire.common.activate.Activator.deactivate(it); Books.installed().removeBook(it) }
        previous.forEach { Books.installed().addBook(it) }
        stopKoin()
        Dispatchers.resetMain()
    }
    private fun register(book: Book): Book = book.also {
        Books.installed().addBook(it)
        registered += it
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected port call: ${method.name}")
    } as T

    private fun book(initials: String, language: String = "en", category: String? = null): Book {
        val root = File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        val conf = root.resolve("mods.d/$initials.conf").readText()
        val md = SwordBookMetaData(conf.replace("Lang=en", "Lang=$language").toByteArray(), "$initials.conf").apply {
            library = root.toURI()
            location = NetUtil.lengthenURI(library!!, getProperty(SwordBookMetaData.KEY_DATA_PATH)!!)
            category?.let { setProperty("Category", it) }
            setProperty("Version", "1.0")
            setProperty("SourceRepository", "ChooserFixture")
        }
        return md.bookType!!.createBook(md)
    }

    // These unused collaborators are never called; pages themselves must run their real initializers.
    private inline fun <reified T> blank(): T {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        return unsafe.allocateInstance(T::class.java) as T
    }

    private fun manager(): CurrentPageManager =
        CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), blank<WindowControl>()).also { managers += it }

    @Test fun docRowsMatchAndroidMapping() {
        val books = listOf(book("K4RawText", "en-US"), book("K4RawCom"), book("K4GenBook"))
        val mapper = DocRowMapper(DownloadControl(unused<UserNotifier>(), unused<CoreStrings>(), unused<AppEnvironment>()), books)
        val rows = books.map(mapper::toDocRow)
        assertEquals(listOf("K4RawText", "K4RawCom", "K4GenBook"), rows.map { it.docId })
        assertEquals(listOf(DocCategory.BIBLE, DocCategory.COMMENTARY, DocCategory.GENERAL_BOOK), rows.map { it.category })
        assertEquals(listOf("en", "en", "en"), rows.map { it.language.code })
        assertSame(rows[0].language, rows[1].language)
        rows.zip(books).forEach { (row, book) ->
            assertEquals(book.osisID, row.osisId)
            assertEquals(book.name, row.name)
            assertEquals(book.abbreviation, row.abbreviation)
            assertEquals("ChooserFixture", row.repository)
            assertEquals(DocInstallStatus.NOT_INSTALLED, row.installStatus)
            assertEquals(0, row.percentDone)
            assertFalse(row.canDelete)
            assertFalse(row.recommended)
            assertFalse(row.badWarn)
            assertEquals(book.isLocked, row.locked)
            assertEquals(book.isEnciphered, row.enciphered)
            assertEquals(null, row.installSizeMb)
        }
    }

    @Test fun mapKeysForAMapModule() {
        val map = book("K4GenBook", category = "Maps")
        assertEquals(org.crosswire.jsword.book.BookCategory.MAPS, map.bookCategory)
        val page = manager().currentMap.apply { onlySetCurrentDocument(register(map)) }
        val keys = page.keyChooserKeys()
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), keys.map { it.getName() })
        assertSame(keys, page.keyChooserKeys())
        assertEquals("Chapter 2", keys[2].getName())
    }

    @Test fun generalBookUsesItsGlobalKeys() = runTest {
        val page = manager().currentGeneralBook.apply { onlySetCurrentDocument(register(book("K4GenBook"))) }
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), page.keyChooserKeys().map { it.getName() })
        assertSame(page.cachedGlobalKeyList, page.keyChooserKeys())
    }
}
