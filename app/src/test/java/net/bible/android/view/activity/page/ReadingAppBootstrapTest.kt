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
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
 * `WindowRepository(CoroutineScope(Dispatchers.Main))` — an *uninitialised* repository on a scope
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
     * Every non-test Kotlin source directory of `:app`. `src/main/java` alone would miss
     * `src/debug/java` (`DebugApp.kt`) and any future flavour source set — a construction there is
     * production code on at least one variant.
     */
    private val productionSrcRoots: List<File> =
        File("src").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "test" && it.name != "androidTest" }
            .map { File(it, "java") }
            .filter { it.isDirectory }
            .sortedBy { it.path }

    /** The only two places production code may construct a [net.bible.android.control.page.window.WindowRepository]. */
    private val allowedConstructionSites = setOf(
        "net/bible/android/control/page/window/WindowControl.kt",
        "net/bible/android/view/activity/page/ReadingAppBootstrap.kt",
    )

    private val mainBibleActivity =
        File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt")
    private val navHostActivity =
        File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")

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
        assertTrue("MainBibleActivity.kt not found", mainBibleActivity.isFile)
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
                "app bootstrap both Activities call) and in WindowControl's documented lazy " +
                "fallback. A third site would not crash — WindowControl hands out an uninitialised " +
                "repository rather than throwing — it would give a silently empty workspace on a " +
                "screen that looks loaded",
            allowedConstructionSites.sorted(),
            constructionSites(),
        )
    }

    @Test
    fun theActivityItselfNoLongerConstructsOne() {
        assertTrue(
            "MainBibleActivity must get its repository from ReadingAppBootstrap, not build a " +
                "second one of its own",
            "net/bible/android/view/activity/page/MainBibleActivity.kt" !in constructionSites(),
        )
    }

    /**
     * Fix round 1, review item (b). One construction SITE called from two places constructs two
     * repositories, and [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] cannot
     * see it: the site count is still two.
     */
    @Test
    fun createWindowRepositoryIsCalledFromExactlyOneSitePerHost() {
        fun calls(file: File) =
            codeLinesOf(file).count { it.contains("readingAppBootstrap.createWindowRepository(") }
        assertEquals(
            "MainBibleActivity.onCreate must ask the bootstrap for its repository exactly once",
            1, calls(mainBibleActivity),
        )
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
        assertTrue(
            "one call must be in onCreate, before setContent",
            callLines.any { it.index > onCreate && it.index < onCreate + 20 },
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
     * `windowControl.showLink` -> `setKey(addHistoryItem = true)` posts `AddHistoryItem`
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
     */
    @Test
    fun theReadingBootstrapSetsTheVisibilityFlagBeforeDispatchingADeepLink() {
        val body = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("private fun bootstrapIfNeeded()") }
            .takeWhile { !it.trim().startsWith("/** [ReadingHostActivity.hostContext]") }
        val visible = body.indexOfFirst { it.contains("ReadingViewVisibility.setActivityVisible(this, true)") }
        val presence = body.indexOfFirst { it.contains("ReadingHostPresence.setForeground(this)") }
        val deepLink = body.indexOfFirst { it.contains("readingAppBootstrap.openDeepLink(") }
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
        assertTrue("bootstrapIfNeeded() must dispatch the openLink deep link", deepLink >= 0)
        assertTrue(
            "the visibility flag must be set BEFORE the deep-link dispatch, not after: the " +
                "AddHistoryItem the dispatch posts is handled synchronously",
            visible < deepLink,
        )
        assertTrue("…and so must the host's presence, for the same reason", presence < deepLink)
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
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertSame(
                "WindowControl must publish the object the bootstrap created, or every collaborator " +
                    "that reads windowControl.windowRepository is looking at a different workspace",
                activity.readingAppBootstrap.windowRepository,
                activity.windowControl.windowRepository,
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun theBootstrapPublishesAnInitialisedRepository() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertTrue(
                "the published repository must have been through initialize() — WindowControl's " +
                    "lazy fallback hands out one that has NOT, and it looks identical until the " +
                    "workspace turns out to be empty",
                activity.windowControl.windowRepository.initialized,
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
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            val first = activity.readingAppBootstrap.windowRepository
            val second = activity.readingAppBootstrap.createWindowRepository()
            assertSame("a second createWindowRepository() must not build another one", first, second)
            assertSame(
                "…and it must not have re-pointed WindowControl at a new object either",
                first,
                activity.windowControl.windowRepository,
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
     * The same obligation on the classic host, whose `onDestroy` is the other call site.
     *
     * `firstTime` is set false first, and that is not incidental: it is a file-level `var` in
     * `ActivityBase.kt`, and `MainBibleActivity.onCreate` RETURNS EARLY while it is true (the
     * night-mode `recreate()` hack), before ever reaching `registerNetworkCallback()`. A test that
     * did not pin it would pass or fail on whether some earlier test in the same JVM had already
     * consumed the flag — measured, not guessed: a probe in a fresh JVM registered zero callbacks
     * for the first `MainBibleActivity` and one for the next host built after it.
     */
    @Test
    fun theClassicHostUnregistersItsOwnCallbackToo() {
        firstTime = false
        val shadow = connectivityShadow
        val before = shadow.networkCallbacks.size
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        val activity = controller.create().get()
        assertTrue(
            "MainBibleActivity.onCreate must have registered its bootstrap's callback — if this " +
                "fails, the early `firstTime` return above ran and the assertion below is vacuous",
            activity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
        )

        controller.close()

        assertFalse(
            "classic's onDestroy must unregister it too — MainBibleActivity survives this batch " +
                "and is still a live second host",
            activity.readingAppBootstrap.networkCallback in shadow.networkCallbacks,
        )
        assertEquals("…and the count is back where it started", before, shadow.networkCallbacks.size)
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
     * The regression the coordinator's fix-round-1 dispatch named: `setSoftKeyboardMode()` is the
     * ONE method shared by both hosts (`MainBibleActivity.onCreate` calls it, same as
     * `NavHostComposeActivity.onCreate`'s `bootstrapIfNeeded()`), and Task 7's first attempt gated it
     * on a single `Build.VERSION_CODES.R` constant. That would have put `MainBibleActivity`'s window
     * into `ADJUST_NOTHING` on API 30-34, where classic's OWN compensating listener
     * (`MainBibleActivity.kt` ~:677) and `ActivityBase`'s inset setup (~:130/:141) both stay gated at
     * `>= VANILLA_ICE_CREAM` (35) -- so classic would get neither the framework's resize (suppressed
     * by `ADJUST_NOTHING`) nor any app-side padding (`imeBottomPaddingPx` is a permanent 0 on this
     * host), silently hiding the reading content behind the keyboard. Classic is not launched in
     * production today, but the regression must not be introduced.
     *
     * **This is the direct, end-to-end regression check, not a stand-in for one:** it builds a real
     * `MainBibleActivity` (as `theClassicHostUnregistersItsOwnCallbackToo` above already does) at sdk
     * 30 and reads the ACTUAL `Window.attributes.softInputMode` `setSoftKeyboardMode()` left behind,
     * the same observable Task 7's own `ReadingImePaddingTest` reads for the nav host. `firstTime` is
     * pinned false first for the same reason as that other classic-host test: `onCreate` returns
     * early while it is true, before ever reaching `setSoftKeyboardMode()`, which would make this
     * test pass or fail on JVM test order rather than on the fix.
     *
     * FAILS on the round-1-dispatch tree (single `>= R` gate): `mode` there is `ADJUST_NOTHING`, not
     * `ADJUST_RESIZE`.
     */
    @Config(sdk = [30])
    @Test
    fun theClassicHostKeepsAdjustResizeOnApi30() {
        firstTime = false
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            val mode = activity.window.attributes.softInputMode and
                WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

            assertEquals(
                "classic's compensating listener and ActivityBase's inset setup both stay gated at " +
                    ">= VANILLA_ICE_CREAM (35) -- a shared setSoftKeyboardMode() threshold that moved " +
                    "to >= R (30) would put this host into ADJUST_NOTHING on API 30-34 with nothing " +
                    "on either side compensating, hiding the reading content behind the keyboard",
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                mode,
            )
        } finally {
            controller.close()
        }
    }

    /**
     * The threshold values themselves, pinned directly -- cheap, and the reason
     * [theClassicHostKeepsAdjustResizeOnApi30] above fails or passes for the right cause rather than
     * by accident (e.g. `windowControl.isMultiWindow` happening to be true in some other test order,
     * which would also produce `ADJUST_PAN` instead of `ADJUST_RESIZE` and could be mistaken for this guard
     * firing). Also documents that `setSoftKeyboardMode()` reads `host.appOwnsImeInsetFromSdk` (the
     * generic bound `ReadingAppBootstrap<T> where T : ActivityBase, T : ReadingHostActivity` exists
     * for), not a constant of its own -- there is no `ReadingAppBootstrap` field to read directly (it
     * has none of its own; the value comes from whichever host is plugged in as `T`), so this is
     * expressed as the two hosts' own property values plus the source-level check that
     * `setSoftKeyboardMode()`'s body actually reads `host.appOwnsImeInsetFromSdk` and not a literal
     * `Build.VERSION_CODES` constant.
     */
    @Test
    fun theTwoHostsAnswerDifferentThresholdsAndTheBootstrapReadsTheHostsOwn() {
        firstTime = false
        val classicController = Robolectric.buildActivity(MainBibleActivity::class.java)
        val navController = navHostOnReading()
        try {
            val classic = classicController.create().get()
            val nav = navController.create().get()

            assertEquals(
                "classic's threshold must stay today's VANILLA_ICE_CREAM (35) -- unchanged by this batch",
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                classic.appOwnsImeInsetFromSdk,
            )
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
                    "(a literal Build.VERSION_CODES.R here would silently re-introduce the shared-" +
                    "threshold regression this file's other test guards)",
                Regex("""setSoftKeyboardMode\(\)\s*\{[\s\S]*?host\.appOwnsImeInsetFromSdk""")
                    .containsMatchIn(source),
            )
        } finally {
            classicController.close()
            navController.close()
        }
    }
}
