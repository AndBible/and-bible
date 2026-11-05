package net.bible.sharedui.app.readingplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.CoroutineDispatcher
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.database.DbDate
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.download.FakeBookFactory
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.readingplan.nav.readingPlanNavGraph
import net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class ReadingPlanJourneyTest {
    @Test fun selectingTickingAndReopeningUsesHostAndRealServices() {
        val main = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
            override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
        }
        Dispatchers.setMain(main)
        val appJob = SupervisorJob()
        val windowJob = SupervisorJob()
        var speakToRelease: SpeakControl? = null
        try {
        val values = mutableMapOf<String, String?>()
        val settings = proxy<AppSettings> { method, args ->
            when (method) {
                "getString" -> values[args[0] as String] ?: args.getOrNull(1)
                "setString" -> { values[args[0] as String] = args[1] as String?; Unit }
                "getBoolean" -> args[1]
                else -> error("Unexpected settings: $method")
            }
        }
        val strings = proxy<CoreStrings> { method, args ->
            when (method) {
                "readingPlanDay" -> "Day ${args[0]}"
                "getMyNotesDescription", "getMyNotesAbbreviation" -> "My Notes"
                else -> error("Unexpected core string: $method")
            }
        }
        val calendar = proxy<LocalCalendar> { method, args ->
            when (method) {
                "startOfToday" -> 1_800_000_000_000L
                "addDays" -> (args[0] as Long) + (args[1] as Int) * 86_400_000L
                "formatDefaultDate" -> "date"
                else -> error("Unexpected calendar: $method")
            }
        }
        val plans = PlanDao()
        val windowControl = WindowControl()
        val windowRepository = WindowRepository(kotlinx.coroutines.CoroutineScope(main + windowJob))
        val scope = AppCoroutineScope(main + appJob)
        val fakeBooks = FakeBookFactory(strings)
        startKoin { modules(module { single { settings }; single { fakeBooks }; single { WindowStateServiceImpl() } }) }
        val pageManager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), windowControl)
        pageManager.currentBible.onlySetCurrentDocument(fakeBooks.createFakeRepoBook(
            "TestBible", "[TestBible]\nDescription=TestBible\nCategory=Biblical Texts\nVersification=KJV\nAndBibleSpecial=1", "",
        ))
        val speak = SpeakControl(lazy { blank<net.bible.service.device.speak.TextToSpeechServiceManager>() }, windowControl,
            proxy<SpeakPlatform> { method, _ -> when (method) {
                "isSupported" -> false
                "attachMediaControls" -> Unit
                else -> error(method)
            } },
            proxy<UserNotifier> { method, _ -> error(method) }, strings, settings, scope,
            AdvancedSpeakSettings(settings), OrderedLauncher(scope), fakeBooks)
        speakToRelease = speak
        val repo = ReadingPlanRepository({ plans }, scope, calendar, today = { DbDate(1_800_000_000_000L - 7 * 86_400_000L) })
        var language by mutableStateOf("en")
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = listOf("plan")
            override fun openBuiltInPlan(code: String) = "# Plan\n1=Gen.1,Matt.1\n2=Gen.2,Matt.2\n3=Gen.3,Matt.3\n"
        }
        val textDao = ReadingPlanTextFileDao(source, repo, strings, calendar,
            userPlanFolder = { "/nonexistent-reading-plans".toPath() }, providedPlans = { emptyMap() },
            distributedPlans = { listOf(DistributedPlanDetails("plan", if (language == "en") "1-Year through Bible" else "Vuodessa läpi Raamatun", "")) })
        val control = ReadingPlanControl(speak, windowControl, repo, textDao, settings, calendar)
        val window = Window(WorkspaceEntities.Window(IdType(), false, false, windowLayout = WorkspaceEntities.WindowLayout("VISIBLE")), pageManager, windowRepository)
        try {
            windowControl.windowRepository = windowRepository
            windowRepository.activeWindow = window
            runComposeUiTest {
                var host by mutableStateOf<ReadingPlanHost?>(null)
                var generation by mutableStateOf(0)
                setContent {
                    val uiScope = rememberCoroutineScope()
                    val nav = rememberNavController()
                    val shell = remember { object : HostShell {
                        override val hostScope = uiScope
                        override fun exitHost() = Unit
                        override fun setWindowTitle(title: String) = Unit
                        override fun recreateHost() = Unit
                        override fun openReading() = Unit
                        override fun setHistoryRoute(owner: Any, route: String) = Unit
                        override fun clearHistoryRoute(owner: Any) = Unit
                        override fun goBackInHistory() = false
                        override fun navigate(route: String) { nav.navigate(route) }
                    } }
                    val current = remember(generation) {
                        ReadingPlanHost(shell, iosStrings("en"), object : ReadingPlanHostPlatform {
                            override val isPortrait = true
                            override fun localYmd(epochMs: Long): Triple<Int, Int, Int> = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().let { Triple(it.year, it.monthValue, it.dayOfMonth) }
                            override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int): Long = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).withYear(year).withMonth(month1to12).withDayOfMonth(day).toInstant().toEpochMilli()
                        }, { _, _, _ -> }, object : DocumentPicker {
                            override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = emptyList<DocumentRef>()
                            override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
                            override suspend fun openDocumentTree(): DocumentRef? = null
                        }, { control }, { speak }, { scope })
                    }
                    host = current
                    val deps = remember(current, nav) { current.navDeps(nav) }
                    CompositionLocalProvider(LocalStrings provides iosStrings(language), LocalLiveSettingsLanguage provides true) {
                        androidx.compose.runtime.key(generation) {
                            NavHost(nav, startDestination = NavRoutes.dailyReading()) { readingPlanNavGraph(nav, deps) }
                        }
                    }
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("1-Year through Bible")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("1-Year through Bible").performClick()
                waitUntil(timeoutMillis = 10_000) { host?.dayLoaded == 1 }
                onNodeWithText("Day 1").assertExists()
                runOnIdle { language = "fi" }
                waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("Vuodessa läpi Raamatun")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("1-Year through Bible").assertDoesNotExist()
                onNodeWithText(iosStrings("fi").done).assertIsNotEnabled()
                runOnIdle { language = "en" }
                waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("1-Year through Bible")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText(iosStrings("en").done).assertIsNotEnabled()
                onNodeWithContentDescription("Gen 1").assertIsOff().performClick()
                waitUntil(timeoutMillis = 10_000) { host?.dailyReadingController?.ui?.value?.readings?.firstOrNull()?.isRead == true }
                onNodeWithText(iosStrings("en").done).assertIsNotEnabled()
                val previousHost = host
                runOnIdle { generation++ }
                waitUntil(timeoutMillis = 10_000) { host !== previousHost && host?.dailyReadingController?.ui?.value?.readings?.firstOrNull()?.isRead == true }
                onNodeWithContentDescription("Gen 1").assertIsOn()
                onNodeWithContentDescription("Mat 1").performClick()
                waitUntil(timeoutMillis = 10_000) { host?.dailyReadingController?.ui?.value?.allRead == true }
                onNodeWithText(iosStrings("en").done).assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) { host?.dayLoaded == 2 }
                onNodeWithText("Day 2").assertExists()
                onNodeWithContentDescription("Gen 2").assertIsOff()
                val secondDayHost = host
                runOnIdle { generation++ }
                waitUntil(timeoutMillis = 10_000) { host !== secondDayHost && host?.dayLoaded == 2 }
                onNodeWithText("Day 2").assertExists()
                onNodeWithContentDescription("Gen 2").assertIsOff()
                assertEquals(2, host!!.dayLoaded)
            }
        } finally {
            window.destroy()
        }
        } finally {
            runBlocking {
                windowJob.cancelAndJoin()
                appJob.cancelAndJoin()
            }
            speakToRelease?.release()
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args ?: emptyArray()) } as T

    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }

    private class PlanDao : ReadingPlanDao {
        private val plans = mutableMapOf<String, ReadingPlan>()
        private val statuses = mutableMapOf<Pair<String, Int>, ReadingPlanStatus>()
        override suspend fun getPlan(planCode: String) = plans[planCode]?.copy()
        override suspend fun getStatus(planCode: String, planDay: Int) = statuses[planCode to planDay]?.copy()
        override suspend fun updatePlan(planCode: String, planStartDate: DbDate, planCurrentDay: Int, id: IdType) {
            plans[planCode] = ReadingPlan(planCode, planStartDate, planCurrentDay, id)
        }
        override suspend fun addPlanStatus(planCode: String, planDay: Int, readingStatus: String, id: IdType) {
            statuses[planCode to planDay] = ReadingPlanStatus(planCode, planDay, readingStatus, id)
        }
        override suspend fun deleteStatusesBeforeDay(planCode: String, planDay: Int) { statuses.keys.removeAll { it.first == planCode && it.second < planDay } }
        override suspend fun deleteStatusesForPlan(planCode: String) { statuses.keys.removeAll { it.first == planCode } }
        override suspend fun deletePlanInfo(planCode: String) { plans.remove(planCode) }
    }
}
