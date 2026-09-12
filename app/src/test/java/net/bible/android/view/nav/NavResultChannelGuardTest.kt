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

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Replaces `NavHostRoutingGuardTest.noGraphNavigatesToTheReadingProgressRoute`, deleted in the same
 * change that made it obsolete -- as that test's own kdoc required ("delete this test as part of
 * that change -- not loosen it").
 *
 * That guard forbade in-graph navigation to the one destination that produced a result, because the
 * result was produced by `finish()`, which is only correct from a start destination. With
 * `NavResultChannel` a destination handles both entries, so the prohibition is gone and what needs
 * guarding is the opposite: that every result-producing arm goes THROUGH the channel, and no arm
 * quietly grows a second exit-with-result path beside it.
 *
 * **Fix round 1, Findings 1+2.** The original version of this class matched a regex
 * (`onFinishWithResult|exitWithResult|finishWith[A-Za-z]*Result`) against `:sharedUi` sources --
 * but none of those identifiers has EVER existed in `:sharedUi`; the two functions this guard was
 * meant to catch a regression of lived only in `:app` (`NavHostComposeActivity`), which this class
 * does not scan. That guard could never go red, so it was decorative, not a ratchet. Replaced with a
 * genuinely POSITIVE guard: [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered] discovers
 * every `NavResultChannel<...>` field declared in `:sharedUi/commonMain` and asserts the ONLY member
 * access on it, anywhere in a `*NavGraph.kt` file, is `.deliver(`; the anti-vacuity half,
 * [readingProgressArmActuallyDeliversThroughTheChannel], asserts the wiring this batch actually
 * added is still present, so the first test cannot pass merely because nobody uses the field at all.
 *
 * **slice 2, Task 5 widened the allowed member set from `deliver` alone to
 * `deliver`/`pending`/`consume`**, and that is a correction rather than a loosening. The original
 * wording ("or calling `.consume()` from an arm rather than letting the channel decide") described
 * a producer reaching around its own channel -- but consuming `pending` is not the producer's move,
 * it is the PARENT's, and it is half of what [net.bible.sharedui.nav.NavResultChannel] exists for:
 * its kdoc says in so many words that a destination entered from inside the graph "publishes to
 * [pending] and pops. The parent arm consumes it once in a `LaunchedEffect`." The `ManageLabels`
 * arm is the first parent there has ever been, so the guard had never had to distinguish the two
 * uses. What is still forbidden is everything else -- most pointedly `publishForTest`, which would
 * be a way to fake a result into a graph, and any future member -- and the pairing is pinned
 * positively by [manageLabelsArmConsumesTheLabelEditChannel] below, so "allowed" does not become
 * "unused".
 *
 * **slice 2, Task 6 extended the walk to `:app`.** The paragraph above notes that the two functions
 * the original (decorative) guard was meant to catch lived only in `NavHostComposeActivity`, "which
 * this class does not scan" -- and the bookmark list's exit is exactly that kind of code: a
 * `NavResultChannel`'s `exitWithResult` lambda, unreachable by any unit test because it can only run
 * inside a launched host, and carrying THREE properties no structural test of
 * `NavResultIntents.forBookmarks` can see (see [theBookmarkExitBuildsOneIntentAndAliasesIt] and its
 * three siblings). So the class now reads that file too. A source-text guard is the weaker tool, but
 * the alternative here is not a stronger test -- it is no test, which is the state that already let
 * this very lambda lose its `try`/`catch` once, unnoticed.
 *
 * Dependency-free on purpose (no `@RunWith(RobolectricTestRunner::class)`): every test here is a
 * plain text walk over `.kt` sources and touches no Android type. The two tests that DO need
 * Robolectric -- `NavResultIntents.forReadingProgress`'s `Intent` extras -- moved out to
 * `net.bible.android.view.activity.nav.NavResultIntentsTest` (fix round 1, Finding 3).
 */
