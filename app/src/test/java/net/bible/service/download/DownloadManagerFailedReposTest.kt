package net.bible.service.download

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `failedRepos` was a `TreeSet<String>`: natural (code unit) order, no duplicates. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadManagerFailedReposTest {
    @Test fun failedReposReadSortedWithoutDuplicates() = runBlocking {
        val manager = DownloadManager(
            get(AppSettings::class.java), get(BookInstallWatcher::class.java),
            GenericFileDownloader(
                settings = get(AppSettings::class.java), notifier = get(UserNotifier::class.java),
                strings = get(CoreStrings::class.java), http = testDownloadHttp(), copyFile = testCopyFile,
            ),
            get(AppEnvironment::class.java),
        )
        DownloadManager::class.java.getDeclaredField("installManager").apply {
            isAccessible = true
            set(manager, InstallManager().apply {
                for (name in listOf("zeta", "alpha", "Beta", "ä-repo")) {
                    val installer = mock<Installer>()
                    whenever(installer.getBooks()).thenThrow(RuntimeException("unreachable repository"))
                    addInstaller(name, installer)
                }
            })
        }
        assertTrue(manager.failedRepos.isEmpty())
        // Reading the book list throws for each: recorded as failed, a repeated name only once
        for (name in listOf("zeta", "alpha", "Beta", "alpha", "ä-repo")) manager.getDownloadableBooks(null, name, false)
        assertEquals(listOf("Beta", "alpha", "zeta", "ä-repo"), manager.failedRepos)
    }
}
