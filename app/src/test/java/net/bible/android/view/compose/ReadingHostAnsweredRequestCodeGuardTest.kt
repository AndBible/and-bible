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
 * **The derivation is the whole point.** The list of codes is read out of CLASSIC's dispatcher, not
 * out of a hand-kept list here and not out of the host's own set: a fifth code added to classic
 * therefore fails this guard rather than being quietly dropped by the host a fourth time. The two
 * `mainMenuCommandHandler` predicates classic's tail calls are followed into `MenuCommandHandler`,
 * because the code they test (`REFRESH_DISPLAY_ON_FINISH`) is not a literal in classic's body at all
 * — which is exactly the shape of arm a naive scan of the `when` would miss.
 *
 * **What this guard cannot see**, stated because the previous round's blind spot was the mirror
 * image of this one: an ASYNC request code. `ActivityBase.awaitIntent` allocates a code at or above
 * `ASYNC_REQUEST_CODE_START` and resolves it through `resultByCode`, never through a `when` arm, so
 * no walk of classic's dispatcher can enumerate those. `NavHostComposeActivity.onChooseDocumentDownload`
 * is one such site and is deliberately unpaid (its route is unreachable today); whoever wires
 * `Screen.ChooseDocument` into the graph owes it.
 */
class ReadingHostAnsweredRequestCodeGuardTest {

    private val classicSrc = File(
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"
    ).readText()

    private val hostSrc = File(
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
    ).readText()

    private val menuSrc = File(
        "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt"
    ).readText()

    private val readingCommandsSrc = File(
        "src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt"
    ).readText()

    @Test fun theScansCanSeeTheirSubjects() {
        // Anti-vacuity: every assertion below is a search over these three strings.
        assertTrue(classicSrc.length > 50_000, "MainBibleActivity.kt is missing or truncated")
        assertTrue(hostSrc.length > 100_000, "NavHostComposeActivity.kt is missing or truncated")
        assertTrue(menuSrc.length > 5_000, "MenuCommandHandler.kt is missing or truncated")
        assertTrue(
            classicRequestCodes().size >= 4,
            "only ${classicRequestCodes()} were derived from classic's dispatcher — the derivation " +
                "has stopped seeing its subject, which is worse than no guard at all",
        )
    }

    /**
     * **The ratchet.** Every request code classic answers is either in the host's
     * `ANSWERED_REQUEST_CODES` or in [UNANSWERED] with a stated reason. An entry with no reason
     * fails; an entry for a code classic no longer answers fails; an entry for a code the host DOES
     * answer fails — so a later task that pays one of these has to remove the claim rather than
     * leave it standing.
     */
    @Test fun everyCodeClassicAnswersIsAnsweredHereOrAllowlistedWithAReason() {
        val classic = classicRequestCodes()
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
     * Both arms classic owns are `ReadingCommands`' now and classic delegates, the shape T8c used
     * for its three. A second copy on either host is the drift `ApplyChosenVerseDriftTest` exists
     * for, one file over.
     */
    @Test fun theArmsAreSharedRatherThanRetyped() {
        for (applier in listOf("applyWorkspaceChangedResult", "preferenceSettingsChanged")) {
            assertTrue(
                Regex("""internal fun $applier""").containsMatchIn(readingCommandsSrc) ||
                    Regex("""    internal fun $applier""").containsMatchIn(readingCommandsSrc),
                "$applier is no longer implemented in ReadingCommands",
            )
            assertTrue(
                classicSrc.contains("readingCommands.$applier"),
                "MainBibleActivity no longer delegates $applier to the shared implementation — two " +
                    "copies of one body is the drift this batch has already been bitten by twice",
            )
            assertTrue(
                hostSrc.contains("readingCommands.$applier"),
                "NavHostComposeActivity no longer calls the shared $applier",
            )
        }
        assertEquals(
            1,
            Regex("""ABEventBus\.post\(SynchronizeWindowsEvent\(true\)\)""")
                .findAll(readingCommandsSrc + classicSrc + hostSrc).count(),
            "SynchronizeWindowsEvent(true) must be posted from exactly ONE place — a second copy " +
                "means preferenceSettingsChanged was retyped rather than shared",
        )
    }

    // ——— the derivation ——————————————————————————————————————————————————————————————————————————

    /**
     * Every request code classic's dispatcher answers, by SIMPLE name: the arms of its
     * `when (requestCode)`, the codes it compares `requestCode` against directly, and the codes
     * tested by the `mainMenuCommandHandler` predicates its tail calls.
     */
    private fun classicRequestCodes(): Set<String> {
        val body = functionBody(
            classicSrc,
            "public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)",
        )
        val codes = mutableSetOf<String>()

        val whenAt = body.indexOf("when (requestCode)")
        assertTrue(whenAt >= 0, "classic no longer dispatches on `when (requestCode)`")
        val armBlock = balancedBraceBlock(body, body.indexOf('{', whenAt))
            ?: error("classic's `when (requestCode)` block is unreadable")
        Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_.]*)\s*->""").findAll(armBlock).forEach {
            val token = it.groupValues[1]
            if (isRequestCodeName(token)) codes += token.substringAfterLast('.')
        }

        Regex("""requestCode\s*==\s*([A-Za-z_][A-Za-z0-9_.]*)""").findAll(body).forEach {
            val token = it.groupValues[1]
            if (isRequestCodeName(token)) codes += token.substringAfterLast('.')
        }

        // The arms that are NOT literals in classic's body: its tail asks MenuCommandHandler which
        // codes mean "refresh"/"restart", and the answer lives in that class.
        Regex("""mainMenuCommandHandler\.(\w+)\(requestCode\)""").findAll(body).forEach { call ->
            val predicate = functionBody(menuSrc, "fun ${call.groupValues[1]}(requestCode: Int)")
            Regex("""IntentHelper\.([A-Z][A-Z0-9_]+)""").findAll(predicate).forEach { codes += it.groupValues[1] }
        }
        return codes
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
