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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.ReadingResultKind
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Test

/**
 * **The ratchet for reading-host re-typing T8d: every request code classic answers, the flipped host
 * answers too.**
 *
 * T8c added the equivalent ratchet for the `NavResultChannel` family
 * ([ReadingInGraphResultGuardTest]). This is the other family, and it is the one that let two
 * user-facing flows die in silence through 2944 green tests: `NavHostComposeActivity
 * .onActivityResult` began `if (requestCode != ActivityBase.STD_REQUEST_CODE) return`, so THREE of
 * the four request codes classic `MainBibleActivity.onActivityResult` answers went nowhere on the
 * host that actually runs — the workspace switch, the Settings refresh and the document refresh.
 *
 * **The list was DERIVED, and is now frozen.** Until slice 8 the codes were read out of CLASSIC's
 * dispatcher on every run, so a fifth code added to classic failed this guard rather than being
 * quietly dropped by the host a fourth time. Slice 8 deletes `MainBibleActivity`, so there is no
 * classic dispatcher left to read, and nothing can add a code to it: [CLASSIC_REQUEST_CODES] is that
 * derivation's last answer, recorded when the class went (slice 8 F3 ran it once more and it
 * returned exactly these four). It is still NOT the host's own set — the host's
 * `ANSWERED_REQUEST_CODES` is checked AGAINST it — so a code quietly dropped from the host is still
 * caught.
 *
 * **What this guard cannot see**, stated because the previous round's blind spot was the mirror
 * image of this one: an ASYNC request code. `ActivityBase.awaitIntent` allocates a code at or above
 * `ASYNC_REQUEST_CODE_START` and resolves it through `resultByCode`, never through a `when` arm, so
 * no walk of classic's dispatcher can enumerate those. `NavHostComposeActivity.onChooseDocumentDownload`
 * is one such site and is deliberately unpaid (its route is unreachable today); whoever wires
 * `Screen.ChooseDocument` into the graph owes it.
 */
class ReadingHostAnsweredRequestCodeGuardTest {

    private val hostSrc = File(
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
    ).readText()

    private val readingCommandsSrc = File(
        "src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt"
    ).readText()

    @Test fun theScansCanSeeTheirSubjects() {
        // Anti-vacuity: every assertion below is a search over these strings.
        assertTrue(hostSrc.length > 100_000, "NavHostComposeActivity.kt is missing or truncated")
        assertTrue(readingCommandsSrc.length > 5_000, "ReadingCommands.kt is missing or truncated")
    }

    /**
     * **The ratchet.** Every request code classic answers is either in the host's
     * `ANSWERED_REQUEST_CODES` or in [UNANSWERED] with a stated reason. An entry with no reason
     * fails; an entry for a code classic no longer answers fails; an entry for a code the host DOES
     * answer fails — so a later task that pays one of these has to remove the claim rather than
     * leave it standing.
     */
    @Test fun everyCodeClassicAnswersIsAnsweredHereOrAllowlistedWithAReason() {
        val classic = CLASSIC_REQUEST_CODES
        val answered = answeredRequestCodes()
        assertTrue(
            answered.isNotEmpty(),
            "no ANSWERED_REQUEST_CODES set found in NavHostComposeActivity.kt — the guard has lost " +
                "its subject",
        )

        assertEquals(
            emptySet<String>(),
            classic - answered - UNANSWERED.keys,
            "classic MainBibleActivity.onActivityResult answers these request codes and the flipped " +
                "host does not. Every one of them is a result the user's own action produced, " +
                "silently discarded — the T8d defect verbatim. Add an arm, or add the code to " +
                "UNANSWERED with a real reason.",
        )
        assertEquals(
            emptySet<String>(),
            UNANSWERED.keys - classic,
            "these allowlist entries name request codes classic no longer answers — a stale claim " +
                "is how a guard stops seeing its subject",
        )
        assertEquals(
            emptySet<String>(),
            UNANSWERED.keys intersect answered,
            "these codes ARE answered now, so their allowlist reason is false; remove the entry",
        )
        val unreasoned = UNANSWERED.filterValues { it.length < 40 }.keys
        assertEquals(emptySet<String>(), unreasoned, "an allowlist entry must carry a real reason")
    }

