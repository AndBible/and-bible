package net.bible.service.download

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildCoreModule
import net.bible.android.control.document.DocumentControl
import net.bible.android.database.CustomRepository
import net.bible.android.platform.AndroidCoreStrings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.CoreStrings
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import net.bible.sharedcore.platform.AppSettings
import net.bible.android.control.versification.BookInstallWatcher
import org.koin.java.KoinJavaComponent

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadReadinessTest {
    @Before fun before() = DatabaseContainer.reset()
    @After fun after() = DatabaseContainer.reset()

    @Test fun injectedFactoryKeepsSpecialBookIdentityAcrossConsumers() {
        val isolated = koinApplication {
            modules(buildCoreModule(), module {
                single<CoreStrings> { AndroidCoreStrings(ApplicationProvider.getApplicationContext()) }
            })
        }
        try {
            val first = isolated.koin.get<FakeBookFactory>()
            val second = isolated.koin.get<FakeBookFactory>()
            assertSame(first, second)
            assertSame(first.journalDocument, second.journalDocument)
            assertSame(first.myNotesDocument, second.pseudoDocuments.first())
            // The done document domain receives this same singleton through its constructor.
            val globalFactory = KoinJavaComponent.get<FakeBookFactory>(FakeBookFactory::class.java)
            val consumer = KoinJavaComponent.get<DocumentControl>(DocumentControl::class.java)
            val injected = DocumentControl::class.java.getDeclaredField("fakeBookFactory").apply { isAccessible = true }.get(consumer) as FakeBookFactory
            assertSame(globalFactory.journalDocument, injected.journalDocument)
        } finally {
            isolated.close()
        }
    }

    @Test fun refreshLoadsCustomInstallersBeforeAnyRepositoryRead() = runBlocking {
        val dao = DatabaseContainer.instance.repoDb.customRepositoryDao()
        dao.insert(CustomRepository(name = "Readiness fixture", type = "sword-https", host = "example.org"))
        val manager = DownloadManager(
            KoinJavaComponent.get(AppSettings::class.java),
            KoinJavaComponent.get(BookInstallWatcher::class.java),
            GenericFileDownloader(settings = KoinJavaComponent.get(AppSettings::class.java), notifier = KoinJavaComponent.get(net.bible.sharedcore.platform.UserNotifier::class.java), strings = KoinJavaComponent.get(CoreStrings::class.java)),
        )
        manager.refreshInstallManager()
        val factory = RepoFactory(manager, KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val custom = factory.repositories().single { it.repoName == "Readiness fixture" }
        assertNotNull(manager.getInstallerFor(custom))
    }
}
