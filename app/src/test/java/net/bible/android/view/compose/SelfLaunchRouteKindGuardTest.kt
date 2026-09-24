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

import java.io.File
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F53 follow-up. `NavHostComposeActivity.navigateInsteadOfSelfLaunch` intercepts a self-launch only
 * when its route maps to a `ReadingResultKind`, because a route no collector answers would leave
 * `awaitIntent`'s deferred spent by a synthetic `RESULT_CANCELED` (see that function's kdoc). That
 * fall-through is silent, so this guard makes the next unanswered self-launch fail the build instead
 * of failing on a device.
 *
 * **What counts as "self-launched".** `navigateInsteadOfSelfLaunch` is an override of
 * `startActivityForResult` that only runs when the call executes ON a `NavHostComposeActivity`
 * instance -- it can never see, let alone intercept, an `awaitIntent` issued by a *different*
 * Activity (`StartupActivity` extends an `ActivityBase` subtree of its own, never
 * `NavHostComposeActivity`). Slice 8 deleted the other three of the original four
 * (`StartupComposeActivity`, `ChooseDocumentComposeActivity`, `TextDisplaySettingsComposeActivity`)
 * -- their screens are destinations of the one host now, so an `awaitIntent` naming their routes IS
 * self-launch-shaped and is no longer excluded. `StartupActivity` launches
 * this host as a genuinely fresh instance and gets a real platform result back, so its
 * `NavRoutes.download(...)` self-launch -- unanswered by design (`ReadingResultKind`'s own kdoc:
 * "download, settings, search ... silent on purpose") -- is structurally not F53-shaped and is
 * excluded by name, the same way `ActivityResultDispatchGuardTest.everyKindIsProducedBySomeScreen`
 * excludes `ActivityResultKind.kt`/`MainBibleActivity.kt`.
 *
 * **Correlation, not file co-occurrence.** A route reaches `navigateInsteadOfSelfLaunch` only when
 * it is the actual argument of an `awaitIntent(...)` call -- inline, via the local variable it is
 * assigned to (awaited a few lines later, every live example in this tree), or handed one call away
 * to a same-file helper that itself awaits its own `Intent` parameter (`CurrentGeneralBookPage
 * .awaitChosenKey`). A file merely *containing* `awaitIntent` elsewhere is not enough: `BibleView
 * .openLink`'s `SCHEME_DOWNLOAD` arm and `MenuCommandHandler.handleMenuRequest`'s `downloadButton`
 * arm both build a `NavRoutes.download(...)` self-launch in a file/function that also has an
 * unrelated `awaitIntent` call for a *different* route, but dispatch their own intent through plain
 * `startActivityForResult` -- never through `awaitIntent` -- so they cannot hit F53 either.
 *
 * **The indirection through `ScreenLauncher` too.** `ScreenLauncher.intentFor(context, Screen.X)`
 * builds exactly `NavHostComposeActivity.intentFor(context, route)` internally when `Screen.X` is
 * in [ScreenLauncher.MIGRATED] -- so an awaited `ScreenLauncher.intentFor(host, Screen.X)` is just
 * as self-launch-shaped as the direct spelling, and the direct-spelling-only scan (this guard's
 * fix-round-1 predecessor) could not see it. Resolved at RUNTIME off the live [ScreenLauncher
 * .MIGRATED] map, not a hand-duplicated source-text mapping, so a screen migrating in or out cannot
 * make this guard quietly wrong. A `Screen.X` this scan cannot even parse out of an awaited call, or
 * a name that is not a real [Screen] constant, fails LOUD as an offender rather than being skipped
 * (no current call site hits this -- verified below -- but a future refactor that awaits a
 * `ScreenLauncher.intentFor` built from a variable rather than a literal `Screen.X` must not go
 * unseen).
 */
class SelfLaunchRouteKindGuardTest {

    private val mainSrc = File("src/main/java")

    /** The route bases `ReadingResultKind` covers, spelled from `NavRoutes` so a rename cannot rot this. */
    private val answeredBases = setOf(
        NavRoutes.MANAGE_LABELS_PATTERN.substringBefore('?'),
        NavRoutes.MY_DOCUMENT_PAGES_PATTERN.substringBefore('?'),
        NavRoutes.READING_PROGRESS_PATTERN.substringBefore('?'),
        NavRoutes.BOOKMARKS_PATTERN.substringBefore('?'),
        NavRoutes.MY_DOCUMENTS_PATTERN.substringBefore('?'),
        NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.substringBefore('?'),
        NavRoutes.CHOOSE_DOCUMENT_PATTERN.substringBefore('?'),
        NavRoutes.CHOOSE_GENERAL_BOOK_KEY,
        NavRoutes.CHOOSE_MAP_KEY,
        NavRoutes.CHOOSE_DICTIONARY_WORD,
        NavRoutes.WORKSPACE_SELECTOR,
    )