    /**
     * …and "answered" must mean answered. A code admitted to `ANSWERED_REQUEST_CODES` and then
     * dropped on the floor by every dispatcher would pass the test above while being the same defect
     * one layer down, so each one has to appear in an arm of the three functions that apply things.
     */
    @Test fun everyAnsweredCodeReachesAnArm() {
        val dispatchers = listOf(
            "private fun applyPendingActivityResult()",
            "private fun applyNonStdActivityResult(pending: PendingActivityResult)",
            "private fun applyReadingReturnWork(requestCode: Int)",
        ).joinToString("\n") { functionBody(hostSrc, it) }

        val unarmed = answeredRequestCodes().filterNot { code ->
            // STD_REQUEST_CODE's arm is the `!=` branch that routes everything else away from it.
            code == "STD_REQUEST_CODE" || dispatchers.contains(code)
        }
        assertEquals(
            emptyList<String>(),
            unarmed,
            "these codes are accepted by onActivityResult and then reach no arm at all: $unarmed",
        )
    }

    /** The early return that WAS the defect must not come back. */
    @Test fun theDispatcherNoLongerDropsEveryNonStdCode() {
        val body = functionBody(
            hostSrc,
            "public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)",
        )
        assertTrue(
            !Regex("""if\s*\(requestCode\s*!=\s*ActivityBase\.STD_REQUEST_CODE\)\s*return""")
                .containsMatchIn(body),
            "the `requestCode != STD_REQUEST_CODE` early return is back: every non-STD result this " +
                "host is handed goes nowhere again. Body was:\n$body",
        )
        assertTrue(
            body.contains("requestCode !in ANSWERED_REQUEST_CODES"),
            "the dispatcher must gate on the derived set, not on one code. Body was:\n$body",
        )
    }

    // ——— the self-launch half: what has no Activity result to arrive at all ————————————————————————

    /**
     * `Screen.Settings` is in `ScreenLauncher.MIGRATED` and the Download screen is a route of this
     * graph, so on this host both launches are `singleTop` self-launches and no result is ever
     * produced. The record is made in the ONE funnel every launch passes through — the same one
     * T8b's `stdRequestCancelIsTheUsers` and T8c's `recordReadingResultRequest` use.
     */
    @Test fun everyLaunchIsOfferedToTheReturnDebtLedger() {
        val body = functionBody(
            hostSrc,
            "override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?)",
        )
        assertTrue(
            body.contains("recordReadingReturnDebt(intent, requestCode)"),
            "the launch funnel no longer records what the reading view will be owed on its way " +
                "back, so a Settings or Download self-launch refreshes nothing. Body was:\n$body",
        )
    }

    /** …and the graph coming back to `reading` is what spends it. */
    @Test fun theGraphReturningToReadingIsWhatSpendsADebt() {
        assertTrue(
            hostSrc.contains("NavController.OnDestinationChangedListener"),
            "nothing listens for the graph's navigation any more, so a recorded debt is never paid",
        )
        assertTrue(
            Regex("""applyReadingReturnDebts\(destination\.route\)""").containsMatchIn(hostSrc),
            "the destination listener no longer offers its route to the ledger",
        )
        assertTrue(
            hostSrc.contains("addOnDestinationChangedListener(onDestinationChanged)") &&
                hostSrc.contains("removeOnDestinationChangedListener(onDestinationChanged)"),
            "the listener must be both registered and unregistered with the composition",
        )
    }

    /**
     * A debt has to survive `recreate()`. `discrete_mode` is one of `RECREATE_ON_CHANGE_KEYS`, and
     * the launcher-alias swap that recreate does NOT perform is precisely what the debt is owed for.
     */
    @Test fun aDebtIsCarriedAcrossARecreate() {
        val saved = functionBody(hostSrc, "override fun onSaveInstanceState(outState: Bundle)")
        assertTrue(
            saved.contains("STATE_RETURN_DEBTS") && saved.contains("readingReturnDebts"),
            "what the reading view is owed is no longer written into the instance state, so a " +
                "settings change that recreates the host loses it. Body was:\n$saved",
        )
        assertTrue(
            Regex("""getIntArray\(STATE_RETURN_DEBTS\)""").containsMatchIn(hostSrc),
            "…and nothing reads it back on the way in",
        )
    }

    // ——— one implementation, not two ——————————————————————————————————————————————————————————————

