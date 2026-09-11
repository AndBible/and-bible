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

package net.bible.android.view.nav

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.compose.ClassicRemovalScan
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The coexistence seam's contract: a MIGRATED screen resolves to the nav host carrying its route,
 * an unmigrated one still resolves to its own Activity, and no screen is both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class NavHostRoutingGuardTest {

    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aMigratedScreenResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ToolInfo)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_TOOL_INFO, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiDocumentFilterResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiDocumentFilter)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_DOCUMENT_FILTER, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun globalToolPermissionsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.GlobalToolPermissions)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiModelsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiModels)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_MODELS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiConnectionSettingsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiConnectionSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.AI_CONNECTION_SETTINGS,
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiProviders)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.aiProviders(startEasySetup = false),
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersEasySetupRouteCarriesTheArgument() {
        val plainRoute = NavRoutes.aiProviders(startEasySetup = false)
        val easySetupRoute = NavRoutes.aiProviders(startEasySetup = true)
        assertTrue(
            easySetupRoute.contains("${NavRoutes.ARG_START_EASY_SETUP}=true"),
            "the easy-setup route must carry startEasySetup=true: $easySetupRoute",
        )
        assertTrue(
            plainRoute != easySetupRoute,
            "the easy-setup route must be distinguishable from the plain AiProviders route",
        )
    }

    @Test
    fun aiPromptsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiPrompts)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_PROMPTS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun promptEditResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.PromptEdit)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.promptEdit(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun rawLlmLogIsNotInMigratedAndIntentForThrows() {
        // Whole-branch review M2: unlike Screen.PromptEdit, an argument-less RawLlmLog route has
        // no safe meaning (both ids null renders an empty screen with no log), so it was dropped
        // from ScreenLauncher.MIGRATED rather than mapped to NavRoutes.rawLlmLog(). intentFor then
        // falls through to targetFor, whose targetForMigratedScreen guard throws loudly instead
        // of silently opening a blank screen.
        assertTrue(Screen.RawLlmLog !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.RawLlmLog) }
    }

    @Test
    fun rawLogHistoryResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.RawLogHistory)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_RAW_LOG_HISTORY, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingPlanSelectorResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingPlanSelector)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.READING_PLAN_SELECTOR, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun dailyReadingListResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.DailyReadingList)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.READING_PLAN_DAY_LIST, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingPlanResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The argument-free route is the correct MIGRATED value: the classic host branched on
        // extras.containsKey, so "no plan, no day" is a real state meaning "current plan day".
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingPlan)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.dailyReading(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun searchResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The argument-free search form is a real screen (classic `SearchComposeActivity` opened
        // with no extras is the empty Find screen), so it is a valid MIGRATED value. Callers that
        // DO know a query/search-type/section (HistoryManager's stored route) build
        // NavRoutes.searchForm(...) directly, bypassing this map.
        val intent = ScreenLauncher.intentFor(context, Screen.Search)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.searchForm(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun searchIndexResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // Also a real screen without arguments: classic `SearchIndexComposeActivity.kt:46` falls
        // back to the CURRENT PAGE's document when SEARCH_DOCUMENT is absent, so an argument-free
        // index prompt has a defined meaning ("index the book I am reading").
        val intent = ScreenLauncher.intentFor(context, Screen.SearchIndex)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.searchIndex(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * The slice-5 twin of [rawLlmLogIsNotInMigratedAndIntentForThrows], and the reason
     * `Screen.SearchIndexProgress` is deliberately absent from [ScreenLauncher.MIGRATED] even
     * though its destination now exists in the graph: an ARGUMENT-FREE index-progress route has no
     * safe meaning. Every real edge into that screen (classic
     * `SearchIndexComposeActivity.kt:72`, now the graph's `SEARCH_INDEX_PATTERN` arm) builds a
     * route carrying the chain's five arguments; a bare `ScreenLauncher.open(ctx,
     * Screen.SearchIndexProgress)` would open a progress screen watching nothing, which then has
     * no document to route onward to when indexing completes.
     *
     * Task 9 deleted the six search host Activities, so this now asserts exactly what the
     * AI-cluster twin does: absent from MIGRATED, and `intentFor` falls through to `targetFor`,
     * whose `targetForMigratedScreen` guard throws. (Between slice 5 and Task 9 it asserted the
     * weaker "never the nav host with an argument-free route", because the classic Activity was
     * still a legal fallback target; the guarantee that matters is unchanged, and is now stronger.)
     */
    @Test
    fun searchIndexProgressIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.SearchIndexProgress !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchIndexProgress) }
    }

    @Test
    fun epubSearchResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The EPUB search FORM is argument-free by construction — classic
        // `EpubSearchComposeActivity` read no extras at all and took its document from the current
        // page — so `NavRoutes.EPUB_SEARCH` is the whole route and a valid MIGRATED value. (Its two
        // callers, `SearchControl.getSearchIntent` and classic `SearchIndexProgressComposeActivity`,
        // both build it with no extras.)
        val intent = ScreenLauncher.intentFor(context, Screen.EpubSearch)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.EPUB_SEARCH, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    /**
     * A second slice-5 twin of [rawLlmLogIsNotInMigratedAndIntentForThrows] — see
     * [searchIndexProgressIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt] for the full reasoning
     * and for why Task 9 turned this into the `assertFailsWith` form.
     *
     * `Screen.SearchResults` specifically: a results route with no `searchText` has nothing to
     * search for. Every real edge builds `NavRoutes.searchResults(...)` with a query (the graph's
     * `SEARCH_FORM_PATTERN`/`SEARCH_INDEX_PROGRESS_PATTERN` arms; `BibleView` and `LinkControl`
     * since Task 6), bypassing this map.
     */
    @Test
    fun searchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.SearchResults !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.SearchResults) }
    }

    /**
     * The EPUB twin of [searchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt], and for the
     * same reason: `NavRoutes.EPUB_SEARCH_RESULTS_PATTERN`'s `searchText` is REQUIRED
     * (`NavRoutes.epubSearchResults` takes it as a non-null parameter), so there is no argument-free
     * route to map here even in principle. Every real edge builds one with a query — the graph's
     * `EPUB_SEARCH` arm on submit, and its `SEARCH_INDEX_PROGRESS_PATTERN` arm after an epub index
     * completes. Task 9 converted this to the `assertFailsWith<IllegalStateException>` form when it
     * deleted `EpubSearchResultsComposeActivity`.
     */
    @Test
    fun epubSearchResultsIsNotInMigratedSoNoArgumentFreeRouteCanOpenIt() {
        assertTrue(Screen.EpubSearchResults !in ScreenLauncher.MIGRATED)
        assertFailsWith<IllegalStateException> { ScreenLauncher.intentFor(context, Screen.EpubSearchResults) }
    }

    @Test
    fun settingsResolvesToTheNavHostCarryingItsRoute() {
        // slice 6, Task 7. The REFRESH_DISPLAY_ON_FINISH edge (MenuCommandHandler.kt:182-186)
        // survives this move untouched: the intent still names an Activity — the nav host — so
        // `startActivityForResult(handlerIntent, REFRESH_DISPLAY_ON_FINISH)` still returns its
        // request code to MainBibleActivity.onActivityResult when the host finishes.
        val intent = ScreenLauncher.intentFor(context, Screen.Settings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.SETTINGS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun syncSettingsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.SyncSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.SYNC_SETTINGS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun readingProgressSettingsResolvesToTheNavHostCarryingItsRoute() {
        // slice 6, Task 8. No arguments at all: the classic host read no extras.
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingProgressSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.READING_PROGRESS_SETTINGS,
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun readingProgressResolvesToTheNavHostCarryingItsArgumentFreeRoute() {
        // The ARGUMENT-FREE route is the correct MIGRATED value, and argument-free MEANS something
        // here: classic ReadingProgressComposeActivity.kt:76-82 defaults an ABSENT
        // ReadingProgressKeys.EXTRA_TAB to the persisted `reading_progress_last_tab` setting, so
        // "no tab" is the real state "open the tab the user was last on" — not a dropped argument.
        // The one caller that DOES know a tab (BibleJavascriptInterface.openReadingProgress) builds
        // NavRoutes.readingProgress(tab) directly, bypassing this map.
        val intent = ScreenLauncher.intentFor(context, Screen.ReadingProgress)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.readingProgress(),
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun promptEditTemplateRoundTripsThroughDecodeArg() {
        // Free text, deliberately containing reserved/percent/unicode characters that would
        // corrupt the route if encodeArg/decodeArg were not both applied — see NavRoutes' kdoc.
        val freeText = "Rock & Roll: 100% <great> \"quoted\" — täst\nwith newline"
        val route = NavRoutes.promptEdit(template = freeText)
        val encoded = route.substringAfter("${NavRoutes.ARG_PROMPT_TEMPLATE}=").substringBefore("&")
        assertEquals(freeText, NavRoutes.decodeArg(encoded))
    }

    /**
     * Task 3 fix round 1, finding I3. `HistoryManager` re-launches a stored history intent through
     * `IntentHistoryItem.revertTo()` (`IntentHistoryItem.kt:59-65`) with
     * `FLAG_ACTIVITY_REORDER_TO_FRONT`, which matches on the **component** alone — it carries no
     * notion of this host's `EXTRA_ROUTE`. One component now serves every migrated cluster, so with
     * the default `standard` launch mode a reading-plan history entry could reorder an EXISTING
     * AI-cluster instance to the front, drop the stored route, and show a completely unrelated
     * screen. `singleTop` is what routes that re-launch into
     * [NavHostComposeActivity.onNewIntent], which navigates the live graph to the requested route
     * instead.
     *
     * A text scan of the manifest rather than a behavioural test: `launchMode` is a manifest-only
     * fact with no runtime accessor that a Robolectric unit test can read back, and the failure it
     * guards against (a silently wrong screen after a history revert) has nothing else watching it.
     */
    @Test
    fun theNavHostIsSingleTopSoAHistoryRevertCannotShowAnotherClustersScreen() {
        val manifest = java.io.File("src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "src/main/AndroidManifest.xml is missing — this guard would pass vacuously")

        val block = manifest.readText()
            .substringAfter("""android:name="${NavHostComposeActivity::class.java.name}"""", "")
            .substringBefore("/>")
        assertTrue(
            block.isNotBlank(),
            "no <activity> block for ${NavHostComposeActivity::class.java.name} in the manifest",
        )
        assertTrue(
            block.contains("""android:launchMode="singleTop""""),
            "the nav host must be singleTop so a FLAG_ACTIVITY_REORDER_TO_FRONT history revert " +
                "reaches onNewIntent with its EXTRA_ROUTE instead of silently reordering an " +
                "instance serving a different cluster. Block was:\n$block",
        )
    }

    @Test
    fun anUnmigratedScreenStillResolvesToItsOwnActivity() {
        val intent = ScreenLauncher.intentFor(context, Screen.Bookmarks)
        assertEquals(
            "net.bible.android.view.activity.bookmark.BookmarksComposeActivity",
            intent.component?.className,
        )
        assertTrue(intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE) == null)
    }

    @Test
    fun everyMigratedScreenHasARouteAndNoneIsBlank() {
        assertTrue(ScreenLauncher.MIGRATED.isNotEmpty())
        ScreenLauncher.MIGRATED.forEach { (screen, route) ->
            assertTrue(route.isNotBlank(), "$screen has a blank route")
        }
    }

    /**
     * Task 7 fix round 1's durable net. `ScreenLauncher.MIGRATED` is a `Map<Screen, String>` — it
     * can only carry an argument-LESS route: [ScreenLauncher.intentFor] resolves a MIGRATED screen
     * straight to `NavHostComposeActivity.intentFor(context, route)`, which reads only
     * [NavHostComposeActivity.EXTRA_ROUTE]. A caller that then adds a `.putExtra(...)` on TOP of
     * `ScreenLauncher.intentFor(context, Screen.X)` for a MIGRATED `X` gets an Intent whose extra is
     * silently dropped — no compiler error, no crash, just the wrong screen state. This is exactly
     * what broke `AiPromptsComposeActivity.onOpenPrompt` and `BibleJavascriptInterface
     * .openPromptEditor` the moment `Screen.PromptEdit` was migrated (Task 7), and
     * `AiConnectionSettingsComposeActivity.launchEasySetup` the same way against `Screen.AiProviders`
     * (Task 6) — all three fixed in the same round as this test, by building the concrete route
     * directly instead: `NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...))`.
     *
     * Scans every shipping source file ([ClassicRemovalScan.appSources]) for three shapes, per
     * `ScreenLauncher.intentFor(...)` call site whose argument list names a screen currently in
     * [ScreenLauncher.MIGRATED]:
     *  1. `ScreenLauncher.intentFor(..., Screen.X).putExtra(...)` — chained directly.
     *  2. `ScreenLauncher.intentFor(..., Screen.X).apply { ... putExtra(...) ... }` — chained via
     *     `apply` (the block is captured by brace-balance, not by a fixed-width window, since an
     *     `apply` block can itself contain nested braces).
     *  3. `val name = ScreenLauncher.intentFor(..., Screen.X)` followed nearby by
     *     `name.putExtra(...)` — the shape that actually broke `openPromptEditor`; a scan limited to
     *     shapes 1-2 would have missed it.
     *
     * **Hardened in Task 8's fix round** against three ways the ORIGINAL version of this scan
     * (Task 7) failed OPEN — missed a real offender instead of flagging it — rather than merely
     * failing to flag a shape that never occurs:
     *  - **An explicit type annotation** (`val intent: Intent = ScreenLauncher.intentFor(...)`).
     *    The old shape-3 regex captured the token immediately before `=`, so a type annotation
     *    made it capture the TYPE NAME (`Intent`), not the variable (`intent`) — the later
     *    `intent.putExtra(...)` was then never matched against anything. Fixed by trying a
     *    typed-assignment regex first (`(\w+)\s*:\s*[^=\n]*=\s*$`) and falling back to the bare one.
     *  - **A multi-line-formatted call.** The old scan located each call by the literal substring
     *    `"Screen.X)"` — the screen literal adjacent to the closing paren with ZERO whitespace —
     *    which a line-wrapped call (screen argument on its own line, closing paren on the next)
     *    never produces. A real precedent already exists in the tree:
     *    `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt` spreads
     *    `ScreenLauncher.intentFor(` across several lines with the screen chosen by an `if`
     *    expression (not a live miss today — `Screen.SearchIndex`/`Screen.SearchResults` aren't
     *    migrated yet — but it WILL be, in a later slice). Fixed by locating each call by its own
     *    `(` / matching `)` (brace-balance, reusing the same technique as the `.apply` block scan)
     *    rather than by a marker glued to one particular argument.
     *  - **Named or reordered arguments**, where the screen is not textually the last argument
     *    (`ScreenLauncher.intentFor(screen = Screen.X, context = context)`). The old scan required
     *    the screen literal to sit immediately before the call's OWN closing paren, which a named
     *    argument in a non-last position never does. Fixed by extracting the whole balanced argument
     *    list once per call and searching it for a word-bounded `Screen.X` anywhere inside, rather
     *    than anchoring on "right before the closing paren".
     *
     * **Hardened again in Task 8's fix round 1** against a fourth fail-open shape a reviewer
     * constructed and ran against the round-1 version of this scan: `.also { it.putExtra(...) }`
     * (and equally `.let`/`.run`) — chained-scope-function forms other than `.apply`, which the
     * scan did not look for at all. No live caller uses one of these today (verified by grep over
     * every `ScreenLauncher.intentFor` call site in `app/src`), but `.also`/`.let` on an `Intent` is
     * idiomatic enough Kotlin that a future caller will write one. Fixed by matching any of
     * `apply`/`also`/`let`/`run` after the call, then checking the captured block for `putExtra(...)`
     * with the receiver reference each form actually uses: `apply`/`run` rebind `this`, so a bare
     * `putExtra(...)` is what a real caller writes; `also`/`let` do NOT rebind `this` -- the receiver
     * is only reachable as the implicit `it` or an explicit named lambda parameter (`.also { intent ->
     * intent.putExtra(...) }`), so the scan looks for `it.putExtra(...)` OR `<paramName>.putExtra(...)`
     * for those two, reading the parameter name off the lambda's own `name ->` header when present.
     *
     * **Hardened again in Task 8's fix round 2** against two more fail-open gaps a second reviewer
     * found — one of them against a LIVE call site, not merely a future risk:
     *  - **`putExtras` (plural, a whole `Bundle`) matched none of the three checks**, because every
     *    one of them searched literally for `"putExtra("` / `\.putExtra\s*\(`, and the character
     *    after `putExtra` in `putExtras(` is `s`, not `(`. Live today:
     *    `LinkControl.kt`'s `intent.putExtras(searchParams)` after a plain assignment, targeting
     *    `Screen.SearchIndex`/`Screen.SearchResults` (not migrated yet, so not a live BUG today --
     *    but the guard existed to catch exactly this shape the day one of those screens migrates,
     *    and it silently would not have). Fixed by giving every check the shared
     *    [PUT_EXTRA_METHODS] alternation (`putExtras?`) instead of the bare singular name — see its
     *    kdoc for why this cannot also match an unrelated identifier that merely starts with those
     *    characters.
     *  - **A TYPED `also`/`let` lambda parameter** (`.also { intent: Intent -> intent.putExtra(...) }`)
     *    defeated the named-parameter read: the header regex's `\w+` stopped at the `:`, so the
     *    match failed outright and the code silently fell back to `it` (which is not a substring of
     *    `intent`). Fixed by accepting an optional, uncaptured `: <type>` between the parameter name
     *    and `->`.
     *
     * **Hardened again in nav-graph 3/5/6 Task 6** against a fifth fail-open shape -- and this one
     * failed open on a LIVE BUG, not a future risk, which is the worst thing a guard can do:
     *  - **The call as a BRANCH of a conditional whose RESULT is assigned**, i.e.
     *    `val intent = if (needToIndex) { ScreenLauncher.intentFor(a, Screen.SearchIndex) } else {
     *    ScreenLauncher.intentFor(a, Screen.SearchResults) }` followed by `intent.putExtras(...)`.
     *    Both assignment regexes anchor immediately before the call (`(\w+)\s*=\s*$` and its typed
     *    twin), and what sits there is the branch opener `if (needToIndex) {` -- not `intent =` --
     *    so neither matched, no scope-function form applied either, and the scan reported nothing.
     *    `LinkControl.showAllOccurrences` had exactly this shape, and the day Task 4 migrated
     *    `Screen.SearchIndex` it became a real dropped-argument bug (a Strong's link into an
     *    unindexed module indexed the CURRENT page's book instead of the Strong's Bible, then
     *    landed on an empty search form) that this test passed straight over. Fixed by a third
     *    assignment check: find the NEAREST `name = if (` / `name = when (` within
     *    [CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS] behind the call, confirm the call is still INSIDE
     *    that conditional expression, then look ahead for `name.putExtra(s)(...)` exactly as the
     *    plain-assignment check does. Containment is decided from the text between the
     *    conditional's opening `(`/`{` and the call, with comments blanked ([withoutComments]):
     *    more `{` than `}` (a braced branch -- `} else {` nets back to depth 1), or depth 0 ending
     *    in `)` (the brace-less `= if (cond) intentFor(...)` form); and no `;` or local
     *    `val`/`var`, either of which means a new statement began and the conditional is no longer
     *    what we are inside of. Run only when the plain-assignment check did not already fire, so
     *    one offender cannot be reported twice.
     *
     * **Known, deliberate, NOT fixed** (explicitly out of scope for this hardening, so the next
     * person here does not have to rediscover why):
     *  - **A conditional branch that declares a local before the call**
     *    (`val intent = if (x) { val ctx = foo(); ScreenLauncher.intentFor(ctx, Screen.X) } ...`).
     *    The containment check treats a `val`/`var` between the conditional head and the call as
     *    "a new statement started", which is what keeps an unrelated earlier `val foo = if (...)`
     *    from being read as this call's assignment. Trading that miss for the false positives is
     *    deliberate: a false offender here blocks a green branch on a non-bug, and no live caller
     *    in the tree writes the declaring form.
     *  - **A conditional whose result is RETURNED rather than assigned** and whose extras are added
     *    by the caller (`return if (x) intentFor(..) else intentFor(..)`, then
     *    `getSearchIntent(...)?.putExtra(...)` at the call site). There is no local name to follow,
     *    so this is the cross-function case below by another route -- it needs data flow, not a
     *    regex. `SearchControl.getSearchIntent` is precisely this shape; Task 6 verified by hand
     *    that all five of its callers pass the Intent straight to `startActivityForResult` with no
     *    extras added, and rewrote its `Screen.SearchIndex` branch to build a concrete route anyway.
     *  - `with(intentVar) { putExtra(...) }` -- no live caller uses `with` on a `ScreenLauncher
     *    .intentFor(...)` result today; purely theoretical.
     *  - A helper function that receives the built `Intent` and adds extras to it elsewhere (e.g.
     *    `fun addSearchExtras(intent: Intent) { intent.putExtra(...) }` called on the result) -- an
     *    inherent limit of a textual/regex scan; catching it needs real static analysis (a
     *    call-graph/data-flow pass), not a bigger regex.
     *  - The pre-existing looseness of the `apply`/`run` bare-substring check (now
     *    `\bputExtras?\s*\(`, previously `.contains("putExtra(")`): since `apply`/`run` rebind
     *    `this`, the scan cannot tell a bare `putExtra(...)` on the actual `Intent` receiver from
     *    one on some OTHER value the block happens to also touch (e.g. a `someOtherIntent
     *    .putExtra(...)` sitting in the same block) -- a same-block false positive is possible in
     *    principle, predates this hardening round, and is accepted rather than chased.
     *
     * The general shape is now: find each `ScreenLauncher.intentFor(` call by locating its own
     * matching closing paren ([matchingParenIndex]); read every migrated screen named ANYWHERE in
     * that balanced argument list; then check independently, relative to that call's OWN
     * boundaries, for a chained `.putExtra(s)`, a chained `.apply`/`.also`/`.let`/`.run { ... }`
     * whose body reaches `putExtra(s)(...)` through the receiver form that scope function actually
     * uses, an assignment (typed or not) whose variable later receives a nearby
     * `.putExtra(s)(...)`, or -- when that last one does not fire -- an enclosing `if`/`when` whose
     * own result is assigned to a variable that then receives one.
     *
     * **Known, deliberate bound** (stated rather than overclaimed, matching this file's sibling
     * scans in [ClassicRemovalScan]'s own kdoc style): the assignment shape's forward look for
     * `name.putExtra(` is windowed to the next [ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS] characters, not
     * the rest of the file. Every real occurrence in this repo today has its `.putExtra` within a
     * few lines of the assignment; an unbounded look would risk a same-named variable in a LATER,
     * unrelated function reading as a false offender.
     */
    @Test
    fun migratedScreenArgumentIsNeverDroppedByAPutExtra() {
        val migratedScreenNames = ScreenLauncher.MIGRATED.keys.map { it.name }
        assertTrue(migratedScreenNames.isNotEmpty(), "ScreenLauncher.MIGRATED is empty -- this scan would pass vacuously")

        val callMarker = "ScreenLauncher.intentFor("
        val offenders = mutableListOf<String>()
        for (file in ClassicRemovalScan.appSources()) {
            val text = file.readText()
            val path = file.path.replace('\\', '/')

            var searchFrom = 0
            while (true) {
                val callStart = text.indexOf(callMarker, searchFrom)
                if (callStart < 0) break

                val openParenIndex = callStart + callMarker.length - 1
                val closeParenIndex = matchingParenIndex(text, openParenIndex)
                if (closeParenIndex == null) {
                    // Truncated/malformed input -- nothing further to find from here either.
                    break
                }
                searchFrom = closeParenIndex + 1

                // Which MIGRATED screens does this call's own argument list name, anywhere in it
                // (not just as the last argument -- see the named/reordered-arguments hardening
                // above)? A word boundary keeps e.g. "Screen.RawLlmLog" from matching a longer
                // hypothetical "Screen.RawLlmLogSomethingElse".
                val argsText = text.substring(openParenIndex + 1, closeParenIndex)
                val matchedScreens = migratedScreenNames.filter { name ->
                    Regex("""Screen\.${Regex.escape(name)}\b""").containsMatchIn(argsText)
                }
                if (matchedScreens.isEmpty()) continue

                val afterCall = text.substring(closeParenIndex + 1)

                val chainedPutExtra = Regex("""^\s*\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(afterCall.take(200))

                // apply/also/let/run: apply/run rebind `this` to the receiver, so a real caller's
                // body calls putExtra(s)(...) bare; also/let do NOT rebind `this` -- the receiver
                // is only reachable as the implicit `it` or an explicit named lambda parameter, so
                // those two are checked for `it.putExtra(s)(...)`/`<param>.putExtra(s)(...)` instead.
                var chainedScopeFunctionDescription: String? = null
                val chainedScopeMatch = Regex("""^\s*\.(apply|also|let|run)\s*\{""").find(afterCall.take(200))
                if (chainedScopeMatch != null) {
                    val functionName = chainedScopeMatch.groupValues[1]
                    val braceIndex = closeParenIndex + 1 + chainedScopeMatch.range.last
                    val block = balancedBraceBlock(text, braceIndex)
                    if (block != null) {
                        val body = block.removePrefix("{").removeSuffix("}")
                        val bodyHasPutExtra = when (functionName) {
                            "apply", "run" -> Regex("""\b$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(body)
                            else -> {
                                // "also"/"let": an explicit named lambda parameter ("intent ->" or
                                // the typed "intent: Intent ->"), or the implicit "it" when none is
                                // declared. The optional type annotation is matched but not
                                // captured -- only the parameter NAME is needed.
                                val namedParam = Regex("""^\s*(\w+)\s*(?::\s*.+?)?\s*->""")
                                    .find(body)?.groupValues?.get(1)
                                val receiverName = namedParam ?: "it"
                                Regex("""\b${Regex.escape(receiverName)}\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(body)
                            }
                        }
                        if (bodyHasPutExtra) {
                            chainedScopeFunctionDescription = "chained with .$functionName { ... putExtra(...) ... }"
                        }
                    }
                }

                // Is this call the RHS of an assignment -- i.e. does "<name>(: Type)? = " (or
                // nothing at all) immediately precede "ScreenLauncher.intentFor("? Typed first
                // (fixes the type-annotation miss above), bare as a fallback.
                var assignedPutExtraName: String? = null
                val beforeCall = text.substring(0, callStart)
                val typedAssignment = Regex("""(\w+)\s*:\s*[^=\n]*=\s*$""").find(beforeCall)
                val bareAssignment = Regex("""(\w+)\s*=\s*$""").find(beforeCall)
                val assignedTo = typedAssignment ?: bareAssignment
                if (assignedTo != null) {
                    val name = assignedTo.groupValues[1]
                    val window = afterCall.take(ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS)
                    if (Regex("""\b${Regex.escape(name)}\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(window)) {
                        assignedPutExtraName = name
                    }
                }

                // Is this call a BRANCH of a conditional that is itself the RHS of an assignment --
                // `val name = if (cond) { intentFor(..) } else { intentFor(..) }` -- followed by
                // `name.putExtra(s)(...)`? Neither regex above can see this shape: what immediately
                // precedes the call is the branch opener (`if (needToIndex) {`), not `name =`. See
                // the kdoc's Task 6 hardening entry; live in LinkControl.showAllOccurrences.
                var conditionalAssignedPutExtraName: String? = null
                if (assignedPutExtraName == null) {
                    val lookBehind = beforeCall.takeLast(CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS)
                    // The NEAREST preceding conditional assignment (typed or not); an earlier,
                    // already-closed one would be rejected by the containment check below anyway,
                    // but starting from the nearest keeps `between` as short as possible.
                    val conditionalAssignment =
                        Regex("""(\w+)\s*(?::\s*[^=\n]*)?=\s*(?:if|when)\s*[({]""")
                            .findAll(lookBehind)
                            .lastOrNull()
                    if (conditionalAssignment != null) {
                        val name = conditionalAssignment.groupValues[1]
                        // Everything between the conditional's opening `(`/`{` and this call. The
                        // call is still INSIDE that conditional expression when the text in
                        // between opens more braces than it closes (a braced branch, including the
                        // `} else {` hop, which nets back to depth 1), or when it closes the
                        // condition's paren and opens nothing (the brace-less
                        // `= if (cond) intentFor(...)` form). A `;` or a local `val`/`var`
                        // declaration in between means a new statement started, so the conditional
                        // is no longer what we are inside of.
                        val between = withoutComments(lookBehind.substring(conditionalAssignment.range.last + 1))
                        val braceDepth = between.count { it == '{' } - between.count { it == '}' }
                        val stillInsideTheConditional =
                            !between.contains(';') &&
                                !Regex("""\b(?:val|var)\b""").containsMatchIn(between) &&
                                (braceDepth >= 1 || (braceDepth == 0 && between.trimEnd().endsWith(')')))
                        if (stillInsideTheConditional) {
                            val window = afterCall.take(ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS)
                            if (Regex("""\b${Regex.escape(name)}\.$PUT_EXTRA_METHODS\s*\(""").containsMatchIn(window)) {
                                conditionalAssignedPutExtraName = name
                            }
                        }
                    }
                }

                val shapeDescriptions = mutableListOf<String>()
                if (chainedPutExtra) shapeDescriptions.add("chained directly with .putExtra(...)")
                chainedScopeFunctionDescription?.let { shapeDescriptions.add(it) }
                assignedPutExtraName?.let { name ->
                    shapeDescriptions.add("assigned to `$name`, then `$name.putExtra(...)` nearby")
                }
                conditionalAssignedPutExtraName?.let { name ->
                    shapeDescriptions.add(
                        "a branch of an if/when assigned to `$name`, then `$name.putExtra(...)` nearby",
                    )
                }

                for (screenName in matchedScreens) {
                    for (description in shapeDescriptions) {
                        offenders.add("$path: Screen.$screenName $description")
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a MIGRATED screen's ScreenLauncher.intentFor(...) intent still receives .putExtra(...) " +
                "-- ScreenLauncher.MIGRATED can only carry an argument-less route, so the extra is " +
                "silently dropped (NavHostComposeActivity reads only EXTRA_ROUTE). Build the concrete " +
                "route explicitly instead: NavHostComposeActivity.intentFor(context, NavRoutes.xyz(...)). " +
                "Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    private companion object {
        /** See [migratedScreenArgumentIsNeverDroppedByAPutExtra]'s kdoc, the assignment shape's known bound. */
        const val ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS = 600

        /**
         * The conditional-RHS shape's backward twin of [ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS]: how far
         * BEHIND a call the scan looks for the `name = if (`/`name = when (` that the call may be a
         * branch of. Bounded for the same reason the forward look is -- an unbounded search would
         * keep finding some conditional assignment eventually, in an unrelated earlier function,
         * and lean entirely on the containment check to reject it.
         */
        const val CONDITIONAL_ASSIGN_LOOKBEHIND_CHARS = 600

        /**
         * The literal method-name alternation every offender check searches for, explicit and
         * shared rather than hand-repeated per call site: `putExtra` (singular, one key/value) OR
         * `putExtras` (plural, an entire `Bundle`) -- both silently dropped the same way by a
         * MIGRATED screen's intent, and `Intent` exposes both. Deliberately NOT a bare `putExtra`
         * prefix match (which would also match unrelated identifiers merely starting with those
         * characters, e.g. a hypothetical `putExtraValidator(...)`): the `s?` alternation matches
         * only the two real `Intent` method names, and every use site additionally requires the
         * immediately following `(` (via `\s*\(` after this fragment), so this can only match an
         * actual method call, not a bare word.
         */
        const val PUT_EXTRA_METHODS = "putExtras?"
    }

    /**
     * The `{ ... }` block starting at [text]\[openBraceIndex\] (which must be `'{'`), matched by
     * brace-balance rather than a fixed window, since an `apply` block can itself contain nested
     * braces (e.g. a lambda argument to one of its own calls). Returns `null` if [openBraceIndex]
     * is not a `'{'`, or the braces never balance before the file ends (truncated/malformed input --
     * treated as "no block found" rather than a false positive).
     */
    private fun balancedBraceBlock(text: String, openBraceIndex: Int): String? {
        if (text.getOrNull(openBraceIndex) != '{') return null
        var depth = 0
        for (i in openBraceIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBraceIndex, i + 1)
                }
            }
        }
        return null
    }

    /**
     * [text] with `//` line comments and `/* ... */` block comments blanked to a space, so the
     * conditional-RHS containment check counts braces in CODE rather than in prose -- a live
     * precedent sits between `LinkControl`'s two branches (`} else { //If an indexed Strong's
     * module is in place ...`), and a comment is exactly where an unbalanced `{` or the word `val`
     * shows up without meaning anything.
     *
     * Known bound, stated rather than hidden: this is a textual strip, not a lexer, so a `//`
     * inside a string literal (a URL, say) blanks the rest of that line. The only consequence is
     * that the containment check may see less text than it should and decline to flag -- the same
     * fail-open direction the rest of this scan's bounds have, never a false positive.
     */
    private fun withoutComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * The index of the `')'` matching [text]\[openParenIndex\] (which must be `'('`), by
     * paren-balance -- same technique as [balancedBraceBlock], needed here so a call's argument
     * list can be located regardless of how it is line-wrapped (see
     * [migratedScreenArgumentIsNeverDroppedByAPutExtra]'s multi-line-call hardening). Returns
     * `null` if [openParenIndex] is not a `'('`, or the parens never balance before the file ends.
     */
    private fun matchingParenIndex(text: String, openParenIndex: Int): Int? {
        if (text.getOrNull(openParenIndex) != '(') return null
        var depth = 0
        for (i in openParenIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
