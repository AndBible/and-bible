package net.bible.sharedui.app.chooser

import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentPageBase
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

class ChooserMappingTest {
    private val registered = mutableListOf<Book>()
    private val previous = mutableListOf<Book>()
    @BeforeTest fun start() {
        listOf("K4RawText", "K4RawCom", "K4GenBook").forEach { id ->
            Books.installed().getBook(id)?.let { previous += it; Books.installed().removeBook(it) }
        }
        startKoin { modules(module { single<PlatformLocales> { object : PlatformLocales {
            override val defaultLanguage = "en"
            override fun iso3ByIso2() = mapOf("en" to "eng")
        } } }) }
    }
    @AfterTest fun stop() {
        registered.forEach { org.crosswire.common.activate.Activator.deactivate(it); Books.installed().removeBook(it) }
        previous.forEach { Books.installed().addBook(it) }
        stopKoin()
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

    // Page construction is internal to sharedLogic. Keep the real cache implementation, without a window/DB graph.
    private inline fun <reified T : CurrentPageBase> page(book: Book): T {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        return (unsafe.allocateInstance(T::class.java) as T).also {
            CurrentPageBase::class.java.getDeclaredField("_currentDocument").apply { isAccessible = true }.set(it, book)
        }
    }

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
        val page = page<CurrentMapPage>(register(map))
        val keys = page.keyChooserKeys()
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), keys.map { it.getName() })
        assertSame(keys, page.keyChooserKeys())
        assertEquals("Chapter 2", keys[2].getName())
    }

    @Test fun generalBookUsesItsGlobalKeys() = runTest {
        val page = page<CurrentGeneralBookPage>(register(book("K4GenBook")))
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), page.keyChooserKeys().map { it.getName() })
        assertSame(page.cachedGlobalKeyList, page.keyChooserKeys())
    }
}
