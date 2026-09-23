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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.bible.android.view.activity.nav.ReadingResultKind
import org.junit.Test

/**
 * The ratchet for reading-host re-typing T8c: the wiring that makes the reading view's own launches
 * come back is present, complete, and not quietly weakened later.
 *
 * **Every assertion here is RED on the pre-T8c tree**, which is what makes this the "fails before"
 * half of the task: `ReadingNavGraph.kt` composed no collector, `NavHostComposeActivity.kt` recorded
 * no request, and the whole `readingResultCollectors` list did not exist. That is exactly the state
 * in which nine user-facing flows silently discarded the user's answer while 2903 tests stayed
 * green.
 *
 * A source-text guard is the weaker tool — [ReadingInGraphResultTest] is what proves the mechanism
 * BEHAVES — but it is the only one that can see the two things a behaviour test cannot: that a
 * LATER channel is wired too ([everyKindHasACollector]), and that the bookmark delivery keeps three
 * properties no unit test outside a launched host can observe
 * ([theInGraphBookmarkDeliveryBuildsOneIntentAndAliasesIt] and its two siblings).
 */
class ReadingInGraphResultGuardTest {

    private val graphSrc = File(
        "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/nav/ReadingNavGraph.kt"
    ).readText()

    private val hostSrc = File(
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
    ).readText()

    @Test fun theScansCanSeeTheirSubjects() {
        // Anti-vacuity: every assertion below is a search over these two strings.
        assertTrue(graphSrc.length > 4_000, "ReadingNavGraph.kt is missing or truncated")
        assertTrue(hostSrc.length > 100_000, "NavHostComposeActivity.kt is missing or truncated")
        assertTrue(ReadingResultKind.entries.size >= 5, "the kind enum lost entries")
    }

    // ——— the destination's half ———————————————————————————————————————————————————————————————————

    /**
     * The reading arm must COMPOSE its collectors. This is the one line whose removal reproduces the
     * whole defect, and [ReadingInGraphResultTest.theArmIsWhatCollects] is its behavioural twin.
     */
    @Test fun theReadingArmComposesItsCollectors() {
        assertTrue(
            Regex("""for\s*\(\s*collector\s+in\s+deps\.results\s*\)""").containsMatchIn(graphSrc),
            "the reading destination no longer composes deps.results -- every answer its own " +
                "launches produce goes back into a void, which is the T8c defect verbatim",
        )
        assertTrue(
            graphSrc.contains("collector.Collect()"),
            "the loop no longer calls Collect()",
        )
    }

    /**
     * The collector must CONSUME, not merely read. `consume()` clears the channel in the same breath
     * as reading it; a `pending.value` read would leave the answer in place and re-apply it on every
     * later composition of this destination — and applying a label set twice is not idempotent.
     */
    @Test fun theCollectorClearsTheChannelAsItReadsIt() {
        assertTrue(graphSrc.contains("resultChannel.pending.collectAsState()"), "no pending collection")
        assertTrue(graphSrc.contains("resultChannel.consume()"), "the channel is read without being cleared")
    }

    /**
     * The GATE. Without `awaiting()` the collector would take whatever was pending, including an
     * answer a sibling arm asked for — the hazard `WorkspaceNavGraph`'s `awaitingHideLabels` comment
     * states for the shared `manageLabelsResults` channel. Reading it BEFORE `consume()` is part of
     * the shape: `apply` clears the host's record, so asking afterwards would always answer "no".
     */
    @Test fun theCollectorIsGatedAndReadsTheGateFirst() {
        val gate = graphSrc.indexOf("val claimed = awaiting()")
        val consume = graphSrc.indexOf("resultChannel.consume()")
        assertTrue(gate >= 0, "the collector is no longer gated on having asked")
        assertTrue(consume >= 0)
        assertTrue(gate < consume, "the gate must be read before the channel is cleared")
        assertTrue(
            graphSrc.contains("dropUnclaimed(result)"),
            "Ruling D: an answer nobody asked for must be reported, not swallowed",
        )
    }

