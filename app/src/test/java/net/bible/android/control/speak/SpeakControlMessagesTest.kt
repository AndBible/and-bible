package net.bible.android.control.speak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.service.device.speak.FakeSpeakEngine
import net.bible.service.device.speak.FakeSpeakPlatform
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SpeakControlMessagesTest {
    @Test fun unspeakableBibleReportsThroughNotifierWithoutInitializingTts() {
        val strings = testCoreStrings()
        val infos = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { infos += message }
            override fun showError(message: String, cause: Throwable?) { error(message) }
        }
        val window = mock(WindowControl::class.java)
        val manager = mock(CurrentPageManager::class.java)
        val page = mock(CurrentPage::class.java)
        val book = mock(Book::class.java)
        val repository = mock(net.bible.android.control.page.window.WindowRepository::class.java)
        val activeWindow = net.bible.android.control.page.window.Window(
            net.bible.android.database.WorkspaceEntities.Window(
                net.bible.android.database.IdType(), false, false,
                windowLayout = net.bible.android.database.WorkspaceEntities.WindowLayout("VISIBLE"),
            ), manager, repository,
        )
        `when`(window.windowRepository).thenReturn(repository)
        org.robolectric.util.ReflectionHelpers.setField(repository, "_activeWindow", activeWindow)
        org.robolectric.util.ReflectionHelpers.setField(manager, "currentPage", page)
        `when`(page.currentDocument).thenReturn(book)
        `when`(book.bookCategory).thenReturn(BookCategory.BIBLE)
        `when`(page.isSpeakable).thenReturn(false)
        val scope = AppCoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val settings = testAppSettings()
            val control = SpeakControl(
                lazy { error("TTS must not initialize for an unspeakable page") }, window,
                FakeSpeakPlatform(FakeSpeakEngine(emptyMap())), notifier, strings, settings,
                scope, AdvancedSpeakSettings(settings), OrderedLauncher(scope), net.bible.service.download.FakeBookFactory(strings),
            )
            control.speakAny()
            assertEquals(listOf(strings.speakNoBooksAvailable), infos)
        } finally {
            scope.cancel()
            SpeakChanges.resetSubscribersForTest()
            SpeakSettingsChanges.resetSubscribersForTest()
        }
    }
}
