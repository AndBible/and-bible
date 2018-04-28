package net.bible.service.download

import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import robolectric.MyRobolectricTestRunner

@RunWith(MyRobolectricTestRunner::class)
@Config(constants = BuildConfig::class, application = TestBibleApplication::class)
class CrosswireRepoIT {

    private lateinit var crosswireRepo: CrosswireRepo

    @Before
    @Throws(Exception::class)
    fun setUp() {
        crosswireRepo = CrosswireRepo()
    }

    @Test
    @Throws(InstallException::class)
    fun getRepoBooks() {
        val repoBooks = crosswireRepo.getRepoBooks(true)
        assertThat(repoBooks.size, greaterThan(100))
    }

    @Test
    fun getRepoName() {
        assertThat(crosswireRepo.repoName, equalTo("CrossWire"))
    }

    @Test
    fun downloadDocument() {
        val repoBooks = crosswireRepo.getRepoBooks(false)
        val kjv = repoBooks.findLast { it.initials == "KJV" }
        print(kjv)
        crosswireRepo.downloadDocument(kjv)
        assertThat(Books.installed().getBook("KJV"), not(nullValue()))
    }

    @Test
    fun downloadDocumentsRequiredForTests() {
        val testBooks = arrayOf("KJV", "ISV", "ESV2011")
        crosswireRepo.getRepoBooks(false)
                .filter {testBooks.contains(it.initials)}
                .filter {Books.installed().getBook(it.initials) == null}
                .forEach {crosswireRepo.downloadDocument(it)}
        assertThat(Books.installed().books.size, greaterThanOrEqualTo(testBooks.size))
    }
}