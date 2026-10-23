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
import org.crosswire.jsword.book.install.sword.HttpsSwordInstaller
import org.crosswire.common.util.HttpBody
import org.crosswire.common.util.HttpHead
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.KUri
import org.crosswire.common.util.WebResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * A repository that failed because its address is not https is named separately, so the UI can say why.
 * The refresh reason is redirect-only: migrated http SWORD repositories are fetched over https and never raise
 * [InsecureUrlException]; it arises when an https request is redirected to http.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadManagerInsecureRepoTest {
    /**
     * The production path with a real [HttpsSwordInstaller]: the transport refuses an https-to-http redirect,
     * jsword wraps it (WebResource -> InstallException) and DownloadManager must still find the cause.
     */
    @Test fun realInstallerRedirectToHttpNamesInsecureRepository() = runBlocking {
        val saved = WebResource.transport
        try {
            WebResource.transport = object : HttpTransport {
                override suspend fun head(uri: KUri, timeoutMs: Int): HttpHead = throw InsecureUrlException("http://redirect.example/x")
                override suspend fun get(uri: KUri, timeoutMs: Int): HttpBody = throw InsecureUrlException("http://redirect.example/x")
            }
            val manager = newManager()
            DownloadManager::class.java.getDeclaredField("installManager").apply {
                isAccessible = true
                set(manager, InstallManager().apply {
                    addInstaller("redirect-repo", HttpsSwordInstaller().apply {
                        setHost("insecure-redirect-${System.nanoTime()}.example")
                        setPackageDirectory("/packages")
                        setCatalogDirectory("/")
                    })
                })
            }
            manager.getDownloadableBooks(null, "redirect-repo", true)
            assertEquals(listOf("redirect-repo"), manager.failedRepos)
            assertEquals(listOf("redirect-repo"), manager.insecureRepos)
        } finally {
            WebResource.transport = saved
        }
    }

    private fun newManager() = DownloadManager(
        get(AppSettings::class.java), get(BookInstallWatcher::class.java),
        GenericFileDownloader(
            settings = get(AppSettings::class.java), notifier = get(UserNotifier::class.java),
            strings = get(CoreStrings::class.java), http = testDownloadHttp(), copyFile = testCopyFile,
        ),
        get(AppEnvironment::class.java),
    )

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
