package net.bible.android.control.download

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.download.Repository
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.DownloadManager
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import com.nhaarman.mockitokotlin2.any
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.DownloadException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadQueueTest {
    @Test fun http404ReportsLocalizedStatusThroughNotifier() = runBlocking {
        val messages = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = Unit
            override fun showError(message: String, cause: Throwable?) { messages.add(message) }
        }
        val strings = mock<CoreStrings>()
        val url = "https://example.org/missing.zip"
        whenever(strings.httpStatusText(404)).thenReturn("not-found-marker")
        whenever(strings.errorDownloadingStatus(url, "not-found-marker", 404)).thenReturn("localized-status-marker")
        val book = mock<Book>()
        whenever(book.initials).thenReturn("QueueFixture")
        val installer = mock<Installer>()
        doThrow(DownloadException(URI(url), 404)).whenever(installer).install(any(), any())
        val manager = DownloadManager(org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppSettings::class.java), org.koin.java.KoinJavaComponent.get(net.bible.android.control.versification.BookInstallWatcher::class.java), GenericFileDownloader(settings = org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppSettings::class.java), notifier = org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.UserNotifier::class.java), strings = org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.CoreStrings::class.java)), org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val installers = InstallManager().apply { addInstaller("Fixture", installer) }
        DownloadManager::class.java.getDeclaredField("installManager").apply {
            isAccessible = true
            set(manager, installers)
        }
        val repo = Repository("Fixture", BookFilter { true }, manager)
        val statuses = mutableListOf<DocumentStatus>()
        DownloadQueue(statuses::add, notifier, strings).addDocumentToDownloadQueue(book, repo)
        assertEquals(listOf("localized-status-marker"), messages)
        assertEquals(DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, statuses.single().documentInstallStatus)
    }
}
