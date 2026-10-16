package net.bible.android.view.compose

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.ManifestResult
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.KoinFactorySnapshot
import net.bible.test.ReadingFixtureOwners
import net.bible.test.awaitHostCondition
import net.bible.test.awaitHostRoute
import net.bible.test.closeReadingHostsBeforeReset
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BM-13 for the Download screen: the live search query and an unsaved new custom-repository draft must
 * survive a real Activity recreation, while a fresh launch's own search argument still seeds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadRecreationStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val hosts = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var originalFirstTime = false
    private var originalStartup = false
    private var originalDoNotAsk = false
    private var serviceSnapshot: KoinFactorySnapshot? = null

    /** Validates every URL as a usable manifest, without touching the network. */
    private class ValidService : CustomRepositoryService {
        val upserts = mutableListOf<CustomRepositoryData>()
        override suspend fun list(): List<CustomRepositoryData> = emptyList()
        override suspend fun upsert(repo: CustomRepositoryData): Boolean { upserts += repo; return true }
        override suspend fun delete(repo: CustomRepositoryData) {}
        override suspend fun validateManifest(url: String, existingId: Long): ManifestResult =
            ManifestResult.Valid(CustomRepositoryData(name = "G3 repo", manifestUrl = url, packageDirectory = "pkg"))
    }

    @Before fun prepare() {
        originalFirstTime = firstTime
        originalStartup = comingFromStartupActivity
        firstTime = false
        comingFromStartupActivity = false
        originalDoNotAsk = CommonUtils.settings.getBoolean("download_do_not_ask", false)
        CommonUtils.settings.setBoolean("download_do_not_ask", true)
        resetComposeUiDispatcher()
    }

    @After fun cleanup() {
        closeReadingHostsBeforeReset(hosts, restoreBeforeReset = {
            firstTime = originalFirstTime
            comingFromStartupActivity = originalStartup
            CommonUtils.settings.setBoolean("download_do_not_ask", originalDoNotAsk)
            serviceSnapshot?.restore()
        })
    }

    private fun advance() { compose.mainClock.advanceTimeBy(32) }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun launch(route: String): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
    ).also { hosts += it }.create().start().resume().visible().get().also {
        awaitHostRoute(it, NavRoutes.DOWNLOAD_PATTERN, ::advance)
    }

    private fun controller(host: NavHostComposeActivity): DocumentSelectionController? =
        field(host, "downloadSession")?.let { field(it, "controller") as DocumentSelectionController }

    private fun awaitController(host: NavHostComposeActivity): DocumentSelectionController {
        awaitHostCondition("download controller built and seeded", ::advance) { controller(host) != null }
        // The seed effect runs in the first frame after the controller exists.
        compose.waitForIdle()
        return requireNotNull(controller(host))
    }

    /** Lets the arm's recording effect observe a controller edit, as the next frame would for a user. */
    private fun settleFrames() {
        repeat(5) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            advance()
            compose.waitForIdle()
        }
    }

    private fun recreate(activity: NavHostComposeActivity, route: String): NavHostComposeActivity {
        ReadingFixtureOwners.retain(activity)
        resetComposeUiDispatcher()
        return hosts.last().recreate().get().also {
            assertNotSame("must cross the actual Activity recreation boundary", activity, it)
            awaitHostRoute(it, route, ::advance)
        }
    }

    @Test fun liveDownloadQuerySurvivesRecreation() {
        val activity = launch(NavRoutes.download())
        val old = awaitController(activity)
        old.openSearch()
        old.setQuery("KJV")
        assertEquals("KJV", old.query.value)
        settleFrames()

        val recreated = recreate(activity, NavRoutes.DOWNLOAD_PATTERN)
        val restored = awaitController(recreated)
        assertNotSame(old, restored)
        awaitHostCondition("live query restored into the fresh controller", ::advance) { restored.query.value == "KJV" }
        assertTrue(restored.searchModeActive.value)
    }

    @Test fun freshIntentSearchArgumentStillSeeds() {
        val activity = launch(NavRoutes.download(search = "Genesis"))
        val old = awaitController(activity)
        assertEquals("Genesis", old.query.value)
        // The user edits the query after the route seeded it.
        old.setQuery("KJV")
        settleFrames()

        // Recreation (same arguments): the live query wins over the route's argument.
        val recreated = recreate(activity, NavRoutes.DOWNLOAD_PATTERN)
        val restored = awaitController(recreated)
        awaitHostCondition("recreation keeps the live query", ::advance) { restored.query.value == "KJV" }

        // A fresh launch with a different search argument must show that argument, not the old live query.
        val fresh = launch(NavRoutes.download(search = "Psalms"))
        val seeded = awaitController(fresh)
        awaitHostCondition("fresh intent argument seeds", ::advance) { seeded.query.value == "Psalms" }
        assertTrue(seeded.searchModeActive.value)
    }

    @Test fun newCustomRepositoryDraftSurvivesRecreationAndStaysDirty() {
        val snapshot = KoinFactorySnapshot.of<CustomRepositoryService>()
        serviceSnapshot = snapshot
        val service = ValidService()
        loadKoinModules(module { single<CustomRepositoryService> { service } })
        val activity = launch(NavRoutes.download())
        awaitController(activity)
        (field(activity, "navController") as NavHostController).navigate(NavRoutes.customRepositoryEditor(null))
        awaitHostRoute(activity, NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN, ::advance)
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("Repository URL")).performTextReplacement("https://g3.invalid/repo")
        awaitHostCondition("manifest validated, Save enabled", ::advance) {
            compose.onAllNodes(isEnabled() and androidx.compose.ui.test.hasContentDescription("OK"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val recreated = recreate(activity, NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN)
        awaitHostCondition("URL kept and Save enabled after recreation", ::advance) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("https://g3.invalid/repo")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(isEnabled() and androidx.compose.ui.test.hasContentDescription("OK"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        // Cancel (Back) while dirty asks to discard and writes no repository.
        recreated.onBackPressedDispatcher.onBackPressed()
        awaitHostCondition("dirty draft asks to discard", ::advance) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Discard unsaved changes?", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("nothing was saved", service.upserts.isEmpty())
    }
}
