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
     * screen currently in [ScreenLauncher.MIGRATED]:
     *  1. `ScreenLauncher.intentFor(..., Screen.X).putExtra(...)` — chained directly.
     *  2. `ScreenLauncher.intentFor(..., Screen.X).apply { ... putExtra(...) ... }` — chained via
     *     `apply` (the block is captured by brace-balance, not by a fixed-width window, since an
     *     `apply` block can itself contain nested braces).
     *  3. `val name = ScreenLauncher.intentFor(..., Screen.X)` followed nearby by
     *     `name.putExtra(...)` — the shape that actually broke `openPromptEditor`; a scan limited to
     *     shapes 1-2 would have missed it.
     *
     * **Known, deliberate bound** (stated rather than overclaimed, matching this file's sibling
     * scans in [ClassicRemovalScan]'s own kdoc style): shape 3's forward look for `name.putExtra(`
     * is windowed to the next [ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS] characters, not the rest of the
     * file. Every real occurrence in this repo today has its `.putExtra` within a few lines of the
     * assignment; an unbounded look would risk a same-named variable in a LATER, unrelated function
     * reading as a false offender.
     */
    @Test
    fun migratedScreenArgumentIsNeverDroppedByAPutExtra() {
        val migratedScreenNames = ScreenLauncher.MIGRATED.keys.map { it.name }
        assertTrue(migratedScreenNames.isNotEmpty(), "ScreenLauncher.MIGRATED is empty -- this scan would pass vacuously")

        val offenders = mutableListOf<String>()
        for (file in ClassicRemovalScan.appSources()) {
            val text = file.readText()
            val path = file.path.replace('\\', '/')
            for (screenName in migratedScreenNames) {
                val marker = "Screen.$screenName)"
                var searchFrom = 0
                while (true) {
                    val markerIndex = text.indexOf(marker, searchFrom)
                    if (markerIndex < 0) break
                    val afterMarker = markerIndex + marker.length
                    searchFrom = afterMarker

                    // Only a genuine ScreenLauncher.intentFor(..., Screen.X) call -- i.e. no
                    // closing paren between the call's opening "(" and this "Screen.X)" (there is
                    // none in this repo today; intentFor's `screen` parameter is always last).
                    val callStart = text.lastIndexOf("ScreenLauncher.intentFor(", markerIndex)
                    if (callStart < 0 || text.substring(callStart, markerIndex).contains(')')) continue

                    val tail = text.substring(afterMarker)
                    val chainedPutExtraMatch = Regex("""^\s*\.putExtra\s*\(""").find(tail)
                    val chainedApplyMatch = Regex("""^\s*\.apply\s*\{""").find(tail)
                    when {
                        chainedPutExtraMatch != null ->
                            offenders.add("$path: Screen.$screenName chained directly with .putExtra(...)")

                        chainedApplyMatch != null -> {
                            val braceIndex = afterMarker + chainedApplyMatch.range.last
                            val block = balancedBraceBlock(text, braceIndex)
                            if (block != null && block.contains("putExtra(")) {
                                offenders.add(
                                    "$path: Screen.$screenName chained with .apply { ... putExtra(...) ... }",
                                )
                            }
                        }

                        else -> {
                            // Shape 3: is this "Screen.X)" the tail of an assignment's RHS -- i.e.
                            // does "<name> = " immediately precede "ScreenLauncher.intentFor("?
                            val beforeCall = text.substring(0, callStart)
                            val assignedTo = Regex("""(\w+)\s*=\s*$""").find(beforeCall)
                            if (assignedTo != null) {
                                val name = assignedTo.groupValues[1]
                                val window = tail.take(ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS)
                                if (Regex("""\b${Regex.escape(name)}\.putExtra\s*\(""").containsMatchIn(window)) {
                                    offenders.add(
                                        "$path: Screen.$screenName assigned to `$name`, " +
                                            "then `$name.putExtra(...)` nearby",
                                    )
                                }
                            }
                        }
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

    /** See [migratedScreenArgumentIsNeverDroppedByAPutExtra]'s kdoc, shape 3's known bound. */
    private companion object {
        const val ASSIGN_PUT_EXTRA_LOOKAHEAD_CHARS = 600
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
}