    // ——— the host's half —————————————————————————————————————————————————————————————————————————

    /**
     * Requests are recorded in the ONE funnel every launch passes through. `ActivityBase.awaitIntent`
     * calls `startActivityForResult` too, which is why hooking the 3-argument override catches all
     * nine call sites without touching any of them.
     */
    @Test fun everyLaunchIsRecordedInTheStartActivityForResultFunnel() {
        val body = functionBody(hostSrc, "override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?)")
        assertTrue(
            body.contains("recordReadingResultRequest(intent, requestCode)"),
            "the launch funnel no longer records what the reading view is waiting for; every " +
                "in-graph answer would then be dropped as unclaimed. Body was:\n$body",
        )
    }

    /**
     * **Completeness**, derived from the enum rather than from a hand-kept list: a kind added to
     * [ReadingResultKind] without a collector beside it would be recorded and then never collected,
     * which is the defect again for that one screen. This is the assertion that makes the guard a
     * ratchet rather than a snapshot.
     */
    @Test fun everyKindHasACollector() {
        val missing = ReadingResultKind.entries.filterNot { kind ->
            Regex("""collectorFor\(\w+, ReadingResultKind\.${kind.name}\)""").containsMatchIn(hostSrc)
        }
        assertEquals(
            emptyList<ReadingResultKind>(),
            missing,
            "these result kinds are recorded as requests but no collector reads their channel, so " +
                "the answer would be dropped as unclaimed: $missing",
        )
    }

    /**
     * **The ratchet one level UP, and the one this task was missing.**
     * [everyKindHasACollector] derives from [ReadingResultKind] — so a kind added without a
     * collector fails. But nothing derived [ReadingResultKind] itself from the host's channel SET,
     * and `NavResultChannelGuardTest` has no such test either. A SIXTH result-producing route
     * reachable from the reading view would therefore be recorded nowhere, collected nowhere, and
     * the suite would be green again: the exact shape of the defect T8c exists to fix, and the exact
     * way the first five went unnoticed through 2903 green tests.
     *
     * So: every `NavResultChannel` field the host declares is either handed to a `collectorFor(...)`
     * or listed in [NOT_COLLECTED] **with a stated reason**. The allowlist is not a mute: an entry
     * with no reason fails, an entry naming a field that no longer exists fails, and an entry for a
     * channel that IS now collected fails — so when a later task gives one of these a consumer, the
     * guard makes it remove the entry rather than leaving a stale claim behind.
     *
     * A source scan is the weak tool here, as everywhere in this class. What makes it a ratchet
     * anyway is that it is a scan for something a new channel CANNOT avoid declaring: the field.
     */
    @Test fun everyHostChannelIsEitherCollectedOrAllowlistedWithAReason() {
        val declared = Regex("""private val (\w+) = NavResultChannel<""")
            .findAll(hostSrc).map { it.groupValues[1] }.toSet()
        assertTrue(
            declared.size >= 12,
            "only ${declared.size} NavResultChannel fields found in NavHostComposeActivity.kt -- " +
                "the field declaration was respelled and this guard has stopped seeing its subject",
        )

        val collected = declared.filter { field ->
            Regex("""collectorFor\($field, ReadingResultKind\.\w+\)""").containsMatchIn(hostSrc)
        }.toSet()
        assertEquals(
            ReadingResultKind.entries.size, collected.size,
            "one collectorFor line per ReadingResultKind; collected = $collected",
        )

        assertEquals(
            emptySet<String>(),
            declared - collected - NOT_COLLECTED.keys,
            "these NavResultChannel fields are neither collected by the reading destination nor " +
                "allowlisted with a reason. If the reading view can reach the destination that " +
                "fills one, the user's answer is being dropped in silence -- add a " +
                "ReadingResultKind and a collectorFor. If it cannot, add it to NOT_COLLECTED and " +
                "say why.",
        )
        assertEquals(
            emptySet<String>(),
            NOT_COLLECTED.keys - declared,
            "these allowlist entries name channels that no longer exist -- a stale claim is how a " +
                "guard stops seeing its subject",
        )
        assertEquals(
            emptySet<String>(),
            NOT_COLLECTED.keys intersect collected,
            "these channels ARE collected now, so their allowlist reason is false; remove the entry",
        )
        val unreasoned = NOT_COLLECTED.filterValues { it.length < 40 }.keys
        assertEquals(
            emptySet<String>(),
            unreasoned,
            "an allowlist entry must carry a real reason, not a placeholder: $unreasoned",
        )
    }

