package net.bible.android.control.navigation

import net.bible.android.control.page.PageControl
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** The sort order lives in the injected [AppSettings]; the button text comes from [CoreStrings] and names the OTHER order. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = net.bible.android.TestBibleApplication::class, sdk = [net.bible.android.TEST_SDK])
class NavigationControlSortOrderTest {
    private val stored = mutableMapOf<String, String?>()
    private val settings = object : AppSettings {
        override fun getString(key: String, default: String?) = stored[key] ?: default
        override fun setString(key: String, value: String?) { stored[key] = value }
        override fun getLong(key: String, default: Long) = TODO()
        override fun getInt(key: String, default: Int) = TODO()
        override fun getBoolean(key: String, default: Boolean) = TODO()
        override fun getDouble(key: String, default: Double) = TODO()
        override fun getFloat(key: String, default: Float) = TODO()
        override fun setLong(key: String, value: Long?) = TODO()
        override fun setInt(key: String, value: Int?) = TODO()
        override fun setBoolean(key: String, value: Boolean?) = TODO()
        override fun setDouble(key: String, value: Double?) = TODO()
        override fun setFloat(key: String, value: Float?) = TODO()
    }
    private val strings = object : CoreStrings by net.bible.test.testCoreStrings() {
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
        override val installZipSuccessful = "installed"
        override val installZipCanceled = "canceled"
        override val invalidModule = "invalid"
        override val sqliteInvalidFile = "invalid: %s"
        override fun sleepTimerStarted(minutes: Int) = "sleep $minutes"
        override val labelAll = ""
        override val errorOccurred = ""
        override fun somethingWithParenthesis(a: String, b: String) = ""
        override fun readingPlanDay(day: String) = ""
        override val sortByAlphabetical = "ALPHA"
        override val sortByBibleBook = "BOOK"
        override fun documentNotInstalled(initials: String) = ""
        override val noIndexedBibleWithStrongsRef = ""
        override val wordNotFoundInDictionaries = ""
    }
    private val control = NavigationControl(
        Mockito.mock(PageControl::class.java), Mockito.mock(DocumentBibleBooksFactory::class.java), settings, strings)

    @Test fun defaultsToBibleBookOrderAndOffersAlphabetical() {
        assertEquals(BibleBookSortOrder.BIBLE_BOOK, control.bibleBookSortOrder)
        assertEquals("ALPHA", control.bibleBookSortOrderButtonDescription)
    }

    @Test fun togglingPersistsInSettingsAndFlipsTheButtonText() {
        control.changeBibleBookSortOrder()
        assertEquals("ALPHABETICAL", stored["BibleBookSortOrder"])
        assertEquals("BOOK", control.bibleBookSortOrderButtonDescription)
        control.changeBibleBookSortOrder()
        assertEquals(BibleBookSortOrder.BIBLE_BOOK, control.bibleBookSortOrder)
    }
}
