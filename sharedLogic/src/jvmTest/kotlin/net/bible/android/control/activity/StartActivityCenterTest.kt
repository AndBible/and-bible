package net.bible.android.control.activity

import net.bible.android.control.buildSharedBindings
import net.bible.service.cloudsync.CloudSync
import net.bible.sharedlogic.startup.SharedAppStartup
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.ContentSource
import net.bible.service.sword.HtmlToPlainText
import net.bible.android.control.speak.SpeakPlatform
import org.crosswire.common.progress.JobManager
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [SharedAppStartup.startActivityCenter] against the real shared bindings (ports stubbed). */
class StartActivityCenterTest {
    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected call of ${T::class.simpleName}.${m.name}")
        } as T

    /** Every String property answers its own name, so lazily resolved texts work. */
    private fun strings(): CoreStrings =
        Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, m, _ ->
            if (m.returnType == String::class.java) m.name else error("unexpected call of CoreStrings.${m.name}")
        } as CoreStrings

    private fun app(): Koin = koinApplication {
        allowOverride(false)
        modules(
            buildSharedBindings(),
            module {
                single<AppDirectories> { stub() }
                single<net.bible.sharedlogic.platform.AddonCatalog> { stub() }
                single<DistributedPlanCatalog> { DistributedPlanCatalog { listOf(DistributedPlanDetails("c", "n", "d")) } }
                single<net.bible.sharedlogic.platform.ManualInstallFolderEnabler> { stub() }
                single { SaxTextPolicy.MODERN }
                single<HtmlToPlainText> { HtmlToPlainText { it } }
                single<ContentSource> { stub() }
                single<CoreStrings> { strings() }
                single<AppEnvironment> { stub() }
                single<ReadingPlanSource> { stub() }
                single<net.bible.sharedlogic.platform.LocalCalendar> { stub() }
                single<AppSettings> { stub() }
                single<UserNotifier> { stub() }
                single<SpeakPlatform> { stub() }
                single<net.bible.service.cloudsync.CloudAdapterFactory> { stub() }
                single<net.bible.sharedcore.platform.sync.SyncScheduler> { stub() }
                single<net.bible.sharedcore.cloud.DocumentSyncStarter> { stub() }
            },
        )
    }.koin

    @Test
    fun startResolvesTheSourcesAndEventsReachTheCenter() {
        val koin = app()
        try {
            SharedAppStartup.startActivityCenter(koin)
            val center = koin.get<ActivityCenter>()
            koin.get<CloudSync>().notifySyncRunning(true)
            assertTrue(center.isRunning("sync"))
            koin.get<CloudSync>().notifySyncRunning(false)
            assertFalse(center.isRunning("sync"))

            val job = JobManager.createJob("test-start-1", "TestStartJob", null)
            try {
                job.beginJob("x", 100)
                assertTrue(center.isRunning("job:test-start-1"))
            } finally { job.done() }
            assertFalse(center.isRunning("job:test-start-1"))
            assertNotNull(koin.getOrNull<DownloadActivitySource>())
        } finally {
            koin.close()
        }
        // Closing the graph detaches the job listener: a later job is not tracked.
        assertEquals(null, JobManager.findJob("test-start-1"))
    }
}
