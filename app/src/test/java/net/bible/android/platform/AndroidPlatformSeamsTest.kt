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
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

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
}
