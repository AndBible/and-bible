package net.bible.sharedlogic.platform

import net.bible.android.control.buildSharedBindings
import net.bible.android.control.document.DocumentControl
import net.bible.android.view.activity.search.AndroidEpubSearchService
import net.bible.service.format.SaxTextPolicy
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.service.sword.ContentSource
import net.bible.service.sword.HtmlToPlainText
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedcore.search.EpubSearchService
import net.bible.android.control.speak.SpeakPlatform
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Proves the platform-neutral definitions moved into `buildSharedBindings()` need only their ports from the platform
 * side. The big port interfaces are stubbed with JDK proxies (their members are never called while resolving).
 */
class SharedBindingsPortsTest {
    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected call of ${T::class.simpleName}.${m.name}")
        } as T

    private class RecordingEnabler : ManualInstallFolderEnabler {
        var calls = 0
        override fun enable() { calls++ }
    }

    private val enabler = RecordingEnabler()
    private val planCatalog = DistributedPlanCatalog { listOf(DistributedPlanDetails("code", "name", "desc")) }

    private fun app(): Koin = koinApplication {
        allowOverride(false)
        modules(
            buildSharedBindings(),
            module {
                single<AppDirectories> { stub() }
                single<AddonCatalog> { stub() }
                single<DistributedPlanCatalog> { planCatalog }
                single<ManualInstallFolderEnabler> { enabler }
                single { SaxTextPolicy.MODERN }
                single<HtmlToPlainText> { HtmlToPlainText { it } }
                single<ContentSource> { stub() }
                single<CoreStrings> { stub() }
                single<AppEnvironment> { stub() }
                single<ReadingPlanSource> { stub() }
                single<LocalCalendar> { stub() }
                single<AppSettings> { stub() }
                single<UserNotifier> { stub() }
                single<SpeakPlatform> { stub() }
            },
        )
    }.koin

    @Test
    fun sharedBindingsNeedOnlyPortsForTheMovedDefinitions() {
        val koin = app()
        koin.get<ReadingPlanTextFileDao>()
        koin.get<SwordContentFacade>()
        koin.get<DocumentControl>()
        val epub = koin.get<EpubSearchService>()
        assertTrue(epub is AndroidEpubSearchService)
        assertSame(epub, koin.get<AndroidEpubSearchService>())
    }

    @Test
    fun documentControlManualFolderCallReachesThePort() {
        val control = app().get<DocumentControl>()
        control.enableManualInstallFolder()
        assertEquals(1, enabler.calls)
    }
}