    /**
     * Both arms classic owned are `ReadingCommands`' now and the host calls them, the shape T8c used
     * for its three. (Classic delegated to the same bodies until slice 8 deleted it.) A second copy
     * in the host is the drift `ApplyChosenVerseDriftTest` exists for, one file over.
     */
    @Test fun theArmsAreSharedRatherThanRetyped() {
        for (applier in listOf("applyWorkspaceChangedResult", "preferenceSettingsChanged")) {
            assertTrue(
                Regex("""internal fun $applier""").containsMatchIn(readingCommandsSrc) ||
                    Regex("""    internal fun $applier""").containsMatchIn(readingCommandsSrc),
                "$applier is no longer implemented in ReadingCommands",
            )
            assertTrue(
                hostSrc.contains("readingCommands.$applier"),
                "NavHostComposeActivity no longer calls the shared $applier",
            )
        }
        assertEquals(
            1,
            Regex("""ABEventBus\.post\(SynchronizeWindowsEvent\(true\)\)""")
                .findAll(readingCommandsSrc + hostSrc).count(),
            "SynchronizeWindowsEvent(true) must be posted from exactly ONE place — a second copy " +
                "means preferenceSettingsChanged was retyped rather than shared",
        )
    }

    /**
     * **The privacy step, which no behaviour test in this repo can watch run.**
     *
     * `CommonUtils.changeAppIconAndName()` ends in `forceStopApp()` — `exitProcess(2)` — as soon as
     * it actually moves a component's enabled state, so a unit test that let it do its job would
     * take the test JVM with it. What can be checked is that it is still IN the shared body and
     * still has exactly one production caller: `discrete_mode` is in
     * `NavHostComposeActivity.RECREATE_ON_CHANGE_KEYS`, and `recreate()` does NOT swap the launcher
     * alias — so if this call ever leaves `preferenceSettingsChanged`, turning on discrete mode
     * stops hiding the app's icon and name and nothing else in the tree notices.
     */
    @Test fun thePrivacyStepIsInTheSharedBody() {
        val body = functionBody(readingCommandsSrc, "internal fun preferenceSettingsChanged()")
        assertTrue(
            body.contains("CommonUtils.changeAppIconAndName()"),
            "the launcher-alias swap is gone from the settings-return body. Body was:\n$body",
        )
        val callers = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
                    .filter { it.contains("changeAppIconAndName()") && !it.contains("fun changeAppIconAndName") }
                    .map { file.name }
            }
            .toList()
        assertEquals(
            listOf("ReadingCommands.kt"),
            callers,
            "changeAppIconAndName must have exactly one production caller, the settings-return " +
                "body -- a second one would mean the swap was retyped rather than shared",
        )
    }

    // ——— the self-launch half, DERIVED from the launch sites ——————————————————————————————————————
    //
    // T8d fix round, review finding 1. `everyAnsweredCodeReachesAnArm` above finds a code's token
    // anywhere in the three dispatchers, so it stayed green when the code was in
    // `ANSWERED_REQUEST_CODES` but had quietly dropped out of `RETURN_TO_READING_REQUEST_CODES` --
    // and for a code whose screen is a SELF-LAUNCH that set is the only thing that makes the arm
    // reachable, because no Activity result ever arrives. Deleting
    // `UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH` from that set left 2964 tests green while the Download
    // return silently stopped refreshing the toolbar again: the batch's signature defect, inside the
    // task whose purpose was to end it. These two tests close it, and they derive rather than list.

    /**
     * **Which codes are self-launch codes is DERIVED from their launch sites, not declared here.**
     *
     * A code belongs in `RETURN_TO_READING_REQUEST_CODES` exactly when some live launch at that code
     * aims an Intent at THIS host — either `NavHostComposeActivity.intentFor(...)` directly, or
     * `ScreenLauncher.intentFor(..., Screen.X)` for an `X` that `ScreenLauncher.MIGRATED` resolves
     * into this graph. `singleTop` then answers with `onNewIntent` and no Activity result is ever
     * produced, so the return to `reading` is the only answer there is.
     *
     * So: move `Screen.Settings` out of `MIGRATED`, or repoint a Download launch at a real Activity,
     * and this guard tells you the set is now wrong — in the direction it is wrong.
     *
     * `STD_REQUEST_CODE` is the one stated exclusion: it carries BOTH self-launches that answer
     * nothing (the menu rows for migrated screens) and real cross-Activity choosers, so "some launch
     * is a self-launch" is true of it and means nothing. `stdRequestCancelIsTheUsers` is the member
     * that reasons about that code, and `ReadingHostActivityResultTest` pins it.
     */
    @Test fun everyReturnToReadingCodeIsDerivedFromItsLaunchSites() {
        val derived = selfLaunchRequestCodes()
        val declared = namedCodesIn("private val RETURN_TO_READING_REQUEST_CODES = setOf(")

        assertTrue(
            derived.isNotEmpty(),
            "no self-launch request code could be derived from any launch site -- the scan has " +
                "stopped seeing its subject, which is worse than no guard",
        )
        assertEquals(
            derived,
            declared,
            "RETURN_TO_READING_REQUEST_CODES must be exactly the codes whose screens this host " +
                "launches AT ITSELF. A code missing from it records no debt, so its arm is never " +
                "reached and the refresh silently stops happening -- with no Activity result to " +
                "fall back on, because a singleTop self-launch never produces one.",
        )
    }

    /** …and each of them has its own arm, so deleting one arm cannot pass unnoticed either. */
    @Test fun everyReturnToReadingCodeHasItsOwnArm() {
        val body = functionBody(hostSrc, "private fun applyReadingReturnWork(requestCode: Int)")
        val unarmed = namedCodesIn("private val RETURN_TO_READING_REQUEST_CODES = setOf(")
            .filterNot { code -> Regex("""IntentHelper\.$code\s*->""").containsMatchIn(body) }
        assertEquals(
            emptyList<String>(),
            unarmed,
            "these self-launch codes record a debt that `applyReadingReturnWork` then does nothing " +
                "with: $unarmed. Body was:\n$body",
        )
    }

    /**
     * Every request code classic answers for which SOME live launch aims at this host.
     *
     * A "launch occurrence" is the token on a line that either calls `startActivityForResult(` or
     * assigns `requestCode =` (single `=`, so classic's own `requestCode ==` comparisons and this
     * host's `when` arms are not mistaken for launches). Its window is that line plus the six
     * before it, which is where the Intent it launches is built at every live site.
     */
    private fun selfLaunchRequestCodes(): Set<String> {
        val migrated = migratedScreens()
        assertTrue(migrated.size >= 10, "ScreenLauncher.MIGRATED scan found only $migrated")
        val codes = CLASSIC_REQUEST_CODES - "STD_REQUEST_CODE"
        val launchLine = Regex("""startActivityForResult\(|requestCode\s*=[^=]""")
        var occurrences = 0
        val selfLaunched = mutableSetOf<String>()
        for (file in File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                if (!launchLine.containsMatchIn(line)) return@forEachIndexed
                val code = codes.firstOrNull { line.contains(it) } ?: return@forEachIndexed
                occurrences++
                val window = lines.subList(maxOf(0, i - 6), i + 1).joinToString("\n")
                val aimedAtThisHost = window.contains("NavHostComposeActivity.intentFor(") ||
                    window.contains("intentFor(this, NavRoutes.") ||
                    migrated.any { window.contains("Screen.$it") }
                if (aimedAtThisHost && !answeredByACollector(window)) selfLaunched += code
            }
        }
        assertTrue(
            occurrences >= 4,
            "only $occurrences launch occurrences found for $codes -- the launch-site scan has " +
                "stopped seeing its subject",
        )
        return selfLaunched
    }

    /**
     * Slice 8 B5: a self-launch whose route has a `ReadingResultKind` is answered by the reading
     * destination's COLLECTOR (T8c/B1), not by the return-to-reading ledger -- the workspace selector at
     * `WORKSPACE_CHANGED` is the one such code. Resolved against the live enum and `NavRoutes` constants
     * by reflection, so a renamed route cannot make this quietly wrong.
     */
    private fun answeredByACollector(window: String): Boolean {
        val viaScreen = ScreenLauncher.MIGRATED.any { (screen, route) ->
            window.contains("Screen.${screen.name}") && ReadingResultKind.forRoute(route) != null
        }
        val viaConstant = Regex("""NavRoutes\.([A-Z][A-Z0-9_]+)\b""").findAll(window).any { m ->
            val route = NavRoutes::class.java.declaredFields.firstOrNull { it.name == m.groupValues[1] }
                ?.apply { isAccessible = true }?.get(null) as? String
            route != null && ReadingResultKind.forRoute(route) != null
        }
        return viaScreen || viaConstant
    }

    /** The `Screen.X` keys `ScreenLauncher.MIGRATED` resolves into this host's graph. */
    private fun migratedScreens(): Set<String> {
        val src = File("src/main/java/net/bible/android/view/ScreenLauncher.kt").readText()
        val marker = "val MIGRATED: Map<Screen, String> = mapOf("
        val at = src.indexOf(marker)
        assertTrue(at >= 0, "ScreenLauncher.MIGRATED was renamed; this guard must follow it")
        val body = withoutComments(balancedParenBlock(src, at + marker.length - 1))
        return Regex("""Screen\.(\w+)\s+to\s""").findAll(body).map { it.groupValues[1] }.toSet()
    }

    /** The request-code names inside a `setOf(` declaration in the host, by simple name. */
    private fun namedCodesIn(marker: String): Set<String> {
        val at = hostSrc.indexOf(marker)
        assertTrue(at >= 0, "'$marker' not found in NavHostComposeActivity.kt")
        val body = balancedParenBlock(hostSrc, at + marker.length - 1)
        return Regex("""([A-Za-z_][A-Za-z0-9_.]*)""").findAll(body)
            .map { it.groupValues[1] }
            .filter { isRequestCodeName(it) }
            .map { it.substringAfterLast('.') }
            .toSet()
    }

    private fun balancedParenBlock(text: String, openIndex: Int): String {
        assertTrue(text[openIndex] == '(', "expected '(' at $openIndex")
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return text.substring(openIndex, i + 1) }
            }
            i++
        }
        error("unbalanced parentheses from $openIndex")
    }

    /** An all-caps trailing segment is a request-code constant; `ActivityResultKind.X` is not. */
    private fun isRequestCodeName(token: String): Boolean =
        token.substringAfterLast('.').let { it.length > 3 && it == it.uppercase() }

    /** The host's own declared set, by simple name. */
    private fun answeredRequestCodes(): Set<String> {
        val marker = "private val ANSWERED_REQUEST_CODES = setOf("
        val at = hostSrc.indexOf(marker)
        if (at < 0) return emptySet()
        val open = at + marker.length - 1
        var depth = 0
        var i = open
        while (i < hostSrc.length) {
            when (hostSrc[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) break }
            }
            i++
        }
        return Regex("""([A-Za-z_][A-Za-z0-9_.]*)""").findAll(hostSrc.substring(open, i))
            .map { it.groupValues[1] }
            .filter { isRequestCodeName(it) }
            .map { it.substringAfterLast('.') }
            .toSet()
    }

    private fun functionBody(src: String, signature: String): String {
        val at = src.indexOf(signature)
        assertTrue(at >= 0, "'$signature' not found -- it was renamed or removed")
        val open = src.indexOf('{', at + signature.length)
        assertTrue(open >= 0, "no body for '$signature'")
        return withoutComments(balancedBraceBlock(src, open) ?: error("unbalanced body for '$signature'"))
    }

    private fun balancedBraceBlock(text: String, openIndex: Int): String? {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '{') return null
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
            i++
        }
        return null
    }

    private fun withoutComments(source: String): String =
        source.lineSequence()
            .filterNot {
                val t = it.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .joinToString("\n")

    private companion object {
        /**
         * Every request code classic `MainBibleActivity.onActivityResult` answered, by SIMPLE name —
         * derived from `MainBibleActivity.onActivityResult` (its `when (requestCode)` arms, its
         * `requestCode ==` comparisons and `MenuCommandHandler`'s predicates) in slice 8 F3, before
         * the F4 deletion; frozen because the class is gone. `REFRESH_DISPLAY_ON_FINISH` is
         * the one that was never a literal in classic's body: it came from following the tail's
         * `mainMenuCommandHandler` predicates into `MenuCommandHandler`.
         */
        val CLASSIC_REQUEST_CODES = setOf(
            "STD_REQUEST_CODE", "WORKSPACE_CHANGED", "REFRESH_DISPLAY_ON_FINISH",
            "UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH",
        )

        /**
         * Request codes classic answers that `NavHostComposeActivity` deliberately does NOT — each
         * with the reason, read and enforced by
         * [everyCodeClassicAnswersIsAnsweredHereOrAllowlistedWithAReason].
         *
         * Empty as of T8d, and that is the state to keep it in: an entry here is a claim that a
         * result the user's own action produced may be thrown away on the host that ships, and the
         * two entries this task deleted before they were ever written were both real, user-visible
         * defects. If a later task adds one, the reason has to say what the user loses and why that
         * is acceptable — not merely that the arm was not ported.
         */
        val UNANSWERED: Map<String, String> = emptyMap()
    }
}
