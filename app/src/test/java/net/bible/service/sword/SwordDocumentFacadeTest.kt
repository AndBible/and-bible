package net.bible.service.sword

import net.bible.service.download.FakeSwordBookFactory
import net.bible.service.download.RepoFactory
import org.crosswire.jsword.book.Book
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class SwordDocumentFacadeTest {
    private var swordDocumentFacade: SwordDocumentFacade? = null
    @Before
    fun setUp() {
		val repoFactory = Mockito.mock(RepoFactory::class.java)
        swordDocumentFacade = SwordDocumentFacade(repoFactory)
    }

    @Test
    @Throws(Exception::class)
    fun testIsIndexDownloadAvailable() {
        val fakeBook: Book = FakeSwordBookFactory.createFakeRepoBook("My Book", TestData.ESVS_CONF + "Version=1.0.1", "")
        MatcherAssert.assertThat(swordDocumentFacade!!.isIndexDownloadAvailable(fakeBook), IsEqual.equalTo(true))
    }

    internal interface TestData {
        companion object {
            const val ESVS_CONF = "[ESVS]\nDescription=My Test Book\nCategory=BIBLE\nModDrv=zCom\nBlockType=CHAPTER\nLang=en\nEncoding=UTF-8\nLCSH=Bible--Commentaries.\nDataPath=./modules/comments/zcom/mytestbook/\n"
        }
    }
}