    /** …and the collectors are actually handed to the destination. */
    @Test fun theCollectorsAreHandedToTheReadingDestination() {
        assertTrue(
            hostSrc.contains("results = readingResultCollectors"),
            "ReadingNavDeps no longer receives the collectors, so the arm composes an empty list",
        )
    }

    /**
     * An async code goes back to `ActivityBase`'s own `awaitIntent` bookkeeping; `STD_REQUEST_CODE`
     * has no continuation and is applied through the shared arms. Getting this branch backwards
     * would hand an `awaitIntent` caller nothing and run a chooser apply nobody asked for.
     */
    @Test fun theDeliveryPicksTheChannelTheCallerAskedUnder() {
        val body = functionBody(hostSrc, "private fun deliverReadingResult(requestCode: Int, resultCode: Int, data: Intent?)")
        assertTrue(
            body.contains("if (requestCode != ActivityBase.STD_REQUEST_CODE)") &&
                body.contains("onActivityResult(requestCode, resultCode, data)"),
            "an awaitIntent caller must be resumed through ActivityBase's own channel. Body was:\n$body",
        )
        assertTrue(
            body.contains("applyInGraphStdResult(resultCode, data)"),
            "a STD_REQUEST_CODE answer must be applied through the shared arms. Body was:\n$body",
        )
    }

    /**
     * The shared arms, not a second implementation of any of them. `ReadingCommands` holds one copy
     * of each and `MainBibleActivity` delegates to the same three (`ApplyChosenVerseDriftTest`
     * pins that side).
     */
    @Test fun theStdArmsDelegateToTheSharedAppliers() {
        val body = functionBody(hostSrc, "private fun applyInGraphStdResult(resultCode: Int, data: Intent?)")
        for (call in listOf(
            "readingCommands.applyChosenMyDocumentPage(extras)",
            "readingCommands.applyChosenMyDocument(extras)",
            "readingCommands.applyChosenPassageResult(kind, extras)",
        )) {
            assertTrue(body.contains(call), "the in-graph STD dispatcher no longer calls `$call`. Body was:\n$body")
        }
    }

    // ——— the bookmark delivery's three properties ————————————————————————————————————————————————
    //
    // `NavResultChannelGuardTest` pins these on the `bookmarkResults` EXIT lambda -- by reading that
    // lambda's body as text, which is why the in-graph delivery could not simply share a helper with
    // it. The duplication is deliberate and named in the production comment; these three tests are
    // what stops the copy drifting, and they are the drift guard that comment promises.

    /** ONE Intent is built: the history list and the delivery get the same object. */
    @Test fun theInGraphBookmarkDeliveryBuildsOneIntentAndAliasesIt() {
        val body = inGraphBookmarkDeliveryBody()
        assertEquals(
            1,
            Regex("""\bval\s+resultIntent\s*=""").findAll(body).count(),
            "the in-graph bookmark delivery must bind its Intent exactly once. Body was:\n$body",
        )
        assertEquals(
            1,
            Regex("""NavResultIntents\.forBookmarks\(""").findAll(body).count(),
            "a second, structurally-equal Intent is not the same Intent, and HistoryManager keeps " +
                "the instance it is handed. Body was:\n$body",
        )
        assertTrue(
            Regex("""addHistoryItem\(\s*null\s*,\s*resultIntent\s*\)""").containsMatchIn(body),
            "the IDENTIFIER must reach the history list, not a freshly-built Intent. Body was:\n$body",
        )
    }