    /** `NavRoutes.manageLabels(` -> the builder name; mapped to its pattern's base below. */
    private val builderToBase = mapOf(
        "manageLabels" to NavRoutes.MANAGE_LABELS_PATTERN.substringBefore('?'),
        "myDocumentPages" to NavRoutes.MY_DOCUMENT_PAGES_PATTERN.substringBefore('?'),
        "readingProgress" to NavRoutes.READING_PROGRESS_PATTERN.substringBefore('?'),
        "bookmarks" to NavRoutes.BOOKMARKS_PATTERN.substringBefore('?'),
        "myDocuments" to NavRoutes.MY_DOCUMENTS_PATTERN.substringBefore('?'),
        "gridChoosePassage" to NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.substringBefore('?'),
        "chooseDocument" to NavRoutes.CHOOSE_DOCUMENT_PATTERN.substringBefore('?'),
    )

    /** Slice 8 B4: argument-free routes spelled as constants -- `NavRoutes.CHOOSE_GENERAL_BOOK_KEY` has no `(`. */
    private val constToBase = mapOf(
        "CHOOSE_GENERAL_BOOK_KEY" to NavRoutes.CHOOSE_GENERAL_BOOK_KEY,
        "CHOOSE_MAP_KEY" to NavRoutes.CHOOSE_MAP_KEY,
        "CHOOSE_DICTIONARY_WORD" to NavRoutes.CHOOSE_DICTIONARY_WORD,
        "WORKSPACE_SELECTOR" to NavRoutes.WORKSPACE_SELECTOR,
    )
    private val navRoutesConst = Regex("""NavRoutes\.([A-Z][A-Z0-9_]+)\b""")

    /** See the class kdoc's "What counts as self-launched" -- these can never BE the host. */
    private val externalLauncherFiles = setOf(
        "StartupActivity.kt",
    )

    /**
     * Route base [ScreenLauncher.MIGRATED] resolves each migrated [Screen] to -- built off the
     * LIVE map, not a hand-copied one, so a screen migrating in or out cannot make this guard
     * quietly wrong (see the class kdoc's "indirection through ScreenLauncher").
     */
    private val migratedScreenBase: Map<String, String> =
        ScreenLauncher.MIGRATED.entries.associate { (screen, route) -> screen.name to route.substringBefore('?') }

    private val funStart = Regex("""(?m)^[ \t]*(?:\w+\s+)*fun\s+(\w+)""")
    private val navRoutesCall = Regex("""NavRoutes\.(\w+)\(""")
    private val screenConst = Regex("""Screen\.(\w+)""")
    private val assignment = Regex("""(?:val\s+|var\s+)?(\w+)\s*=\s*$""")
    private val helperArg = Regex("""(\w+)\(\s*(?:[\w.]+\s*,\s*)*$""")

