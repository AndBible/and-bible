package net.bible.android.platform

import android.content.Context
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.UserNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.buildCoreModule
import net.bible.android.control.coreModule
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import kotlinx.coroutines.Job
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.history.HistoryPlatform
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.readingplan.ReadingPlanSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidPlatformSeamsTest {
    @Test fun coreStringsReadTheResources() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val s = AndroidCoreStrings(ctx)
        assertEquals(ctx.getString(R.string.all), s.labelAll)
        assertEquals(ctx.getString(R.string.error_occurred), s.errorOccurred)
    }

    @Test fun dateFormatsMatchTheFrameworkCallsTheyReplace() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val f = AndroidDateTimeFormats(ctx)
        val t = 1_700_000_000_000L
        assertEquals(DateFormat.getDateFormat(ctx).format(Date(t)), f.shortDate(t))
        assertEquals(DateFormat.getTimeFormat(ctx).format(Date(t)), f.shortTime(t))
        assertEquals(DateFormat.format("EEE, yyyy-MM-dd HH:mm", Date(t)).toString(), f.pattern("EEE, yyyy-MM-dd HH:mm", t))
    }

    /** The bookmark list's date line: weekday, date and 24h time, in the default time zone and locale. */
    @Test fun patternFormatsInTheDefaultZoneAndLocale() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            Locale.setDefault(Locale.US)
            val f = AndroidDateTimeFormats(ApplicationProvider.getApplicationContext())
            assertEquals("Sat, 2022-01-01 13:05", f.pattern("EEE, yyyy-MM-dd HH:mm", 1_641_042_300_000L))
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
        }
    }

    @Test fun appScopeSurvivesAFailingChild() = runBlocking {
        val scope = AppCoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })
        scope.launch { error("boom") }.join()
        assertTrue(scope.launch { }.let { it.join(); it.isCompleted && !it.isCancelled })
    }

    @Test fun koinResolvesEverySeam() {
        listOf(AppSettings::class, UserNotifier::class, CoreStrings::class, DateTimeFormats::class, AppCoroutineScope::class)
            .forEach { assertNotNull(it.simpleName, GlobalContext.get().getOrNull<Any>(it)) }
    }

    /**
     * CoreModuleVerifyTest checks constructors only, and its extraTypes (CoroutineScope, Function0, ...) let lambda
     * and scope parameters through unchecked. This actually BUILDS every definition L1a added or rewired, so a
     * definition whose factory throws (a wrong get<>(), a missing binding) fails here.
     *
     * It resolves into an isolated Koin application built from its OWN [buildCoreModule] instance, never the shared
     * [coreModule] value: a Koin `Module` holds its definitions' instance factories, and with them the cached
     * singletons, so a second application loading the same `Module` object shares the global one's singletons.
     * Resolving there handed this test's objects to the global Koin, and `close()` dropped every global coreModule
     * singleton, so later tests got a new WindowControl while `by inject()` holders kept the old one. The last
     * assert guards that: the suite's global singletons are the same objects afterwards.
     */
    @Test fun koinBuildsEveryDefinitionL1aAdded() {
        val types = listOf(
            OrderedLauncher::class, BookInstallWatcher::class, ProgressJsActions::class, BookmarkJsActions::class,
            DocumentControl::class, ReadingPlanTextFileDao::class, ReadingPlanRepository::class,
            ReadingPlanSource::class, HistoryPlatform::class, LinkPlatform::class, DocumentSyncStarter::class,
            AppSettings::class, AppCoroutineScope::class, UserNotifier::class, CoreStrings::class, DateTimeFormats::class,
        )
        val global = GlobalContext.get()
        val globalBefore = (types + listOf(WindowControl::class, BookmarkControl::class)).associateWith { global.get<Any>(it) }
        val app = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(buildCoreModule())
        }
        try {
            types.forEach { type ->
                val instance: Any = app.koin.get(type, null, null) // throws (with the cause) if the factory fails
                assertTrue("${type.simpleName} resolved to ${instance::class}", type.isInstance(instance))
                // AppSettings is bound to the process-wide CommonUtils.settings, so only it may be the same object
                if (type != AppSettings::class) {
                    assertNotSame("${type.simpleName} must be built by the isolated application", globalBefore[type], instance)
                }
            }
        } finally {
            // the isolated app's own scope: cancel it so nothing it launched outlives this test
            app.koin.getOrNull<AppCoroutineScope>()?.coroutineContext?.get(Job)?.cancel()
            app.close()
        }
        globalBefore.forEach { (type, before) ->
            assertSame("the global ${type.simpleName} singleton must survive the isolated app", before, global.get<Any>(type))
        }
    }
}