    /** Classic stores the history item BEFORE the result reaches the reading view. */
    @Test fun theInGraphBookmarkDeliveryStoresHistoryFirst() {
        val body = inGraphBookmarkDeliveryBody()
        val history = body.indexOf("addHistoryItem(")
        val deliver = body.indexOf("deliverReadingResult(")
        assertTrue(history >= 0 && deliver >= 0, "a call is missing entirely. Body was:\n$body")
        assertTrue(history < deliver, "history must be stored first. Body was:\n$body")
    }

    /**
     * Classic's `try`/`catch`, which the EXIT lambda has already lost once unnoticed (see the
     * `bookmarkResults` field's own comment). A copy that lost it would be a crash on the main
     * thread where classic showed a toast.
     */
    @Test fun theInGraphBookmarkDeliveryKeepsClassicsTryCatch() {
        val body = inGraphBookmarkDeliveryBody()
        assertTrue(body.contains("try {"), "no try at all. Body was:\n$body")
        assertTrue(
            Regex("""catch\s*\(""").containsMatchIn(body) &&
                body.contains("error_occurred") &&
                body.contains("Log.e("),
            "the catch must still log and toast error_occurred. Body was:\n$body",
        )
    }

    /** The `{ … }` handed to `collectorFor(bookmarkResults, …)`, comments stripped. */
    private fun inGraphBookmarkDeliveryBody(): String {
        val marker = "collectorFor(bookmarkResults, ReadingResultKind.Bookmarks) { result, code ->"
        val start = hostSrc.indexOf(marker)
        assertTrue(
            start >= 0,
            "cannot find the in-graph bookmark delivery; if it was renamed this guard must follow " +
                "it rather than be deleted",
        )
        val block = balancedBraceBlock(hostSrc, hostSrc.indexOf('{', start + marker.length - 30))
        return withoutComments(assertNotNull(block))
    }

    // ——— helpers —————————————————————————————————————————————————————————————————————————————————

    private fun functionBody(src: String, signature: String): String {
        val at = src.indexOf(signature)
        assertTrue(at >= 0, "'$signature' not found -- it was renamed or removed")
        val open = src.indexOf('{', at + signature.length)
        assertTrue(open >= 0, "no body for '$signature'")
        return withoutComments(assertNotNull(balancedBraceBlock(src, open)))
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
         * The host's `NavResultChannel` fields that the reading destination deliberately does NOT
         * collect, each with the reason -- read and enforced by
         * [everyHostChannelIsEitherCollectedOrAllowlistedWithAReason].
         *
         * Two shapes only, and both are checkable claims rather than opinions: either the channel's
         * destination is reached from INSIDE the graph by a parent that collects it (the file and
         * line are named, so the claim can be followed), or nothing routes to its destination from
         * the reading view at all and its `exitWithResult` is a hard `error(...)` that would fail
         * loudly if that changed.
         *
         * If a later task gives the reading view an edge into one of these, delete the entry and add
         * a `ReadingResultKind` -- the guard will not let the entry stay.
         */
        val NOT_COLLECTED: Map<String, String> = mapOf(
            "labelEditResults" to
                "the label EDITOR is only ever a child of ManageLabels, whose arm collects it " +
                    "(BookmarkNavGraph.kt:641). The reading view never opens it directly -- " +
                    "Screen.LabelEdit is deliberately absent from ScreenLauncher.MIGRATED and its " +
                    "route cannot be built without a payload only that arm has.",
            "repositoryEditorResults" to
                "the custom-repository editor is registered only as a child of CustomRepositories, " +
                    "whose arm collects it (DownloadNavGraph.kt:529). Its exitWithResult is a hard " +
                    "error(...) precisely because it has no external entry.",
            "textSettingsResults" to
                "the text-display settings editor publishes to the workspace SELECTOR arm, which " +
                    "collects it in-graph (WorkspaceNavGraph.kt:609). The reading view opens " +
                    "TextDisplaySettingsComposeActivity, a separate Activity, not this route.",
        )
    }
}
