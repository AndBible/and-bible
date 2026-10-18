package net.bible.sharedlogic.addons

import fi.sykero.kdom.Content
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.AndBibleAddons
import okio.Path.Companion.toOkioPath
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.basic.AbstractBook
import org.crosswire.jsword.book.basic.DefaultBookMetaData
import org.crosswire.jsword.book.sword.processing.RawTextToXmlProcessor
import org.crosswire.jsword.passage.Key
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Oracle: [SharedAddonScanner] (used on iOS) must answer exactly like `AndBibleAddons` (Android) on the same books.
 * The book list of `AndBibleAddons` is injected through its `_addons` cache, so no installed-book registry is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SharedAddonScannerOracleTest {
    @get:Rule val folder = TemporaryFolder()

    private class Meta(initials: String, location: File, private val multi: Map<String, List<String>>, single: Map<String, String>) :
        DefaultBookMetaData(null, initials, BookCategory.AND_BIBLE) {
        init {
            setInitials(initials)
            this.location = KUri(location.toURI().toString())
            single.forEach { (k, v) -> setProperty(k, v) }
        }
        override fun getValues(key: String?): Collection<String>? = multi[key] ?: super.getValues(key)
    }

    private class StubBook(meta: Meta) : AbstractBook(meta, null) {
        override fun getOsis(key: Key?, noOpRawTextProcessor: RawTextToXmlProcessor?): List<Content> = TODO()
        override fun getGlobalKeyList(): Key = TODO()
        override fun getValidKey(name: String?): Key = TODO()
        override fun getKey(name: String?): Key = TODO()
        override fun createEmptyKeyList(): Key = TODO()
        override fun getOsisIterator(key: Key?, allowEmpty: Boolean, allowGenTitles: Boolean): MutableIterator<Content> = TODO()
        override fun contains(key: Key?): Boolean = false
        override fun getRawText(key: Key?): String = TODO()
        override fun isWritable(): Boolean = false
        override fun setRawText(key: Key?, rawData: String?) {}
        override fun setAliasKey(alias: Key?, source: Key?) {}
    }

    private fun book(
        initials: String, dir: String, plans: List<String> = emptyList(), dateBased: String? = null,
        prompts: String? = null, createPrompts: Boolean = true,
    ): Book {
        val location = folder.newFolder(dir)
        if (prompts != null && createPrompts) File(location, prompts).also { it.parentFile.mkdirs() }.writeText("x")
        val single = buildMap {
            dateBased?.let { put("AndBibleReadingPlanDateBased", it) }
            prompts?.let { put("AndBibleProvidesPrompts", it) }
        }
        val multi = if (plans.isEmpty()) emptyMap() else mapOf("AndBibleProvidesReadingPlan" to plans)
        return StubBook(Meta(initials, location, multi, single))
    }

    private val addonsField = AndBibleAddons::class.java.getDeclaredField("_addons").apply { isAccessible = true }

    @After fun resetAddons() = addonsField.set(AndBibleAddons, null)

    private fun inject(books: List<Book>) = addonsField.set(AndBibleAddons, books)

    @Test fun readingPlansMatchAndroid() {
        val books = listOf(
            book("A", "a", plans = listOf("one.properties", "sub/two.v2.properties"), dateBased = "True"),
            book("B", "b", plans = listOf("three.properties"), dateBased = "yes"),
            book("C", "c", plans = listOf("one.properties")), // overrides A's "one"
            book("D", "d"),
            book("E", "e", plans = listOf("lower.properties"), dateBased = "true"),
            book("F", "f", plans = listOf("upper.properties"), dateBased = "TRUE"),
            book("G", "g", plans = listOf("/abs.properties", "/dir/abs2.properties")), // Android drops the leading '/'
        )
        inject(books)
        val android = AndBibleAddons.providedReadingPlans
        val shared = SharedAddonScanner({ books }).readingPlans()
        assertEquals(setOf("one", "two.v2", "three", "lower", "upper", "abs", "abs2"), android.keys)
        assertTrue(android.getValue("lower").isDateBased && android.getValue("upper").isDateBased)
        assertEquals(android.keys, shared.keys)
        for ((code, a) in android) {
            val s = shared.getValue(code)
            assertEquals(code, a.book.initials, s.book.initials)
            assertEquals(code, a.fileName, s.fileName)
            assertEquals(code, a.isDateBased, s.isDateBased)
            assertEquals(code, a.file.toOkioPath(), s.path)
        }
        assertTrue(shared.getValue("two.v2").isDateBased)
        assertEquals("C", shared.getValue("one").book.initials)
    }

    @Test fun promptPacksMatchAndroid() {
        val books = listOf(
            book("A", "a", prompts = "pack.csv"),
            book("B", "b", prompts = "deep/dir/p.csv"),
            book("Missing", "m", prompts = "gone.csv", createPrompts = false),
            book("None", "n"),
            book("Abs", "x", prompts = "/lead.csv"), // Android drops the leading '/'
        )
        inject(books)
        val android = AndBibleAddons.providedPromptPacks.map { it.moduleName to it.file.path }
        val shared = SharedAddonScanner({ books }).promptPacks().map { it.moduleName to it.path }
        assertEquals(listOf("A", "B", "Abs"), android.map { it.first })
        assertEquals(android, shared)
    }
}
