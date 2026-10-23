package net.bible.service.download

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.sharedcore.http.InsecureUrlException
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** A repository that failed because its address is not https is named separately, so the UI can say why. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadManagerInsecureRepoTest {
    @Test fun refreshNamesInsecureRepository() = runBlocking {
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
                val wrapped = mock<Installer>()
                whenever(wrapped.getBooks()).thenThrow(RuntimeException("outer", RuntimeException("inner", InsecureUrlException("http://x"))))
                addInstaller("http-repo", wrapped)
                val plain = mock<Installer>()
                whenever(plain.getBooks()).thenThrow(RuntimeException("io", IOException("unreachable")))
                addInstaller("plain-repo", plain)
            })
        }
        assertTrue(manager.insecureRepos.isEmpty())
        manager.getDownloadableBooks(null, "http-repo", false)
        manager.getDownloadableBooks(null, "plain-repo", false)
        assertEquals(listOf("http-repo", "plain-repo"), manager.failedRepos)
        assertEquals(listOf("http-repo"), manager.insecureRepos)
    }
}
