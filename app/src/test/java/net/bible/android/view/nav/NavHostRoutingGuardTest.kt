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
    fun promptEditResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.PromptEdit)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.promptEdit(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun rawLlmLogResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.RawLlmLog)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.rawLlmLog(), intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun rawLogHistoryResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.RawLogHistory)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_RAW_LOG_HISTORY, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
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
     * **Known, deliberate, NOT fixed** (explicitly out of scope for this hardening, so the next
     * person here does not have to rediscover why):
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
     * uses, or an assignment (typed or not) whose variable later receives a nearby
     * `.putExtra(s)(...)`.
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

                val shapeDescriptions = mutableListOf<String>()
                if (chainedPutExtra) shapeDescriptions.add("chained directly with .putExtra(...)")
                chainedScopeFunctionDescription?.let { shapeDescriptions.add(it) }
                assignedPutExtraName?.let { name ->
                    shapeDescriptions.add("assigned to `$name`, then `$name.putExtra(...)` nearby")
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
