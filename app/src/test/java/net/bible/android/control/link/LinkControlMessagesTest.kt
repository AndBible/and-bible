package net.bible.android.control.link

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.test.testAppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.NoSuchKeyException
import org.koin.core.context.GlobalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LinkControl]'s user-facing errors go through the injected [UserNotifier] with text from [CoreStrings]
 * (no Dialogs, no resources): a fake notifier records exactly what the user would be told.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LinkControlMessagesTest {
    private val shown = mutableListOf<String>()
    private val notifier = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) { shown += message }
    }
    private val strings = object : CoreStrings {
        override val storageSpaceWarning = "storageSpaceWarning"
        override val errorDownloading = "errorDownloading"
        override val journalDescription = "journalDescription"
        override val studypads = "studypads"
        override val compareDescription = "compareDescription"
        override val compareAbbreviation = "compareAbbreviation"
        override val memorizeDescription = "memorizeDescription"
        override val memorizeAbbreviation = "memorizeAbbreviation"
        override val multiDescription = "multiDescription"
        override val multiAbbreviation = "multiAbbreviation"
        override val myNotesDescription = "myNotesDescription"
        override val myNotesAbbreviation = "myNotesAbbreviation"
        override fun httpStatusText(code: Int) = "http-$code"
        override fun errorDownloadingStatus(url: String, statusText: String, code: Int) = "$url $statusText $code"

        override fun syncCategoryContents(category: String): String = error("unused")
        override val speakNoBooksAvailable = "speakNoBooksAvailable"
        override val speakGeneralError = "speakGeneralError"
        override val verseRangeModeDisabled = "verseRangeModeDisabled"
        override val rewind = "rewind"
        override val forward = "forward"
        override val pause = "pause"
        override val stop = "stop"
        override val speakStatusStopped = "speakStatusStopped"
        override val ttsLangNotAvailable = "ttsLangNotAvailable"
        override val installZipSuccessful = "installed"
        override val installZipCanceled = "canceled"
        override val invalidModule = "invalid"
        override val sqliteInvalidFile = "invalid: %s"
        override fun sleepTimerStarted(minutes: Int) = "sleep $minutes"
        override val labelAll = "all"
        override val errorOccurred = "error"
        override fun somethingWithParenthesis(a: String, b: String) = "$a ($b)"
        override fun readingPlanDay(day: String) = "day $day"
        override val sortByAlphabetical = "alpha"
        override val sortByBibleBook = "book"
        override fun documentNotInstalled(initials: String) = "FAKE please download $initials"
        override val noIndexedBibleWithStrongsRef = "FAKE no strongs bible"
        override val wordNotFoundInDictionaries = "FAKE word not found"
    }
    private val platform = object : LinkPlatform {
        override fun searchStrongsInReadingView(ref: String, documentInitials: List<String>) = false
        override fun openRoute(route: String) {}
    }

    private fun control() = LinkControl(fakeBookFactory = net.bible.service.download.FakeBookFactory(net.bible.test.testCoreStrings()),
        Mockito.mock(WindowControl::class.java), Mockito.mock(BookmarkControl::class.java), GlobalContext.get().get<SearchControl>(),
        testAppSettings(), notifier, strings, platform,
    )

    private fun dictionary(throwing: Boolean): Book = Mockito.mock(Book::class.java).also {
        if (throwing) Mockito.`when`(it.getKey(Mockito.anyString())).thenThrow(NoSuchKeyException("no such key"))
    }

    @Test fun lookupWithNoInstalledDictionaryTellsTheUserTheWordWasNotFound() {
        assertFalse(control().lookupInDictionaries("word", emptyList()))
        assertEquals(listOf("FAKE word not found"), shown)
    }

    @Test fun blankDictionaryLookupTellsTheUserTheWordWasNotFound() {
        // a dictionary IS installed, so only the blank-text branch can produce the message
        assertFalse(control().lookupInDictionaries("  .,; ", listOf(dictionary(throwing = true))))
        assertEquals(listOf("FAKE word not found"), shown)
    }

    @Test fun lookupMissingInEveryDictionaryTellsTheUserTheWordWasNotFound() {
        assertFalse(control().lookupInDictionaries("zzzz", listOf(dictionary(throwing = true), dictionary(throwing = true))))
        assertEquals(listOf("FAKE word not found"), shown)
    }

    @Test fun uninstalledDocumentLinkNamesTheDocumentToDownload() {
        control().loadApplicationUrl(BibleLink("sword", "sword://NoSuchModuleXyz/Matt.1.1"))
        assertEquals(listOf("FAKE please download NoSuchModuleXyz"), shown)
    }
}
