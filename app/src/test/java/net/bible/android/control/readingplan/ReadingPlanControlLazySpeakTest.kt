package net.bible.android.control.readingplan

import kotlinx.coroutines.runBlocking
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.platform.AndroidLocalCalendar
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.test.testAppSettings
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.test.testCoreStrings
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rendering a Verse widget only reads the plan's passage; it must never build SpeakControl, whose init registers
 * listeners and an active media session in a background process (#4175).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class ReadingPlanControlLazySpeakTest {
    @Test fun constructingAndLoadingAPlanDoesNotResolveSpeakControl() = runBlocking {
        var resolved = false
        val lazySpeak = lazy<SpeakControl> { resolved = true; error("SpeakControl must not be built") }
        val appScope = AppCoroutineScope()
        val repo = ReadingPlanRepository(daoProvider = { error("no db") }, appScope = appScope, calendar = AndroidLocalCalendar())
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = listOf("three")
            override fun openBuiltInPlan(code: String) = "# Three\n1=Gen.1\n"
        }
        val dao = ReadingPlanTextFileDao(source, repo, testCoreStrings(), AndroidLocalCalendar(), userPlanFolder = { "/nonexistent".toPath() }, providedPlans = { emptyMap() })
        val koin = GlobalContext.get()
        val control = ReadingPlanControl(lazySpeak, koin.get(), repo, dao, testAppSettings(), AndroidLocalCalendar())
        assertFalse(control.isReadingPlanSelected && resolved)
        assertEquals(false, resolved)
    }
}
