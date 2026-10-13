package net.bible.service.llm

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Exercises the real editor route and application-owned PromptServiceImpl writes without a provider. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PromptEditMutationHostTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owner = AppCoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private var host: org.robolectric.android.controller.ActivityController<NavHostComposeActivity>? = null
    private var oldScope: AppCoroutineScope? = null
    private var oldService: PromptService? = null
    private val initialFirstTime = net.bible.android.view.activity.base.firstTime
    private val writeReturned = CompletableDeferred<String>()

    private fun service(): PromptServiceImpl {
        val koin = GlobalContext.get()
        oldScope = koin.get()
        oldService = koin.get()
        loadKoinModules(module { single<AppCoroutineScope> { owner } })
        return PromptServiceImpl(koin.get<LlmStrings>()).also { real ->
            loadKoinModules(module { single<PromptService> { object : PromptService by real {
                    override suspend fun savePrompt(data: net.bible.sharedcore.ai.PromptEditData): String =
                        real.savePrompt(data).also { writeReturned.complete(it) }
                } } })
        }
    }

    @After fun cleanup() {
        var failure: Throwable? = null
        fun attempt(block: () -> Unit) {
            try { block() } catch (caught: Throwable) {
                if (failure == null) failure = caught else failure!!.addSuppressed(caught)
            }
        }
        attempt { host?.close() }
        val ownerJob = owner.coroutineContext[Job]!!
        ownerJob.cancel()
        attempt {
            runBlocking {
                withTimeout(5_000) {
                    while (!ownerJob.isCompleted) {
                        scheduler.advanceUntilIdle()
                        delay(10)
                    }
                }
            }
            assertTrue("test owner must quiesce before database reset", ownerJob.isCompleted)
        }
        attempt {
            oldScope?.let { scope -> oldService?.let { service ->
                loadKoinModules(module {
                    single<AppCoroutineScope> { scope }
                    single<PromptService> { service }
                })
            } }
        }
        net.bible.android.view.activity.base.firstTime = initialFirstTime
        // A failed drain must never detach a database still used by this owner.
        if (ownerJob.isCompleted) attempt { DatabaseResetter.resetDatabase() }
        failure?.let { throw it }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        compose.waitForIdle()
    }

    private fun awaitWriteReturned() {
        compose.waitUntil(timeoutMillis = 5_000) {
            scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            writeReturned.isCompleted
        }
        idle()
    }

    private fun awaitApplicationMutation() {
        // PromptServiceImpl.mutate owns a Deferred independently of its canceled UI waiter.
        runBlocking {
            withTimeout(5_000) {
                while (owner.coroutineContext[Job]!!.children.any { it is Deferred<*> && it.isActive }) {
                    scheduler.advanceUntilIdle()
                    delay(10)
                }
            }
        }
    }

    private fun rows() = runBlocking { DatabaseContainer.instance.aiSettingsDb.agentPromptDao().allPrompts() }

    @Test fun admittedRealServiceSaveCreatesOneRowEvenWhenWaiterIsCancelled() {
        val real = service()
        val editor = PromptEditController(real, null)
        editor.setName("One admitted row")
        editor.setTemplate("Original accepted template")
        val waiter = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = waiter.launch(start = CoroutineStart.UNDISPATCHED) { editor.save() }
        assertFalse(editor.canSave.value)
        runBlocking { assertNull(editor.save()); editor.delete(); assertNull(editor.copyToCustomize()) }
        editor.setTemplate("Rejected input")
        first.cancel()
        awaitApplicationMutation()
        val saved = rows().filter { it.name == "One admitted row" }
        assertEquals(1, saved.size)
        assertEquals("Original accepted template", saved.single().promptTemplate)
        assertEquals("Original accepted template", editor.state.value.template)
        waiter.cancel()
    }

    private fun route(failFirstSave: Boolean = false): Pair<NavHostComposeActivity, NavHostController> {
        service()
        val real = GlobalContext.get().get<PromptService>()
        if (failFirstSave) {
            var fail = true
            val failing = object : PromptService by real {
                override suspend fun savePrompt(data: net.bible.sharedcore.ai.PromptEditData): String {
                    if (fail) { fail = false; throw IllegalStateException("deliberate write failure") }
                    return real.savePrompt(data)
                }
            }
            loadKoinModules(module { single<PromptService> { failing } })
        }
        net.bible.test.resetComposeUiDispatcher()
        net.bible.android.view.activity.base.firstTime = false
        host = Robolectric.buildActivity(NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_CONNECTION_SETTINGS)).setup()
        idle()
        val activity = host!!.get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        nav.navigate(NavRoutes.promptEdit(template = "Original accepted template"))
        idle()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Host draft")
        idle()
        return activity to nav
    }

    @Test fun repeatedRealHostSaveNavigatesExactlyOnceAndDisablesInput() {
        val (activity, nav) = route()
        assertSame("Save must address the retained live host", activity, host!!.get())
        val hostedNav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(host!!.get())
        assertSame("listener and semantics must address the same NavController", nav, hostedNav)
        var departures = 0
        nav.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.route == NavRoutes.AI_CONNECTION_SETTINGS) departures++
        }
        val save = compose.onNodeWithContentDescription("OK")
        val callback = save.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        save.performClick()
        callback()
        idle()
        save.assertIsNotEnabled()
        compose.onNodeWithText("Host draft").assertIsNotEnabled()
        assertEquals(NavRoutes.PROMPT_EDIT_PATTERN, nav.currentDestination?.route)
        awaitWriteReturned()
        compose.waitUntil(timeoutMillis = 5_000) {
            scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            compose.waitForIdle()
            nav.currentDestination?.route == NavRoutes.AI_CONNECTION_SETTINGS
        }
        assertEquals(1, rows().count { it.name == "Host draft" })
        assertEquals(1, departures)
        assertEquals(NavRoutes.AI_CONNECTION_SETTINGS, nav.currentDestination?.route)
    }

    @Test fun failedRealHostSaveKeepsDraftWithoutNavigationAndCanRetry() {
        val (_, nav) = route(failFirstSave = true)
        val entry = nav.currentBackStackEntry
        compose.onNodeWithContentDescription("OK").performClick()
        idle()
        assertSame(entry, nav.currentBackStackEntry)
        assertEquals(0, rows().count { it.name == "Host draft" })
        compose.onNodeWithText("Host draft").assertIsEnabled()
        compose.onNodeWithContentDescription("OK").assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            compose.waitForIdle()
            nav.currentDestination?.route == NavRoutes.AI_CONNECTION_SETTINGS
        }
        assertEquals(1, rows().count { it.name == "Host draft" })
    }

    @Test fun disposedRealHostDoesNotNavigateAfterAdmittedPersistence() {
        val (_, nav) = route()
        compose.onNodeWithContentDescription("OK").performClick()
        idle()
        assertTrue(nav.popBackStack())
        idle()
        val current = nav.currentBackStackEntry
        awaitApplicationMutation()
        idle()
        assertEquals(1, rows().count { it.name == "Host draft" })
        assertSame(current, nav.currentBackStackEntry)
    }
}