class NavResultChannelGuardTest {

    @Test
    fun everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered() {
        val fieldDeclaration = Regex("""\bval\s+(\w+)\s*:\s*NavResultChannel<""")
        val files = navGraphSources().associateWith { withoutComments(it.readText()) }

        val fields = mutableSetOf<String>()
        for (text in files.values) {
            fieldDeclaration.findAll(text).forEach { fields += it.groupValues[1] }
        }
        assertTrue(
            fields.isNotEmpty(),
            "no `NavResultChannel<...>` field was found declared in any *NavGraph.kt -- this guard " +
                "would pass vacuously",
        )

        val offenders = mutableListOf<String>()
        for ((file, text) in files) {
            val path = file.path.replace('\\', '/')
            for (field in fields) {
                // Any member access on the field outside ALLOWED_MEMBERS is a second exit
                // mechanism beside the channel -- most pointedly `publishForTest`, the test seam,
                // which would fake a result into a live graph. `deliver` is the PRODUCER's move and
                // `pending`/`consume` are the PARENT's, and those three are the whole of the
                // channel's contract; anything else is reaching around it.
                Regex("""\b${Regex.escape(field)}\.(\w+)""").findAll(text).forEach { m ->
                    val member = m.groupValues[1]
                    if (member !in ALLOWED_MEMBERS) {
                        offenders.add("$path: $field.$member")
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a NavResultChannel field is used some way outside its contract " +
                "($ALLOWED_MEMBERS) -- a second exit path beside the channel. " +
                "Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    /**
     * The anti-vacuity half: without this, the previous test would pass just as well if
     * `SettingsNavGraph.kt` stopped calling `.deliver(...)` on `readingProgressResults` entirely --
     * "used nowhere but `.deliver`" is trivially true of a field used nowhere. This asserts the real
     * wiring this batch added is actually THERE.
     */
    @Test
    fun readingProgressArmActuallyDeliversThroughTheChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "SettingsNavGraph.kt" }
        assertTrue(file != null, "cannot find SettingsNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("readingProgressResults.deliver("),
            "SettingsNavGraph.kt no longer calls readingProgressResults.deliver(...) -- the " +
                "reading-progress arm's result would silently stop reaching NavResultChannel",
        )
    }

    /**
     * The bookmark cluster's twin of [readingProgressArmActuallyDeliversThroughTheChannel], and it
     * matters more here: [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] declares THREE channels
     * (see its kdoc for why they are created together), and as of slice 2, Task 6 all three have
     * destinations — so each one gets its own line below.
     * [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered] would be vacuous for a channel
     * nothing used; these tests are what stop a result from silently ceasing to reach its channel.
     */
    @Test
    fun labelEditArmActuallyDeliversThroughTheChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "BookmarkNavGraph.kt" }
        assertTrue(file != null, "cannot find BookmarkNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("labelEditResults.deliver("),
            "BookmarkNavGraph.kt no longer calls labelEditResults.deliver(...) -- the label editor " +
                "would pop without ever handing its result back",
        )
    }

    /**
     * slice 2, Task 5's producer half. `ManageLabels` has two exits -- `saveAndExit` and the
     * HIDELABELS reset -- and both are the HOST's (they need Room and the workspace DAO), so what
     * the arm owns is the one line that hands the host's finished `ManageLabelsResult` to the
     * channel. Without it the label manager would leave with the user's whole session -- deletes,
     * renames, auto-assign changes -- committed to the database but never reported back to the
     * caller that asked for them.
     */
    @Test
    fun manageLabelsArmActuallyDeliversThroughTheChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("manageLabelsResults.deliver("),
            "BookmarkNavGraph.kt no longer calls manageLabelsResults.deliver(...) -- the label " +
                "manager would leave without handing its result back",
        )
    }

    /**
     * slice 2, Task 5's CONSUMER half, and the reason [ALLOWED_MEMBERS] has three entries rather
     * than one.
     *
     * This is the first place the channel's in-graph branch actually runs: `ManageLabels` navigates
     * to `LabelEdit`, so a save there publishes to `pending` and pops instead of exiting the host.
     * If the `ManageLabels` arm did not collect and consume that, editing a label from inside the
     * graph would pop back with the user's edit silently dropped -- and nothing else in the suite
     * would notice, since every other path through `LabelEdit` takes the exit branch. Both halves
     * are asserted: the collection (so the result is seen) and the `consume()` (so a recomposition
     * cannot apply it twice).
     *
     * **Fix round 1, Important 2.** The first version stopped there, and so could not detect the
     * failure this kdoc names: deleting `d.onLabelEditResult(result)` from the arm leaves both
     * `pending` and `consume()` in place -- the result is read, cleared, and thrown away -- and the
     * test stayed green while the user's edit was silently dropped. `consume()` without a consumer
     * is strictly worse than not collecting at all, since it also destroys the value. The third
     * assertion is what makes this a real guard rather than a spelling check, and it was
     * mutation-proved by deleting that call and watching this test go red.
     */
    @Test
    fun manageLabelsArmConsumesTheLabelEditChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("labelEditResults.pending"),
            "BookmarkNavGraph.kt does not collect labelEditResults.pending -- a label edited from " +
                "inside the graph would pop with the user's changes dropped",
        )
        assertTrue(
            text.contains("labelEditResults.consume()"),
            "BookmarkNavGraph.kt does not clear labelEditResults with consume() -- a pending " +
                "result would be re-applied on every recomposition",
        )
        assertTrue(
            text.contains("onLabelEditResult("),
            "BookmarkNavGraph.kt consumes labelEditResults but hands the result to nobody -- " +
                "ManageLabelsDeps.onLabelEditResult is never called, so a label edited from inside " +
                "the graph is read, cleared and DROPPED",
        )
    }

    /**
     * slice 2, Task 6's producer half, and the last of the three. The bookmark LIST's only result is
     * the row the user picked, and unlike the other two exits in this cluster it is not merely a
     * payload handed back: the host lambda behind this channel ALSO stores the very same `Intent`
     * OBJECT in `HistoryManager` before `setResult`s it (classic
     * `BookmarksComposeActivity.kt:188-189`). Losing this call would therefore lose both the caller's
     * result and the history entry, and `MainBibleActivity` would simply never move to the bookmark.
     */
    @Test
    fun bookmarksArmActuallyDeliversThroughTheChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("bookmarkResults.deliver("),
            "BookmarkNavGraph.kt no longer calls bookmarkResults.deliver(...) -- picking a bookmark " +
                "row would leave without telling the caller which one",
        )
    }

    /**
     * slice 2, Task 6's CONSUMER half -- the second parent in these graphs, and the one that makes
     * [net.bible.sharedui.nav.NavResultChannel]'s in-graph branch live for `ManageLabels` at last:
     * until this arm existed, every entry into the label manager was the host's START destination,
     * so `deliver` always took the exit branch and the `pending`/`consume` half of its contract was
     * only ever exercised by `LabelEdit`.
     *
     * Both round trips this arm makes — assign-labels (classic `BookmarksComposeActivity.kt:206`)
     * and manage-labels (`:258`) — come back through this one channel, and the host tells them apart
     * from the request it recorded when it built the payload. Without the consumption the user's
     * label assignment would be committed nowhere and the list would not refresh; the third
     * assertion is the one that catches a `consume()` whose value is then thrown away, exactly as in
     * [manageLabelsArmConsumesTheLabelEditChannel].
     */
    @Test
    fun bookmarksArmConsumesTheManageLabelsChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("manageLabelsResults.pending"),
            "BookmarkNavGraph.kt does not collect manageLabelsResults.pending -- labels assigned " +
                "from inside the graph would pop with the user's choices dropped",
        )
        assertTrue(
            text.contains("manageLabelsResults.consume()"),
            "BookmarkNavGraph.kt does not clear manageLabelsResults with consume() -- a pending " +
                "result would be re-applied on every recomposition",
        )
        assertTrue(
            text.contains("onManageLabelsResult("),
            "BookmarkNavGraph.kt consumes manageLabelsResults but hands the result to nobody -- " +
                "BookmarksDeps.onManageLabelsResult is never called, so an assign/manage round trip " +
                "is read, cleared and DROPPED",
        )
    }

    /**
     * nav-graph slice 4, Task 3's producer half, [labelEditArmActuallyDeliversThroughTheChannel]'s
     * twin for the new `download/nav` cluster. Without this, the repository editor could pop back
     * to the list without ever handing its `RepositoryResult` to the channel -- every one of
     * classic's exits (save/delete/cancel) would then silently do nothing.
     */
    @Test
    fun repositoryEditorArmActuallyDeliversThroughTheChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "DownloadNavGraph.kt" }
        assertTrue(file != null, "cannot find DownloadNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("repositoryEditorResults.deliver("),
            "DownloadNavGraph.kt no longer calls repositoryEditorResults.deliver(...) -- the " +
                "repository editor would pop without ever handing its result back",
        )
    }

    /**
     * nav-graph slice 4, Task 3's consumer half, [manageLabelsArmConsumesTheLabelEditChannel]'s
     * twin: the CUSTOM_REPOSITORIES arm is the parent that must collect and consume
     * `repositoryEditorResults.pending`, or an edit made in the child editor would be read, cleared
     * and dropped instead of reaching [net.bible.sharedcore.download.CustomRepositoryController
     * .applyResult].
     */
    @Test
    fun customRepositoriesArmConsumesTheRepositoryEditorChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "DownloadNavGraph.kt" }
        assertTrue(file != null, "cannot find DownloadNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("repositoryEditorResults.pending"),
            "DownloadNavGraph.kt does not collect repositoryEditorResults.pending -- a repository " +
                "edited from inside the graph would pop with the change dropped",
        )
        assertTrue(
            text.contains("repositoryEditorResults.consume()"),
            "DownloadNavGraph.kt does not clear repositoryEditorResults with consume() -- a pending " +
                "result would be re-applied on every recomposition",
        )
        assertTrue(
            text.contains("applyResult("),
            "DownloadNavGraph.kt consumes repositoryEditorResults but hands the result to nobody -- " +
                "CustomRepositoryController.applyResult is never called, so an edit made inside the " +
                "graph is read, cleared and DROPPED",
        )
    }

    // ——— The bookmark list's exit lambda ————————————————————————————————————————————————————
    // Four properties of `NavHostComposeActivity.bookmarkResults`' `exitWithResult`, none of which
    // any other test in the repo can see.
    //
    // `NavResultIntents.forBookmarks` is a pure function and is tested as one (`NavResultIntentsTest`),
    // but everything that makes this exit CORRECT rather than merely well-packed lives in the lambda:
    // classic `BookmarksComposeActivity.kt:187-194` builds ONE Intent, hands that same OBJECT to
    // `historyTraversal.historyManager.addHistoryItem(null, it)` and then to `setResult`, in that
    // order, all inside a `try` whose `catch` logs and toasts `R.string.error_occurred`. A test that
    // built two structurally-equal Intents would agree with a version that built two -- which is the
    // regression, since `HistoryManager.createHistoryItem` (`:153-155`) keeps the instance it is
    // handed and `IntentHistoryItem.revertTo` (`:55-63`) replays its extras.
    //
    // Four separate tests rather than one offenders list, so a failure names the property rather
    // than a line number, and so each could be mutation-proved on its own.

    /**
     * ONE Intent is built. Mutation-proved by replacing `addHistoryItem(null, resultIntent)` with
     * `addHistoryItem(null, NavResultIntents.forBookmarks(result))`: two Intents, the history list
     * keeping one the caller never sees.
     */
    @Test
    fun theBookmarkExitBuildsOneIntentAndAliasesIt() {
        val body = bookmarkResultsExitBody()
        assertEquals(
            1,
            Regex("""\bval\s+resultIntent\s*=""").findAll(body).count(),
            "NavHostComposeActivity's bookmarkResults exit must bind the result Intent exactly ONCE " +
                "-- the same object goes to the history list and to setResult. Body was:\n$body",
        )
        assertEquals(
            1,
            Regex("""NavResultIntents\.forBookmarks\(""").findAll(body).count(),
            "NavHostComposeActivity's bookmarkResults exit calls NavResultIntents.forBookmarks more " +
                "than once -- a second, structurally-equal Intent is not the same Intent, and " +
                "HistoryManager keeps the instance it is handed. Body was:\n$body",
        )
    }

    /**
     * The SAME IDENTIFIER reaches the history list -- not a second call, not a copy. This is the
     * assertion the task brief asked for in so many words ("assert OBJECT IDENTITY"), expressed the
     * only way a test outside a launched host can express it.
     */
    @Test
    fun theBookmarkExitHandsTheSameIntentObjectToBothSinks() {
        val body = bookmarkResultsExitBody()
        assertTrue(
            Regex("""addHistoryItem\(\s*null\s*,\s*resultIntent\s*\)""").containsMatchIn(body),
            "NavHostComposeActivity's bookmarkResults exit must pass the resultIntent IDENTIFIER to " +
                "addHistoryItem, not a freshly-built Intent. Body was:\n$body",
        )
        assertTrue(
            Regex("""setResult\(\s*RESULT_OK\s*,\s*resultIntent\s*\)""").containsMatchIn(body),
            "NavHostComposeActivity's bookmarkResults exit must pass the same resultIntent " +
                "IDENTIFIER to setResult. Body was:\n$body",
        )
    }

    /**
     * The ORDER. Classic stores the history item BEFORE it sets the result and finishes; `finish()`
     * runs last of the three. Mutation-proved by swapping the first two lines.
     */
    @Test
    fun theBookmarkExitStoresHistoryBeforeSettingTheResult() {
        val body = bookmarkResultsExitBody()
        val history = body.indexOf("addHistoryItem(")
        val setResult = body.indexOf("setResult(")
        val finish = body.indexOf("finish()")
        assertTrue(history >= 0 && setResult >= 0 && finish >= 0, "a call is missing entirely. Body was:\n$body")
        assertTrue(
            history < setResult,
            "NavHostComposeActivity's bookmarkResults exit calls setResult BEFORE addHistoryItem -- " +
                "classic (BookmarksComposeActivity.kt:188-189) stores the history item first. " +
                "Body was:\n$body",
        )
        assertTrue(
            setResult < finish,
            "NavHostComposeActivity's bookmarkResults exit finishes before setting the result. " +
                "Body was:\n$body",
        )
    }

    /**
     * The `try`/`catch` wrapper, which this exact lambda has already lost once (see the
     * `bookmarkResults` field's own comment). All three calls must sit INSIDE the `try`, and the
     * `catch` must still log and toast `error_occurred` -- a `try` whose `catch` swallowed silently
     * would pass a mere "contains try" check.
     */
    @Test
    fun theBookmarkExitKeepsClassicsTryCatchAroundAllThreeCalls() {
        val body = bookmarkResultsExitBody()
        val tryIndex = body.indexOf("try {")
        assertTrue(
            tryIndex >= 0,
            "NavHostComposeActivity's bookmarkResults exit has no `try` at all -- classic " +
                "(BookmarksComposeActivity.kt:168-194) wraps the history/setResult/finish trio. " +
                "Body was:\n$body",
        )
        val tryBlock = balancedBraceBlock(body, body.indexOf('{', tryIndex))
        assertTrue(tryBlock != null, "could not read the try block. Body was:\n$body")
        for (call in listOf("addHistoryItem(", "setResult(", "finish()")) {
            assertTrue(
                tryBlock!!.contains(call),
                "NavHostComposeActivity's bookmarkResults exit leaves `$call` OUTSIDE its try block. " +
                    "Body was:\n$body",
            )
        }
        assertTrue(
            Regex("""catch\s*\(""").containsMatchIn(body) &&
                body.contains("error_occurred") &&
                body.contains("Log.e("),
            "NavHostComposeActivity's bookmarkResults exit lost classic's catch (log + " +
                "R.string.error_occurred toast). Body was:\n$body",
        )
    }

    /**
     * The `exitWithResult` lambda of `NavHostComposeActivity`'s `bookmarkResults`, comments stripped.
     * Read from `:app`'s own source tree -- the working directory of this module's unit tests is the
     * `:app` project directory, which is how [net.bible.android.view.nav.NavHostRoutingGuardTest]
     * already reads `src/main/AndroidManifest.xml`.
     */
    private fun bookmarkResultsExitBody(): String {
        val file = File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        assertTrue(file.isFile, "cannot find ${file.absolutePath} -- this guard would pass vacuously")
        val text = file.readText()
        val marker = "private val bookmarkResults = NavResultChannel<BookmarkResult> {"
        val start = text.indexOf(marker)
        assertTrue(
            start >= 0,
            "cannot find the bookmarkResults channel declaration in ${file.path}; if it was renamed, " +
                "this guard must follow it rather than be deleted",
        )
        val block = balancedBraceBlock(text, start + marker.length - 1)
        assertTrue(block != null, "the bookmarkResults lambda has unbalanced braces")
        return withoutComments(block!!.removePrefix("{").removeSuffix("}"))
    }

    /** The `{ ... }` starting at [openIndex], braces balanced. Null when they are not. */
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

    @Test
    fun theWalkActuallySeesSource() {
        val total = navGraphSources().sumOf { it.readText().length }
        assertTrue(total > 10_000, "the graph-source walk found almost nothing ($total chars)")
    }

    private fun navGraphSources(): List<File> {
        val root = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui")
        assertTrue(root.isDirectory, "cannot find :sharedUi sources at ${root.absolutePath}")
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith("NavGraph.kt") }.toList()
        assertTrue(files.isNotEmpty(), "no *NavGraph.kt found under ${root.absolutePath}")
        return files
    }

    private fun bookmarkNavGraphSource(): String {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "BookmarkNavGraph.kt" }
        assertTrue(file != null, "cannot find BookmarkNavGraph.kt among ${sources.map { it.path }}")
        return withoutComments(file.readText())
    }

    private fun withoutComments(source: String): String =
        source.lineSequence()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")

    private companion object {
        /**
         * The whole of [net.bible.sharedui.nav.NavResultChannel]'s public contract: `deliver` is
         * what a producing destination calls, `pending`/`consume` are what a PARENT destination
         * calls to pick up what a child published before it popped. Anything else -- the
         * `publishForTest` seam, or any member added later -- is a way around the channel and fails
         * [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered].
         *
         * This is one flat set, deliberately. A per-FIELD allow-map (`labelEditResults` may be
         * consumed, the others delivered only) was considered and rejected: it would have to be
         * hand-maintained in lock-step with every new parent/child pairing the migration adds, which
         * is the kind of list that silently goes stale, and the thing it would buy -- catching an arm
         * that consumed a channel it has no business consuming -- is not a failure mode anyone has
         * hit. So, as written, ANY arm may read ANY channel's `pending`; what the guard still pins is
         * that nothing reaches around the channel's contract altogether.
         */
        val ALLOWED_MEMBERS = setOf("deliver", "pending", "consume")
    }
}
