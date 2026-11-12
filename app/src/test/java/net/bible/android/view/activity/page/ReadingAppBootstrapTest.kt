/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.page

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.notice.BetaNotice
import net.bible.sharedui.app.notice.BetaNoticePlatform
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Reading-host re-typing R7 (design spec §3.4 and §6 item 1): the app bootstrap is extracted ONCE
 * and called by both Activities, and `WindowRepository` is constructed in exactly one place inside
 * it — from exactly one call site per host, at most once per host.
 *
 * **Why this counts construction SITES rather than asserting that a repository exists.** §2.3's
 * failure mode is silent. `WindowControl.kt`'s `windowRepository` getter falls back to
 * `net.bible.test.testWindowRepository()` — an *uninitialised* repository on a scope
 * nobody owns — instead of throwing, so "a repository exists" is true even when the bootstrap never
 * ran, and equally true when TWO of them ran and the second quietly replaced the first. The visible
 * symptom is not a crash: it is an empty workspace on a screen that looks fully loaded.
 *
 * Measured, not assumed: when a second `WindowRepository(lifecycleScope)` is put back into
 * `MainBibleActivity.onCreate`, [theBootstrapPublishesAnInitialisedRepository] and
 * [theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes] BOTH still pass, because
 * `WindowRepository.activeWindow`'s getter is `if(!initialized) initialize()` and quietly loads
 * whatever object it is handed. Only the counts below go red. That is why they are the
 * load-bearing assertions.
 *
 * **Why a source scan and not a runtime counter.** The spec's wording ("the test must count
 * constructions") was written against a test-only counter incremented from `WindowRepository`'s
 * `init` behind a debug hook. That hook would be production code existing solely for a test. A
 * static count needs none, and sees a second site on a code path no unit test happens to execute.
 *
 * **Three counts, because each sees a failure the others cannot** (fix round 1, review items a/b):
 *  - [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] — one *construction* site
 *    outside `WindowControl`'s documented fallback, over EVERY non-test Kotlin source set (`main`
 *    and `debug`; `standard`/`discrete` carry no Kotlin), not just `src/main/java`.
 *  - [createWindowRepositoryIsCalledFromExactlyOneSitePerHost] — a construction-site count cannot
 *    see ONE site called from two places.
 *  - [callingItTwiceReturnsTheSameRepositoryRatherThanReplacingIt] — nor can it see one site
 *    EXECUTED twice; that one needs the runtime.
 *
 * `WindowRepository(` appears all over `src/test` (every window fixture builds one by hand); the
 * scans are deliberately confined to non-test source sets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingAppBootstrapTest {

    /**
     * Fix round (test-pollution): this class raises real [AppDialogController] requests
     * (`showStableNotice`/`showBetaNotice` below) but never had a teardown -- unlike every sibling
     * dialog test class, which calls `cancelAll()` in `@After`. On the happy path each notice test
     * answers its own request, so nothing leaked from THIS class's own tests; the real exposure is
     * indirect, via [net.bible.android.view.activity.nav.NavHostComposeActivity.bootstrapIfNeeded]'s
     * un-awaited `lifecycleScope.launch(Dispatchers.Main) { readingAppBootstrap.showFirstRunNotices() }`,
     * which several tests below trigger by building a real host through `navHostOnReading()`. That
     * launch is gated by [ReadingAppBootstrap]'s process-wide (companion, not per-instance)
     * `initialized` flag, so across the whole unit-test JVM it can fire from whichever test anywhere
     * in the suite happens to be first to build a reading host with the Main looper pumped -- raising
     * a stray [AppDialogRequest.Notice] this class never consumes. [AppDialogControllerResetRule]
     * closes both ends: a clean queue before this class's own tests run, regardless of what any
     * earlier class in the JVM left behind, and a clean queue after, so this class cannot hand the
     * problem to whichever class runs next.
     */
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    /**
     * Every non-test Kotlin source directory of `:app`, plus the `:sharedLogic` commonMain root. `src/main/java` alone would miss
     * `src/debug/java` (`DebugApp.kt`) and any future flavour source set — a construction there is
     * production code on at least one variant.
     */
    private val productionSrcRoots: List<File> =
        File("src").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "test" && it.name != "androidTest" }
            .map { File(it, "java") }
            .filter { it.isDirectory }
            .sortedBy { it.path } +
            // Domain code that moved to :sharedLogic commonMain (same FQNs) is production code too.
            net.bible.test.ProductionSources.roots.drop(1).filter { it.isDirectory }

    /** The only two places production code may construct a [net.bible.android.control.page.window.WindowRepository]. */
    private val allowedConstructionSites = setOf(
        "net/bible/android/control/page/window/WindowControl.kt",
        // ReadingAppBootstrap.createWindowRepository delegates to the shared ReadingAppInit (the one construction).
        "net/bible/service/startup/ReadingAppInit.kt",
    )

    private val navHostActivity =
        net.bible.test.ProductionSources.resolve("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")

    /** Source lines with comment-only lines dropped, so a mention in a KDoc is not a call site. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines()
            .filterNot { val t = it.trim(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }

    /** Every non-test file that CALLS the constructor (the declaration itself is not a call). */
    private fun constructionSites(): List<String> =
        productionSrcRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    codeLinesOf(file)
                        .filterNot { it.contains("class WindowRepository(") }
                        .any { Regex("""(?<![\w.])WindowRepository\(""").containsMatchIn(it) }
                }
                .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
        }.sorted()

    @Test
    fun theScansCanSeeTheirSubjects() {
        assertTrue(
            "no non-test Kotlin source root found — every scan below would pass vacuously",
            productionSrcRoots.isNotEmpty(),
        )
        assertTrue(
            "src/debug/java is a production source set on the debug variant and must be scanned " +
                "too; if it has genuinely gone away, say so here rather than silently narrowing",
            productionSrcRoots.any { it.path.replace(File.separatorChar, '/') == "src/debug/java" },
        )
        assertTrue("NavHostComposeActivity.kt not found", navHostActivity.isFile)
        assertTrue(
            "no WindowRepository construction found anywhere in production sources — the regex has " +
                "drifted off its subject and every assertion below is vacuous",
            constructionSites().isNotEmpty(),
        )
    }

    @Test
    fun windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode() {
        assertEquals(
            "production code may construct a WindowRepository only in ReadingAppBootstrap (the one " +
                "app bootstrap the reading host calls) and in WindowControl's documented lazy " +
                "fallback. A third site would not crash — WindowControl hands out an uninitialised " +
                "repository rather than throwing — it would give a silently empty workspace on a " +
                "screen that looks loaded",
            allowedConstructionSites.sorted(),
            constructionSites(),
        )
    }

    /**
     * Fix round 1, review item (b). One construction SITE called from two places constructs two
     * repositories, and [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] cannot
     * see it: the site count is still two.
     *
     * Slice 8 F3 dropped the `MainBibleActivity.onCreate` half with its subject (the class is deleted
     * in F4), and deleted `theActivityItselfNoLongerConstructsOne` for the same reason --
     * [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] pins the exact sites.
     */
    @Test
    fun createWindowRepositoryIsCalledFromExactlyOneSitePerHost() {
        fun calls(file: File) =
            codeLinesOf(file).count { it.contains("readingAppBootstrap.createWindowRepository(") }
        assertEquals(
            "NavHostComposeActivity must ask exactly once — from bootstrapIfNeeded(), which is the " +
                "one-shot both of its reading-route entry points share",
            1, calls(navHostActivity),
        )
    }

    /**
     * Fix round 1, review Important 3. `bootstrapIfNeeded()` must be reachable from BOTH
     * reading-route entry points: `onCreate` (the start route) and `onNewIntent` (a later
     * `EXTRA_ROUTE` navigating the live graph onto reading, on a host that started elsewhere and
     * therefore never bootstrapped — the composition would read `WindowControl`'s uninitialised
     * lazy fallback).
     *
     * Slice 8 E2 adds the third, in-graph entry point: gate (b), `welcomeAfterFlow()`, which turns a
     * WELCOME-started host into a reading host by navigating onto reading (spec §4).
     */
    @Test
    fun theReadingBootstrapIsReachedFromAllThreeReadingEntryPoints() {
        val lines = codeLinesOf(navHostActivity)
        val callLines = lines.withIndex().filter { (_, l) -> l.contains("bootstrapIfNeeded()") && !l.contains("fun ") }
        assertEquals(
            "exactly three call sites expected — onCreate's start-route gate, onNewIntent's and " +
                "welcomeAfterFlow's (gate (b)); found: " + callLines.map { it.value.trim() },
            3, callLines.size,
        )
        val decl = lines.indexOfFirst { it.contains("private fun bootstrapIfNeeded()") }
        assertTrue("bootstrapIfNeeded() is not declared in NavHostComposeActivity", decl >= 0)
        val onNewIntent = lines.indexOfFirst { it.contains("override fun onNewIntent(") }
        val onCreate = lines.indexOfFirst { it.contains("override fun onCreate(") }
        assertTrue("onNewIntent not found", onNewIntent >= 0)
        assertTrue("onCreate not found", onCreate >= 0)
        assertTrue(
            "one call must be in onNewIntent — a host that started on download or a settings route " +
                "reaches reading only through it",
            callLines.any { it.index > onNewIntent && it.index < onNewIntent + 40 },
        )
        // Anchor on the real `setContent` (the first one after onCreate), not a line-count window: a
        // window of N code lines broke every time onCreate gained a statement (fix batch 2 added
        // refreshSystemBars), while the property under test is only "before setContent".
        val setContentInOnCreate = lines.withIndex().first { (i, l) -> i > onCreate && l.contains("setContent {") }.index
        assertTrue(
            "one call must be in onCreate, before setContent",
            callLines.any { it.index > onCreate && it.index < setContentInOnCreate },
        )
        val welcomeAfterFlow = lines.indexOfFirst { it.contains("internal fun welcomeAfterFlow(") }
        assertTrue("welcomeAfterFlow not found", welcomeAfterFlow >= 0)
        assertTrue(
            "one call must be in welcomeAfterFlow -- gate (b) navigates a WELCOME-started host onto reading",
            callLines.any { it.index > welcomeAfterFlow && it.index < welcomeAfterFlow + 20 },
        )
    }

    /**
     * Fix round 1, review Important 3. Two entry points means the second one must be free. The
     * guard is the first statement, and the flag is set before anything else can throw.
     */
    @Test
    fun theReadingBootstrapIsAOneShot() {
        val body = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("private fun bootstrapIfNeeded()") }
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            "bootstrapIfNeeded()'s FIRST statement must be the early return — registerNetworkCallback " +
                "registers one callback object per bootstrap against the ONE unregister onDestroy " +
                "makes (T8a item 4), so a second run leaves a registration behind",
            "if (readingAppBootstrapped) return", body[0],
        )
        assertEquals(
            "…and the flag must be set immediately after it, before anything that can throw",
            "readingAppBootstrapped = true", body[1],
        )
    }

    /**
     * Fix round 1, review Important 1. `ReadingViewVisibility.setActivityVisible(this, true)` must
     * run BEFORE the deep-link dispatch, on this host as on classic. `openDeepLink` ->
     * `windowControl.showLink` -> `setKey(addHistoryItem = true)` calls `HistoryManager.recordIfCreated`
     * SYNCHRONOUSLY, and with the flag false `createHistoryItem` records a wrong `IntentHistoryItem`
     * carrying the deep-link intent instead of the verse. Deferring the flag to the reading
     * destination's `DisposableEffect` cannot work: an effect inside the graph runs after
     * `setContent`, i.e. after this has already fired.
     *
     * **R7b re-pointed this guard and added the presence half.** The setter took a host parameter
     * (`setActivityVisible(this, true)`) when the flag became "registered by the FOREGROUND host",
     * so the old literal this scanned no longer occurs anywhere and the guard would have passed
     * vacuously — the failure mode a scan-based guard has and a compiler does not. The same
     * ordering argument applies verbatim to `ReadingHostPresence.setForeground(this)`: a
     * registration by a host that is not in front is not visible, so a presence declared after the
     * deep link is the same wrong `IntentHistoryItem`.
     *
     * Task 15 routes the producer through the synchronous `applyBootstrapUiIfForeground` helper.
     * Pin the setters before that call AND the actual guarded, one-shot producer inside the helper;
     * comparing whole-file positions would mistake declaration order for execution order. Paused
     * dispatch waits for resume; the real old-verse `KeyHistoryItem` tests in
     * `MyDocumentBookManagerTest` and `ReadingHostBridgeRearmTest` cover that lifecycle path.
     */
    @Test
    fun theReadingBootstrapSetsTheVisibilityFlagBeforeDispatchingADeepLink() {
        val lines = codeLinesOf(navHostActivity)
        val end = lines.indexOfFirst { it.trim().startsWith("override val hostContext:") }
        assertTrue("the bootstrap body boundary must survive the comment filter", end >= 0)
        val body = lines.take(end)
            .dropWhile { !it.contains("private fun bootstrapIfNeeded()") }
        val visible = body.indexOfFirst { it.contains("ReadingViewVisibility.setActivityVisible(this, true)") }
        val presence = body.indexOfFirst { it.contains("ReadingHostPresence.setForeground(this)") }
        val dispatch = body.indexOfFirst { it.trim() == "applyBootstrapUiIfForeground()" }
        val helper = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("private fun applyBootstrapUiIfForeground()") }
            .takeWhile { !it.contains("private fun bootstrapIfNeeded()") }
        val deepLink = helper.indexOfFirst { it.contains("readingAppBootstrap.openDeepLink(") }
        val guard = helper.indexOfFirst {
            it.trim() == "if (!readingAppBootstrapped || paused || !ReadingHostPresence.isForeground(this)) return"
        }
        val consume = helper.indexOfFirst { it.trim() == "intent.removeExtra(\"openLink\")" }
        assertTrue(
            "bootstrapIfNeeded() must call ReadingViewVisibility.setActivityVisible(this, true) — " +
                "without it the deep link below records a wrong IntentHistoryItem, silently",
            visible >= 0,
        )
        assertTrue(
            "…and ReadingHostPresence.setForeground(this), without which that registration is not " +
                "visible at all (R7b)",
            presence >= 0,
        )
        assertTrue("bootstrapIfNeeded() must call the guarded synchronous UI dispatch", dispatch >= 0)
        assertTrue(
            "the visibility flag must be set BEFORE the guarded deep-link dispatch, not after: the " +
                "history item the dispatch records is handled synchronously",
            visible < dispatch,
        )
        assertTrue("…and so must the host's presence, for the same reason", presence < dispatch)
        assertTrue("the guarded helper must contain the actual deep-link producer", deepLink >= 0)
        val openLink = helper.indexOfFirst { it.trim() == "if (intent.hasExtra(\"openLink\")) {" }
        assertTrue("the actual deep link must be dispatched from the openLink branch", openLink >= 0 && openLink < deepLink)
        assertTrue("the helper must reject paused, nonforeground or unbootstrapped UI before dispatch", guard >= 0 && guard < deepLink)
        assertTrue("the helper must consume the pending link after the guard and before dispatch", consume > guard && consume < deepLink)
    }

    /**
     * **R7b: the bootstrap bridge is RETIRED again, so this host stays balanced.**
     *
     * `bootstrapIfNeeded()` is one-shot and declares `setActivityVisible(this, true)` above; that
     * input is the bridge over the window before the graph composes, and nothing in the host would
     * ever clear it on its own. Left set, this host reports a reading view on screen for the rest of
     * its life — on every OTHER destination it shows — so `HistoryManager` would record a
     * `KeyHistoryItem` on a Download screen and `goBack()` would never finish anything. The
     * destination's `enter(host)` retires it when the graph composes; `onPause` is the other end,
     * for a host backgrounded before that ever happens.
     *
     * A scan when it was written, because neither end was then reachable from a unit test: the
     * READING route's content slot still `error(...)`ed, so no test could build this host with the
     * bridge set. **R8 made that slot real and the rationale stale** (T8a fix round 1, review Minor
     * 3): `ReadingHostBridgeRearmTest` builds exactly that host and drives the bridge through real
     * lifecycle callbacks — `aHostThatWasNeverMadeVisibleHasNotComposedItsReadingView` asserts it is
     * armed on a created, resumed, uncomposed host, and
     * `aHostThatResumesBeforeItsDestinationComposesStillReportsTheReadingView` asserts the `onPause`
     * retirement this scan names. The scan is kept because it pins the retirement to that specific
     * LINE in `onPause`, which a behavioural test cannot distinguish from any other way of clearing
     * the flag, but it is no longer the only thing that can see this. Mutation: delete the
     * `setActivityVisible(this, false)` line from the host's `onPause` and this fails; the
     * `:sharedCore` half (a composed destination retiring the bridge) is
     * `ReadingViewVisibilityTest.aComposedDestinationRetiresItsOwnHostsBootstrapBridge`.
     */
    @Test
    fun theBootstrapBridgeIsRetiredWhenTheHostPauses() {
        val onPause = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("override fun onPause()") }
            .takeWhile { !it.contains("override fun onResume()") }
        assertTrue(
            "NavHostComposeActivity.onPause() must retire the bootstrap bridge with " +
                "ReadingViewVisibility.setActivityVisible(this, false) — bootstrapIfNeeded() sets " +
                "it and nothing else ever clears it",
            onPause.any { it.contains("ReadingViewVisibility.setActivityVisible(this, false)") },
        )
        assertTrue(
            "…and retract its presence with clearForeground(this), never setForeground(null): a " +
                "stale pause must not clear the host that came to the front after it",
            onPause.any { it.contains("ReadingHostPresence.clearForeground(this)") },
        )
        assertTrue(
            "onPause must not clear the foreground unconditionally",
            onPause.none { it.contains("ReadingHostPresence.setForeground(null)") },
        )
    }

    @Test
    fun theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes() {
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            assertSame(
                "WindowControl must publish the object the bootstrap created, or every collaborator " +
                    "that reads windowControl.windowRepository is looking at a different workspace",
                activity.readingAppBootstrap.windowRepository,
                CommonUtils.windowControl.windowRepository,
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun theBootstrapPublishesAnInitialisedRepository() {
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            assertTrue(
                "the published repository must have been through initialize() — WindowControl's " +
                    "lazy fallback hands out one that has NOT, and it looks identical until the " +
                    "workspace turns out to be empty",
                CommonUtils.windowControl.windowRepository.initialized,
            )
        } finally {
            controller.close()
        }
    }

    /**
     * Fix round 1, review item (b): the failure shape no static count can see — one call site
     * EXECUTED twice. A second `createWindowRepository()` must hand back the repository this host
     * already has rather than replacing it, because a replacement does not crash: it strands every
     * collaborator holding the first one on a workspace `WindowControl` no longer publishes.
     */
    @Test
    fun callingItTwiceReturnsTheSameRepositoryRatherThanReplacingIt() {
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            val first = activity.readingAppBootstrap.windowRepository
            val second = activity.readingAppBootstrap.createWindowRepository()
            assertSame("a second createWindowRepository() must not build another one", first, second)
            assertSame(
                "…and it must not have re-pointed WindowControl at a new object either",
                first,
                CommonUtils.windowControl.windowRepository,
            )
        } finally {
            controller.close()
        }
    }

    // ——— T8a item 4: the network callback's other end ———————————————————————————————————————————

    private val connectivityShadow get() = shadowOf(
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    )

    private fun navHostOnReading() = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    )

    /**
     * **T8a item 4.** R7 extracted `registerNetworkCallback` and recorded, correctly, that nothing
     * in the repository ever unregistered it — a leak it preserved rather than fixed inside an
     * extraction commit. T8b makes that unacceptable: the boot handoff keeps
     * `FLAG_ACTIVITY_MULTIPLE_TASK` for `ACTION_VIEW` deep links, so every deep link spawns another
     * reading host, another bootstrap and another registration nothing removes.
     *
     * Counted, not asserted-to-exist, for this class's own reason: "an unregister method exists" is
     * true of a method nothing calls, and a leak has no symptom a functional test can see. The count
     * is a DELTA because `TestBibleApplication` is free to register callbacks of its own.
     *
     * Mutation: delete `readingAppBootstrap.unregisterNetworkCallback()` from
     * `NavHostComposeActivity.onDestroy` and the third assertion fails.
     */
    @Test
    fun theNetworkCallbackIsUnregisteredWhenItsHostIsDestroyed() {
        val shadow = connectivityShadow
        val before = shadow.networkCallbacks.size
        val controller = navHostOnReading()
        val activity = controller.create().get()
        assertEquals(
            "bootstrapIfNeeded must have registered exactly one default-network callback",
            before + 1, shadow.networkCallbacks.size,
        )
        assertTrue(
            "…and it must be THIS bootstrap's own callback object",
            activity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
        )

        controller.close()

        assertFalse(
            "destroying the host must unregister the callback it registered — with " +
                "FLAG_ACTIVITY_MULTIPLE_TASK every deep link makes another host, another bootstrap " +
                "and another registration, and nothing else ever removes one",
            activity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
        )
        assertEquals(
            "…and the registration count must be back where it started",
            before, shadow.networkCallbacks.size,
        )
    }

    /**
     * The hazard an unregister has to disprove: that it could tear down a callback another live host
     * still needs.
     *
     * It cannot, and this measures why. `ReadingAppBootstrap` is constructed once per host (each
     * Activity holds its own `readingAppBootstrap`), `networkCallback` is an instance property of
     * it, and `unregisterNetworkCallback(cb)` removes the one object it is given — so two live hosts
     * hold two distinct callbacks and destroying either leaves the other's registration untouched.
     * The `ConnectivityManager` is the process-wide system service in both cases (measured: the same
     * instance for the application context and for both Activities), which is exactly why the
     * callback IDENTITY is what matters.
     *
     * Two NAV hosts, because that is T8b's own scenario — `FLAG_ACTIVITY_MULTIPLE_TASK` on an
     * `ACTION_VIEW` deep link — and because it needs no `firstTime` pinning.
     */
    @Test
    fun destroyingOneHostLeavesTheOtherLiveHostsCallbackRegistered() {
        val shadow = connectivityShadow
        val before = shadow.networkCallbacks.size
        val first = navHostOnReading()
        val second = navHostOnReading()
        try {
            val firstActivity = first.create().get()
            val secondActivity = second.create().get()
            assertEquals(
                "two live reading hosts register two callbacks — one per bootstrap",
                before + 2, shadow.networkCallbacks.size,
            )

            first.close()

            assertFalse(
                "the destroyed host's callback is gone",
                firstActivity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
            )
            assertTrue(
                "…and the surviving host's is still registered: the unregister names ONE callback " +
                    "object, so it cannot reach another bootstrap's",
                secondActivity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
            )
            assertEquals("…leaving exactly one", before + 1, shadow.networkCallbacks.size)
        } finally {
            second.close()
        }
    }

    // ——— F59 fix round 1: setSoftKeyboardMode()'s per-host threshold —————————————————————————————

    /**
     * The nav host's threshold, pinned directly, plus the source-level check that
     * `setSoftKeyboardMode()` reads `host.appOwnsImeInsetFromSdk` (the generic bound
     * `ReadingAppBootstrap<T> where T : ActivityBase, T : ReadingHostActivity` exists for), not a literal
     * `Build.VERSION_CODES` constant of its own -- there is no `ReadingAppBootstrap` field to read
     * directly (the value comes from whichever host is plugged in as `T`).
     *
     * Slice 8 F2: was `theTwoHostsAnswerDifferentThresholdsAndTheBootstrapReadsTheHostsOwn`. Its classic
     * half (`MainBibleActivity` answered `VANILLA_ICE_CREAM`) and the classic end-to-end sdk-30 test it
     * backed (`theClassicHostKeepsAdjustResizeOnApi30`) measured the deleted class's own constant and
     * went with it (spec §5.3); the nav host's `R` and the per-host read stay live.
     */
    @Test
    fun theNavHostAnswersRAndTheBootstrapReadsTheHostsOwn() {
        firstTime = false
        val navController = navHostOnReading()
        try {
            val nav = navController.create().get()

            assertEquals(
                "the nav host is the one Task 6/7 measured (spec §3.1.1) -- R (30)",
                Build.VERSION_CODES.R,
                nav.appOwnsImeInsetFromSdk,
            )

            val source = File(
                "src/main/java/net/bible/android/view/activity/page/ReadingAppBootstrap.kt"
            ).readText()
            assertTrue(
                "setSoftKeyboardMode() must gate on the per-host member, not a constant of its own " +
                    "(a literal Build.VERSION_CODES constant here would silently tie every host to one " +
                    "threshold again)",
                Regex("""setSoftKeyboardMode\(\)\s*\{[\s\S]*?host\.appOwnsImeInsetFromSdk""")
                    .containsMatchIn(source),
            )
        } finally {
            navController.close()
        }
    }

    // ——— Task 30 Step 4: first-run pinning help deleted (unreachable, plan correction 13) ————————

    /**
     * `showFirstTimeHelp`'s dialog was unreachable in practice (`||` short-circuits on
     * `CommonUtils.isFirstInstall || CommonUtils.mainVersionFloat >= 3.4`, already true at this
     * app's shipped `versionName`) -- maintainer decision 2026-09-25 (plan correction 13) deleted
     * `askPinningHelp()`, its dialog, `showFirstTimeHelp()` itself and the `pinning-help-shown` pref
     * read/write (grepped repo-wide: nothing else reads that key). The same
     * `help_window_pinning_title`/`help_window_pinning_text` strings stay reachable via Help & tips
     * (`CommonUtils.showHelp`'s `HelpItem` list, `CommonUtils.kt:1045`) -- `CommonUtilsDialogsTest`
     * covers `showHelp` generically, not this specific entry.
     */
    private val appDialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    // ——— Task 28: showStableNotice/showBetaNotice ported to AppDialogRequest.Notice —————————————

    /**
     * `CommonUtils.isBeta` is `true` under every Robolectric unit test (measured:
     * `application.applicationInfo` carries `FLAG_DEBUGGABLE` on the `standardGoogleplayDebug` test
     * variant, and `isBeta` is `... || isDebugMode`), and also because `version.properties` gives 6.0
     * builds a `-beta` `versionNameSuffix` -- so [showBetaNotice]'s guard passes and
     * [showStableNotice]'s does not, in every test below that does not call this first.
     *
     * This forces a non-beta build whatever the file's suffix: it clears the debuggable flag AND
     * strips a `-beta`/`-alpha` suffix from the installed package's `versionName` (the package name
     * does not end in `.next` in this variant, the third disjunct). Both are restored in `finally`
     * so they never bleed into another test.
     */
    private suspend fun withNonBetaBuild(block: suspend () -> Unit) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val info = app.applicationInfo
        val originalFlags = info.flags
        val packageInfo = org.robolectric.Shadows.shadowOf(app.packageManager)
            .getInternalMutablePackageInfo(app.packageName)
        val originalVersionName = packageInfo.versionName
        info.flags = originalFlags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        packageInfo.versionName = originalVersionName?.removeSuffix("-beta")?.removeSuffix("-alpha")
        try {
            assertFalse("test precondition: isBeta must be false here", CommonUtils.isBeta)
            block()
        } finally {
            info.flags = originalFlags
            packageInfo.versionName = originalVersionName
        }
    }

    @Test
    fun showStableNoticeSkipsWithNoDialogWhenBeta() = runTest(timeout = 30.seconds) {
        CommonUtils.settings.removeString("stable-notice-beta-ran")
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            assertFalse(activity.readingAppBootstrap.showStableNotice())
            assertNull(appDialogs.pending.value)
            assertEquals("", CommonUtils.settings.getString("stable-notice-beta-ran", ""))
        } finally {
            controller.close()
            CommonUtils.settings.removeString("stable-notice-beta-ran")
        }
    }

    @Test
    fun showStableNoticeSkipsWithNoDialogWhenAlreadyDisplayedForThisVersion() = runTest(timeout = 30.seconds) {
        withNonBetaBuild {
            CommonUtils.settings.setString("stable-notice-displayed", CommonUtils.mainVersion)
            val controller = navHostOnReading()
            try {
                val activity = controller.create().get()
                assertFalse(activity.readingAppBootstrap.showStableNotice())
                assertNull(appDialogs.pending.value)
            } finally {
                controller.close()
                CommonUtils.settings.removeString("stable-notice-displayed")
            }
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.runStableNotice(
        discretePref: Boolean = false,
        block: suspend (NavHostComposeActivity, kotlinx.coroutines.Deferred<Boolean>) -> Unit,
    ) {
        withNonBetaBuild {
            CommonUtils.settings.removeString("stable-notice-displayed")
            CommonUtils.settings.removeString("stable-notice-beta-ran")
            CommonUtils.settings.removeString("new-sync-targets-notice-displayed")
            if (discretePref) CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).commit()
            val controller = navHostOnReading()
            try {
                val activity = controller.create().get()
                val done = async { activity.readingAppBootstrap.showStableNotice() }
                advanceUntilIdle()
                block(activity, done)
            } finally {
                controller.close()
                CommonUtils.realSharedPreferences.edit().remove("discrete_mode").commit()
                CommonUtils.settings.removeString("stable-notice-displayed")
                CommonUtils.settings.removeString("new-sync-targets-notice-displayed")
            }
        }
    }

    @Test
    fun welcomeSilentlyMarksStableNoticeBeforeReading() = runTest(timeout = 30.seconds) {
        withNonBetaBuild {
            CommonUtils.settings.removeString("stable-notice-displayed")
            val controller = navHostOnReading()
            try {
                val activity = controller.create().get()
                val databaseHost = net.bible.service.db.DatabaseContainer.host
                val productionHost = object : net.bible.service.db.DatabaseHost by databaseHost {
                    override val isRunningTests = false
                }
                try {
                    net.bible.service.db.DatabaseContainer.installHost(productionHost)
                    net.bible.service.db.DatabaseContainer.ready = false
                    net.bible.android.view.activity.WelcomeFlow(activity).controller
                    assertTrue("Welcome must open settings before marking the release notice", net.bible.service.db.DatabaseContainer.ready)
                } finally {
                    net.bible.service.db.DatabaseContainer.installHost(databaseHost)
                    net.bible.service.db.DatabaseContainer.ready = true
                }
                assertEquals(CommonUtils.mainVersion, CommonUtils.settings.getString("stable-notice-displayed", ""))
                assertFalse(activity.readingAppBootstrap.showStableNotice())
                assertNull(appDialogs.pending.value)
            } finally {
                controller.close()
                CommonUtils.settings.removeString("stable-notice-displayed")
            }
        }
    }

    @Test
    fun betaWelcomeDoesNotStoreStableOrBetaMarker() = runTest(timeout = 30.seconds) {
        CommonUtils.settings.removeString("stable-notice-displayed")
        CommonUtils.settings.removeString("stable-notice-beta-ran")
        val controller = navHostOnReading()
        try {
            controller.create().get().welcomeFlow.controller
            assertEquals("", CommonUtils.settings.getString("stable-notice-displayed", ""))
            assertEquals("", CommonUtils.settings.getString("stable-notice-beta-ran", ""))
        } finally {
            controller.close()
            CommonUtils.settings.removeString("stable-notice-displayed")
            CommonUtils.settings.removeString("stable-notice-beta-ran")
        }
    }

    @Test
    fun discreteWelcomeDoesNotStoreNoticeVersion() = runTest(timeout = 30.seconds) {
        withNonBetaBuild {
            CommonUtils.settings.removeString("stable-notice-displayed")
            CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).commit()
            val controller = navHostOnReading()
            try {
                controller.create().get().welcomeFlow.controller
                assertEquals("", CommonUtils.settings.getString("stable-notice-displayed", ""))
            } finally {
                controller.close()
                CommonUtils.realSharedPreferences.edit().remove("discrete_mode").commit()
                CommonUtils.settings.removeString("stable-notice-displayed")
            }
        }
    }

    @Test
    fun oldBetaMarkerDoesNotOverrideWelcomeDismissal() = runTest(timeout = 30.seconds) {
        withNonBetaBuild {
            CommonUtils.settings.setString("stable-notice-beta-ran", CommonUtils.mainVersion)
            CommonUtils.settings.removeString("stable-notice-displayed")
            val controller = navHostOnReading()
            try {
                val activity = controller.create().get()
                activity.welcomeFlow.controller
                assertFalse(activity.readingAppBootstrap.showStableNotice())
                assertNull(appDialogs.pending.value)
                assertEquals(CommonUtils.mainVersion, CommonUtils.settings.getString("stable-notice-displayed", ""))
            } finally {
                controller.close()
                CommonUtils.settings.removeString("stable-notice-displayed")
                CommonUtils.settings.removeString("stable-notice-beta-ran")
            }
        }
    }

    @Test
    fun stableNoticeShownWithoutInstallOrSyncMarkers() = runTest(timeout = 30.seconds) {
        runStableNotice { _, done ->
            val request = appDialogs.pending.value!!.request as AppDialogRequest.ReleaseNotice
            assertEquals(CommonUtils.mainVersion, request.version)
            assertTrue(request.sponsorAllowed)
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.ReleaseNoticeAnswer(null, dontShowAgain = true))
            advanceUntilIdle()
            assertTrue("don't show again returns true", done.await())
            assertEquals(CommonUtils.mainVersion, CommonUtils.settings.getString("stable-notice-displayed", ""))
        }
    }

    @Test
    fun stableNoticeCloseWithoutCheckboxDoesNotPersist() = runTest(timeout = 30.seconds) {
        runStableNotice { activity, done ->
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.ReleaseNoticeAnswer(null, dontShowAgain = false))
            advanceUntilIdle()
            assertFalse(done.await())
            assertEquals("", CommonUtils.settings.getString("stable-notice-displayed", ""))
            val nextLaunch = async { activity.readingAppBootstrap.showStableNotice() }
            advanceUntilIdle()
            assertTrue(appDialogs.pending.value!!.request is AppDialogRequest.ReleaseNotice)
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.Cancel)
            advanceUntilIdle()
            assertFalse(nextLaunch.await())
        }
    }

    @Test
    fun stableNoticeNotShownInDiscrete() = runTest(timeout = 30.seconds) {
        runStableNotice(discretePref = true) { _, done ->
            assertTrue(done.isCompleted)
            assertFalse(done.await())
            assertNull(appDialogs.pending.value)
        }
    }

    @Test
    fun shareActionOpensRecommendDialog() = runTest(timeout = 30.seconds) {
        runStableNotice { _, done ->
            appDialogs.respond(
                appDialogs.pending.value!!.id,
                AppDialogResult.ReleaseNoticeAnswer(net.bible.sharedcore.ui.dialog.ReleaseNoticeAction.SHARE, dontShowAgain = false),
            )
            advanceUntilIdle()
            val dialog = appDialogs.pending.value!!.request as AppDialogRequest.Notice
            assertTrue(dialog.blocks.contains(AppDialogRequest.NoticeBlock.QrCode))
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.Cancel)
            advanceUntilIdle()
            done.await()
        }
    }

    @Test
    fun showBetaNoticeSkipsWithNoDialogWhenNotBeta() = runTest(timeout = 30.seconds) {
        withNonBetaBuild {
            val controller = navHostOnReading()
            try {
                val activity = controller.create().get()
                assertFalse(activity.readingAppBootstrap.showBetaNotice())
                assertNull(appDialogs.pending.value)
            } finally {
                controller.close()
            }
        }
    }

    /** Fix batch 6 A6 (surface 4): the English-only, github.com/AndBible beta notice is skipped when discrete. */
    @Test
    fun showBetaNoticeIsSkippedWhenDiscrete() = runTest(timeout = 30.seconds) {
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        val betaRanKey = "stable-notice-beta-ran"
        CommonUtils.settings.removeString(betaRanKey)
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).commit()
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            assertFalse(activity.readingAppBootstrap.showBetaNotice())
            assertFalse(activity.readingAppBootstrap.showStableNotice())
            assertNull(appDialogs.pending.value)
            assertEquals("", CommonUtils.settings.getString(betaRanKey, ""))
        } finally {
            controller.close()
            CommonUtils.realSharedPreferences.edit().remove("discrete_mode").commit()
            CommonUtils.settings.removeString(betaRanKey)
        }
    }

    @Test
    fun showBetaNoticeSkipsWithNoDialogWhenAlreadyAnnouncedAtVersion5() = runTest(timeout = 30.seconds) {
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        CommonUtils.settings.setInt("beta-notice-displayed2", 5)
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            assertFalse(activity.readingAppBootstrap.showBetaNotice())
            assertNull(appDialogs.pending.value)
        } finally {
            controller.close()
            CommonUtils.settings.setInt("beta-notice-displayed2", null)
        }
    }

    @Test
    fun showBetaNoticeShowsAgainForTestersWhoDismissedVersion4() = runTest(timeout = 30.seconds) {
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        CommonUtils.settings.setInt("beta-notice-displayed2", 4)
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            val done = async { activity.readingAppBootstrap.showBetaNotice() }
            advanceUntilIdle()
            assertNotNull(appDialogs.pending.value)
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.Neutral)
            advanceUntilIdle()
            assertFalse(done.await())
        } finally {
            controller.close()
            CommonUtils.settings.setInt("beta-notice-displayed2", null)
        }
    }

    @Test
    fun showBetaNoticeRaisesASingleHtmlBlockAndPersistsOnDismissUntilUpdate() = runTest(timeout = 30.seconds) {
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        CommonUtils.settings.setInt("beta-notice-displayed2", null)
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            val done = async { activity.readingAppBootstrap.showBetaNotice() }
            advanceUntilIdle()

            val request = appDialogs.pending.value!!.request as AppDialogRequest.Notice
            assertEquals(activity.getString(R.string.beta_notice_title), request.title)
            assertTrue(request.showTitleLogo)
            assertEquals(1, request.blocks.size)
            val body = (request.blocks[0] as AppDialogRequest.NoticeBlock.Html).html
            assertFalse("no video link in the 6.0 beta notice", body.contains("youtu"))
            assertTrue("carries the 6.0 text", body.contains("Welcome to the AndBible 6.0 beta!"))
            assertTrue("carries the issues link", body.contains("https://github.com/AndBible/and-bible/issues/new/choose"))
            assertTrue("carries the sponsor link", body.contains(buyDevelopmentLink))
            assertTrue("Android wording", body.contains(BetaNotice.specialNoticeHtml(BetaNoticePlatform.ANDROID, net.bible.sharedui.strings.AndroidStrings(activity))))
            assertTrue("carries the installation guide", body.contains(BetaNotice.INSTALLATION_GUIDE_LINK))
            assertTrue("carries the version", body.contains(CommonUtils.applicationVersionName))
            assertEquals(activity.getString(R.string.beta_notice_dismiss_until_update), request.confirmText)
            assertEquals(activity.getString(R.string.close), request.neutralText)
            assertNull(request.dismissText)

            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.Ok)
            advanceUntilIdle()

            assertTrue(done.isCompleted)
            assertTrue(done.await())
            assertEquals(5, CommonUtils.settings.getInt("beta-notice-displayed2", 0))
        } finally {
            controller.close()
            CommonUtils.settings.setInt("beta-notice-displayed2", null)
        }
    }

    @Test
    fun showBetaNoticeNeutralDismissReturnsFalseAndDoesNotPersist() = runTest(timeout = 30.seconds) {
        assertTrue("test precondition: isBeta must be true here", CommonUtils.isBeta)
        CommonUtils.settings.setInt("beta-notice-displayed2", null)
        val controller = navHostOnReading()
        try {
            val activity = controller.create().get()
            val done = async { activity.readingAppBootstrap.showBetaNotice() }
            advanceUntilIdle()
            appDialogs.respond(appDialogs.pending.value!!.id, AppDialogResult.Neutral)
            advanceUntilIdle()

            assertTrue(done.isCompleted)
            assertFalse(done.await())
            assertEquals(0, CommonUtils.settings.getInt("beta-notice-displayed2", 0))
        } finally {
            controller.close()
            CommonUtils.settings.setInt("beta-notice-displayed2", null)
        }
    }
}
