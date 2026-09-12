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
package net.bible.android.view.compose

import androidx.test.core.app.ApplicationProvider
import java.io.File
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.cloudsync.documents.SyncPlan
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task-8 fix round 2, finding B: [CloudDocumentsInGraphArmTest]'s
 * `theArmAsksControllerForAfreshOnEveryGenuineNewEntry` exercises the ARM's contract with a FAKE
 * `CloudDocumentsDeps`, which cannot fail against a reverted, `by lazy`-singleton
 * `NavHostComposeActivity.cloudDocumentsController` -- the bug (and fix round 1's repair of it)
 * live entirely in the HOST's own field. This file drives the REAL `NavHostComposeActivity`
 * instead, using the same private-field/method reflection technique `ClientPageObjectsTest` and
 * `TextDisplaySettingsComposeActivityColorsTest` already use in this module for state a production
 * class deliberately keeps private, and `ComposeHostActionBarTest`'s
 * `Robolectric.buildActivity(NavHostComposeActivity::class.java, intentFor(...)).create()` for
 * building the real Activity itself (no `ComposeTestRule` needed: none of this touches Compose
 * composition at all, only the plain Kotlin construction/callback wiring `buildCloudDocumentsController`
 * does).
 *
 * **What was tried and why this shape, not a full Compose round trip:** driving the real graph
 * through an actual `NavHost` (as `CloudDocumentsInGraphArmTest` does for the ARM) would need the
 * REAL `cloudDocumentsOpenOrGate()` to run to completion first (it touches `CloudSync`/`DocumentSync`,
 * real singletons with real IO), and then a genuine leave-and-reopen would need to survive that
 * gate twice while some coroutine from the FIRST entry is deliberately kept in flight past the
 * second entry's build -- an awkward amount of timing control for a property that does not need
 * real composition to observe: [buildCloudDocumentsController] is a plain private method with no
 * Compose dependency, and every constructor callback it wires (`onShowRemovedChange`, `onRescan`,
 * `onSyncNow`, `onAction`, `onBulkAction`) can be triggered directly through `CloudDocumentsController`'s
 * own PUBLIC API. Calling `buildCloudDocumentsController()` twice via reflection reproduces the
 * "abandoned entry 1, live entry 2" shape exactly, without needing Compose, a real sign-in, or any
 * timing games -- the callback closures either capture the right instance or they do not,
 * regardless of how much real time elapses between the two builds.
 *
 * **Why no `advanceUntilIdle`/dispatcher control was needed:** `lifecycleScope.launch { ... }`
 * uses `Dispatchers.Main.immediate` (the default `LifecycleCoroutineScope` context), and a
 * Robolectric test method runs ON the thread `Looper.getMainLooper()` is bound to -- so the
 * coroutine body runs SYNCHRONOUSLY up to its first genuine suspension point the moment `launch` is
 * called, before the triggering call (`controller.setShowRemoved(...)`/`.rescan()`) even returns.
 * Every assertion below reads state set during that synchronous prefix (`pushBusy(true)`, before the
 * suspending network/cache scan), so no idling is needed or attempted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudDocumentsControllerRebuildIsolationTest {

    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private fun buildActivity(): NavHostComposeActivity =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.cloudDocuments()),
        ).create().get()

    /** `buildCloudDocumentsController` is `private fun buildCloudDocumentsController(): CloudDocumentsController`. */
    private fun buildController(activity: NavHostComposeActivity): CloudDocumentsController {
        val method = NavHostComposeActivity::class.java.getDeclaredMethod("buildCloudDocumentsController")
        method.isAccessible = true
        return method.invoke(activity) as CloudDocumentsController
    }

    /**
     * The `CloudDocumentsEntry` [buildController]'s last call built -- the host's per-entry state
     * object (task-8 fix round 4), read straight off the `cloudDocumentsEntryRef` field it was just
     * assigned to. Typed `Any` on purpose: `CloudDocumentsEntry` is a PRIVATE nested class, and
     * nothing here needs to name its type -- only its `controller`/`lastPlan` fields, reached the
     * same reflective way everything else in this file is.
     */
    private fun currentEntry(activity: NavHostComposeActivity): Any {
        val field = NavHostComposeActivity::class.java.getDeclaredField("cloudDocumentsEntryRef")
        field.isAccessible = true
        return requireNotNull(field.get(activity)) { "no CloudDocuments entry has been built yet" }
    }

    private fun lastPlanField(entry: Any) =
        entry.javaClass.getDeclaredField("lastPlan").apply { isAccessible = true }

    private fun lastPlanOf(entry: Any): SyncPlan? = lastPlanField(entry).get(entry) as SyncPlan?

    /**
     * `private fun cloudDocumentsPresentSyncNow(entry: CloudDocumentsEntry, plan: SyncPlan)` -- the
     * REAL path that parks a resolved plan and puts the sheet up, located by name since its first
     * parameter type is private. Deliberately used instead of writing `lastPlan` reflectively: the
     * point of the tests below is where production code puts that plan, so production code has to be
     * the thing that puts it there. Only the IO round trip that RESOLVES the plan is skipped (its
     * caller, `cloudDocumentsShowSyncNow`, does that inside `withContext(Dispatchers.IO)`, whose
     * real-thread timing this test cannot control -- see the kdoc on the source-scan tests below).
     */
    private fun presentSyncNow(activity: NavHostComposeActivity, entry: Any, plan: SyncPlan) {
        val method = NavHostComposeActivity::class.java.declaredMethods
            .single { it.name == "cloudDocumentsPresentSyncNow" }
        method.isAccessible = true
        method.invoke(activity, entry, plan)
    }

    @Test
    fun `two entries in one Activity instance get two distinct controllers`() {
        val activity = buildActivity()
        val first = buildController(activity)
        val second = buildController(activity)
        assertTrue(first !== second, "buildCloudDocumentsController must return a fresh instance every call")
    }

    /**
     * The show-removed toggle path -- `CloudDocumentsController.setShowRemoved` calls
     * `onShowRemovedChange` synchronously and unconditionally, which is what makes this reachable
     * with an ordinary user action rather than a contrived one. Pre-fix, `onShowRemovedChange` was
     * a bare `::handleCloudDocumentsShowRemovedChange` method reference reading the shared
     * `cloudDocumentsController` accessor, so once a second entry had rebuilt it this would have
     * pushed busy onto [second] instead of [first] -- exactly backwards from both assertions below.
     */
    @Test
    fun `a show-removed toggle from an abandoned entry pushes busy onto the entry that started it, not the next entry`() {
        val activity = buildActivity()
        val first = buildController(activity)
        val second = buildController(activity)

        first.setShowRemoved(true)

        assertTrue(first.busy.value, "the toggle must push busy onto the controller that started it")
        assertFalse(second.busy.value, "the toggle must not leak onto the ref's current (later) controller")
    }

    /**
     * The rescan path -- `CloudDocumentsController.rescan()` calls the `onRescan` callback
     * [buildCloudDocumentsController] binds to `cloudDocumentsRunSyncAction(controller) { ... }`.
     * Same shape as the show-removed case, for the second of finding A's three named functions.
     */
    @Test
    fun `a rescan from an abandoned entry pushes busy onto the entry that started it, not the next entry`() {
        val activity = buildActivity()
        val first = buildController(activity)
        val second = buildController(activity)

        first.rescan()

        assertTrue(first.busy.value, "rescan must push busy onto the controller that started it")
        assertFalse(second.busy.value, "rescan must not leak onto the ref's current (later) controller")
    }

    /**
     * The Sync-now path -- the third of finding A's three named functions, and the one with no
     * public `CloudDocumentsController` entry point of its own (classic's "Sync now" overflow row
     * calls straight into the host, not through a controller callback). Reflects into the private
     * `cloudDocumentsShowSyncNow(controller: CloudDocumentsController)` directly.
     */
    @Test
    fun `showSyncNow for an abandoned entry pushes busy onto the entry that started it, not the next entry`() {
        val activity = buildActivity()
        val first = buildController(activity)
        val firstEntry = currentEntry(activity)
        val second = buildController(activity)

        showSyncNow(activity, firstEntry)

        assertTrue(first.busy.value, "showSyncNow must push busy onto the controller it was given")
        assertFalse(second.busy.value, "showSyncNow must not leak onto the ref's current (later) controller")
    }

    /** `private fun cloudDocumentsShowSyncNow(entry: CloudDocumentsEntry)` -- located by name, since its parameter type is private. */
    private fun showSyncNow(activity: NavHostComposeActivity, entry: Any) {
        val method = NavHostComposeActivity::class.java.declaredMethods
            .single { it.name == "cloudDocumentsShowSyncNow" }
        method.isAccessible = true
        method.invoke(activity, entry)
    }

    /**
     * Task-8 fix round 4, the open finding: leaving the destination with the Sync-now sheet STILL UP
     * must pin nothing on the host.
     *
     * Fix rounds 2 and 3 kept the resolved plan in a host-lifetime
     * `Map<CloudDocumentsController, SyncPlan>`, pruned on confirm (round 2) and then also on an
     * explicit dismiss (round 3, via a one-shot `syncNowDialog.first { it == null }` watcher). A
     * third way out was left open: nothing nulls `_syncNowDialog` when the arm's composition is
     * disposed WITHOUT either of those firing -- a pop or replace driven from elsewhere while the
     * sheet is up -- so the watcher (on `lifecycleScope`, which outlives the arm) waited forever,
     * itself holding a hard reference to the controller it was watching, and map entry, controller
     * and plan all stayed pinned for the rest of the host Activity's lifetime.
     *
     * Round 4 removed the container instead of adding a fourth pruning path: the plan is now a field
     * of the per-entry `CloudDocumentsEntry`, so this test asserts the STRUCTURAL property that makes
     * every such path unnecessary -- after a leave-and-reopen with the sheet still open, NO field of
     * the host holds the abandoned entry, its controller or its plan, directly or inside a collection.
     * That is deliberately generic rather than "the old map field is gone": any future host-lifetime
     * container that pins an abandoned entry fails here too, whatever it is called.
     */
    @Test
    fun `leaving with the Sync-now sheet open pins nothing on the host`() {
        val activity = buildActivity()
        val abandoned = buildController(activity)
        val abandonedEntry = currentEntry(activity)
        val plan = SyncPlan(emptyList(), emptyList(), emptyList(), 0L, 0L)
        // The plan is parked and the sheet goes up through the REAL host path, and is then NEVER
        // dismissed or confirmed -- the third way out, the one neither `confirmSyncNow` nor
        // `dismissSyncNow` covers.
        presentSyncNow(activity, abandonedEntry, plan)

        // ... and the destination is left and reopened while it is still up.
        val live = buildController(activity)
        val liveEntry = currentEntry(activity)

        assertNotNull(abandoned.syncNowDialog.value, "sanity: the abandoned entry's sheet is still up")
        assertTrue(liveEntry !== abandonedEntry, "sanity: the reopen built a genuinely new entry")
        assertNull(lastPlanOf(liveEntry), "a fresh entry must start with no pinned plan")

        val pinnedBy = NavHostComposeActivity::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filter { field ->
                field.isAccessible = true
                retains(field.get(activity), abandonedEntry, abandoned, plan)
            }
            .map { it.name }
        assertEquals(
            emptyList(), pinnedBy,
            "no host field may still hold an abandoned CloudDocuments entry, its controller or its " +
                "resolved Sync-now plan once the destination has been left with the sheet open -- " +
                "anything that does keeps them alive for the rest of the Activity's lifetime, since " +
                "no dismiss or confirm is ever coming for that sheet (task-8 fix round 4). Held by: $pinnedBy",
        )
        assertTrue(live.busy.value.not(), "sanity: the live entry was not disturbed")
    }

    /** Identity-reachability, one level into maps and collections -- enough for any plausible host-side pin. */
    private fun retains(value: Any?, vararg targets: Any): Boolean = when (value) {
        null -> false
        is Map<*, *> -> value.keys.any { retains(it, *targets) } || value.values.any { retains(it, *targets) }
        is Collection<*> -> value.any { retains(it, *targets) }
        else -> targets.any { it === value }
    }

    /**
     * The other half of what the removed map guaranteed: one entry's resolved plan must never answer
     * ANOTHER entry's confirm. Round 2 got this from an identity-keyed map; round 4 gets it from the
     * plan living on the entry whose `onSyncNow` callback was bound to it, which is strictly harder
     * to get wrong -- but it is the behaviour that matters, so it is asserted, not assumed.
     *
     * This one reads the entry's own `lastPlan` slot, so it is coupled to round 4's shape on
     * purpose: it says "the abandoned entry still holds its plan, so the live confirm cannot have
     * consumed it". Under a reverted, map-shaped implementation it fails for that reason rather than
     * for a behaviour reason -- the finding's own test above
     * (`leaving with the Sync-now sheet open pins nothing on the host`) is the one that fails on the
     * substance, naming the offending host field in its message.
     */
    @Test
    fun `one entry's pinned plan cannot answer another entry's confirm`() {
        val activity = buildActivity()
        buildController(activity)
        val abandonedEntry = currentEntry(activity)
        val plan = SyncPlan(emptyList(), emptyList(), emptyList(), 0L, 0L)
        presentSyncNow(activity, abandonedEntry, plan)

        val live = buildController(activity)
        live.confirmSyncNow(listOf(true, true, true))

        assertTrue(
            lastPlanOf(abandonedEntry) === plan,
            "the live entry's confirm must not consume (or dispatch) a plan resolved for an earlier entry",
        )
    }

    /**
     * Task-8 fix round 3, finding 2. **What was tried first, and why it isn't what ships:** a
     * reflection-driven test analogous to the ones above -- launch `cloudDocumentsRefreshFromNetwork`
     * via the raw suspend-function ABI (a trailing `Continuation` parameter, no `kotlin-reflect`
     * needed), build a second controller while it is suspended inside `withContext(Dispatchers.IO)`,
     * then `job.cancel()` and `shadowOf(Looper.getMainLooper()).idle()` to let the `finally` run and
     * assert it clears busy on the FIRST controller, not the second. It compiled and ran, but FAILED
     * flakily against the ALREADY-FIXED code: `Dispatchers.IO` in a JVM/Robolectric unit test is a
     * REAL background thread pool, and its resumption back onto `Dispatchers.Main` only happens once
     * that real thread's work actually completes and posts back -- `idle()` only drains whatever the
     * Android main-looper queue ALREADY holds, so if the background thread has not yet posted (a real
     * wall-clock race, not a coroutine-ordering guarantee this test can control), `idle()` does
     * nothing and the assertion sees a `finally` that has simply not run yet. A test that fails
     * against correct code for timing reasons is worse than no test, so this is a deliberate SOURCE
     * SCAN instead -- the same choice `SearchHostBackRoutingGuardTest`/`MenuSeamGuardTest` make for
     * properties a runtime assertion cannot pin down without flakiness or invasive production changes
     * (a swappable IO dispatcher `cloudDocumentsRefreshFromNetwork`/`cloudDocumentsOpenOrGate` do not
     * have and should not gain just for this).
     *
     * The property fix round 3 established is entirely mechanical and exactly what a scan CAN prove
     * deterministically: [assertControllerReadExactlyOnce] asserts the bare identifier
     * `cloudDocumentsController` (the mutable ref's accessor) appears EXACTLY ONCE in each function's
     * body -- the one `val controller = cloudDocumentsController` capture at the top, comments
     * stripped so a stray mention in a doc comment cannot inflate the count. A regression that
     * re-introduced a second read (in a `finally`, or anywhere after another suspension point) would
     * push the count to two and fail here, with no dependence on real thread timing at all.
     */
    @Test
    fun `cloudDocumentsRefreshFromNetwork reads the mutable controller ref exactly once`() {
        assertControllerReadExactlyOnce("cloudDocumentsRefreshFromNetwork")
    }

    /** The other function [cloudDocumentsControllerRef]'s kdoc names as reading the property directly. */
    @Test
    fun `cloudDocumentsOpenOrGate reads the mutable controller ref exactly once`() {
        assertControllerReadExactlyOnce("cloudDocumentsOpenOrGate")
    }

    private val navHostComposeActivitySource by lazy {
        stripComments(File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt").readText())
    }

    private fun assertControllerReadExactlyOnce(functionName: String) {
        val body = extractFunctionBody(navHostComposeActivitySource, functionName)
        val hits = Regex("""\bcloudDocumentsController\b""").findAll(body).count()
        assertEquals(
            1, hits,
            "$functionName must read the mutable cloudDocumentsController ref exactly ONCE (a " +
                "`val controller = cloudDocumentsController` capture up front), never again later in the " +
                "same function -- a second read (e.g. inside a `finally`, or after another suspension " +
                "point) can resolve to a DIFFERENT (later) entry's controller if a leave-and-reopen " +
                "rebuilt the ref while this function was suspended (task-8 fix round 3, finding 2). Found " +
                "$hits occurrence(s) in:\n$body",
        )
    }

    /**
     * Extracts [functionName]'s body -- the braces immediately following its signature -- from
     * [source] by simple brace counting. Fine for this file: neither function this test scans
     * contains a string or char literal with an unbalanced `{`/`}` inside it.
     */
    private fun extractFunctionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        require(signatureIndex >= 0) { "no `fun $functionName(` found in NavHostComposeActivity.kt -- has it been renamed or moved?" }
        val openBraceIndex = source.indexOf('{', signatureIndex)
        require(openBraceIndex >= 0) { "no opening brace found after `fun $functionName(...)`" }
        var depth = 0
        var i = openBraceIndex
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBraceIndex + 1, i)
                }
            }
            i++
        }
        error("unbalanced braces while scanning the body of $functionName")
    }

    /** [SearchHostBackRoutingGuardTest]'s own helper, copied rather than shared -- see that file's kdoc for why. */
    private fun stripComments(text: String): String {
        val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        return noBlockComments.lines().joinToString("\n") { line ->
            val commentAt = line.indexOf("//")
            if (commentAt >= 0) line.substring(0, commentAt) else line
        }
    }
}