    @Test
    fun everySelfLaunchedAwaitIntentNamesARouteACollectorAnswers() {
        require(mainSrc.isDirectory) { "src/main/java not found -- SelfLaunchRouteKindGuardTest scans it" }
        require(migratedScreenBase.isNotEmpty()) {
            "ScreenLauncher.MIGRATED resolved empty -- the runtime lookup this guard relies on is broken"
        }

        val offenders = mutableListOf<String>()
        var selfLaunchSitesSeen = 0

        mainSrc.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            if (file.name in externalLauncherFiles) return@forEach

            val text = file.readText()
            // Comment-stripped, so a kdoc quoting the old broken shape is not a finding.
            val code = text.lineSequence()
                .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                .joinToString("\n")
            if (!code.contains("awaitIntent")) return@forEach

            val funStarts = funStart.findAll(code).map { it.range.first to it.groupValues[1] }.toList()
            fun chunkFor(pos: Int): String {
                val idx = funStarts.indexOfLast { it.first <= pos }
                val start = if (idx >= 0) funStarts[idx].first else 0
                val end = funStarts.firstOrNull { it.first > start }?.first ?: code.length
                return code.substring(start, end)
            }
            // Same-file helpers that await their own Intent parameter one call removed, e.g.
            // `private suspend fun awaitChosenKey(context: ActivityBase, intent: Intent) { ...
            // context.awaitIntent(intent) ... }`. A call that hands our match to one of these is
            // awaited just as surely as an inline `awaitIntent(...)`.
            val awaitingHelperNames = funStarts.mapNotNull { (start, name) ->
                val end = funStarts.firstOrNull { it.first > start }?.first ?: code.length
                name.takeIf { code.substring(start, end).contains("awaitIntent(") }
            }.toSet()

            /** Every call opening with [calleePrefix] that is genuinely reached by an `awaitIntent`. */
            fun awaitedCalls(calleePrefix: String): List<String> {
                val calls = mutableListOf<String>()
                var searchFrom = 0
                while (true) {
                    val callStart = code.indexOf("$calleePrefix(", searchFrom)
                    if (callStart == -1) break
                    var depth = 0
                    var i = callStart + calleePrefix.length
                    var callEnd = -1
                    while (i < code.length) {
                        when (code[i]) {
                            '(' -> depth++
                            ')' -> { depth--; if (depth == 0) { callEnd = i; break } }
                        }
                        i++
                    }
                    if (callEnd == -1) break // malformed source; stop scanning this file defensively
                    searchFrom = callEnd + 1

                    val before = code.substring(maxOf(0, callStart - 150), callStart).trimEnd()
                    val inlineAwaited = before.endsWith("awaitIntent(")
                    val assignedVar = assignment.find(before)?.groupValues?.get(1)
                    val varAwaited = assignedVar != null &&
                        chunkFor(callStart).contains("awaitIntent(" + assignedVar)
                    val helperName = helperArg.find(before)?.groupValues?.get(1)
                    val helperAwaited = helperName != null && helperName in awaitingHelperNames

                    // Slice 8 C3: a function that FAILS FAST on the nav host before awaiting cannot be an
                    // F53 self-launch -- `BackupControl.awaitBackupFromAnotherActivity` is the one today.
                    val crossActivityOnly =
                        Regex("""check\(\s*\w+\s*!is\s+NavHostComposeActivity\s*\)""").containsMatchIn(chunkFor(callStart))
                    if (crossActivityOnly) continue
                    if (inlineAwaited || varAwaited || helperAwaited) {
                        calls += code.substring(callStart, callEnd + 1)
                    }
                }
                return calls
            }

            awaitedCalls("NavHostComposeActivity.intentFor").forEach { callText ->
                val builder = navRoutesCall.find(callText)?.groupValues?.get(1)
                val constant = if (builder == null) navRoutesConst.find(callText)?.groupValues?.get(1) else null
                if (builder == null && constant == null) return@forEach
                selfLaunchSitesSeen++
                val base = if (builder != null) builderToBase[builder] else constToBase[constant]
                if (base == null || base !in answeredBases) {
                    offenders += "${file.path}: NavRoutes.${builder ?: constant} is self-launched in a file " +
                        "that uses awaitIntent, but no ReadingResultKind answers that route -- " +
                        "navigateInsteadOfSelfLaunch will fall through and the awaited deferred " +
                        "will be spent by a synthetic RESULT_CANCELED (finding F53). Add a " +
                        "ReadingResultKind + collector for it, or do not await this launch."
                }
            }

            awaitedCalls("ScreenLauncher.intentFor").forEach { callText ->
                val screenName = screenConst.find(callText)?.groupValues?.get(1)
                if (screenName == null) {
                    // An awaited ScreenLauncher.intentFor(...) whose Screen argument is not a literal
                    // `Screen.X` -- e.g. built from a variable -- is exactly the shape this scan
                    // cannot classify. Fail loud rather than silently letting it through unseen.
                    offenders += "${file.path}: an awaited ScreenLauncher.intentFor(...) call's Screen " +
                        "argument is not a literal `Screen.X` this guard can read -- it cannot tell " +
                        "whether this is an unanswered self-launch (F53). Make the argument a literal " +
                        "Screen constant, or extend this guard to resolve it."
                    return@forEach
                }
                val screen = try {
                    Screen.valueOf(screenName)
                } catch (e: IllegalArgumentException) {
                    offenders += "${file.path}: awaited ScreenLauncher.intentFor(..., Screen.$screenName) " +
                        "-- \"$screenName\" is not a real Screen constant (renamed?); this guard cannot " +
                        "resolve it against ScreenLauncher.MIGRATED. Fix the guard's Screen parsing."
                    return@forEach
                }
                val base = migratedScreenBase[screen.name]
                    // Not in ScreenLauncher.MIGRATED: ScreenLauncher.intentFor resolves this to a
                    // DIFFERENT Activity via targetFor, so it is not a self-launch of
                    // NavHostComposeActivity at all -- structurally safe, not this guard's domain.
                    ?: return@forEach
                selfLaunchSitesSeen++
                if (base !in answeredBases) {
                    offenders += "${file.path}: ScreenLauncher.intentFor(..., Screen.$screenName) is " +
                        "self-launched (Screen.$screenName is in ScreenLauncher.MIGRATED, so it resolves " +
                        "to NavHostComposeActivity.intentFor(context, \"$base...\")) in a file that uses " +
                        "awaitIntent, but no ReadingResultKind answers that route -- " +
                        "navigateInsteadOfSelfLaunch will fall through and the awaited deferred will be " +
                        "spent by a synthetic RESULT_CANCELED (finding F53). Add a ReadingResultKind + " +
                        "collector for it, or do not await this launch."
                }
            }
        }

        // Anti-vacuity: the known self-launch sites must be visible to this scan.
        assertTrue(
            "the scan found no self-launched intentFor at all -- the regex or the path is wrong, " +
                "not the tree (six such sites existed on 2026-09-18)",
            selfLaunchSitesSeen >= 5,
        )
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
