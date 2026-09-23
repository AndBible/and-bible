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

package net.bible.android.view.activity.nav

import android.content.Intent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.nav.ReadingResultCollector

/** Saved-instance-state key carrying [navHostStartRoute]'s answer across a recreate. */
const val STATE_START_ROUTE: String = "nav_start_route"

/**
 * Which route `NavHostComposeActivity.onCreate` starts its graph on.
 *
 * A plain function of two strings so it can be tested without an Activity, for the reason
 * `deliverNavResult` is split out of `NavResultChannel`: this is the part that has to be tested.
 *
 * **Why saved state wins over the Intent** (reading-host re-typing T8b, step 4). `onNewIntent`
 * calls `setIntent(intent)` before navigating, so after an external caller sends this `singleTop`
 * host a child route (`manageLabels` from `CurrentGeneralBookPage`'s StudyPad arm,
 * `myDocumentPages` from its my-document arm, `download` from `ChooseDocumentComposeActivity`, a
 * `HistoryManager` intent revert) the host's OWN intent is that child route. `recreate()` — which
 * the `ReadingPlansUpdatedViaSyncEvent` handler calls, and which every configuration change the
 * manifest does not absorb causes — would then re-run `onCreate` against it, so a host that was
 * created as the reading host would come back as a download host: `bootstrapIfNeeded()` would not
 * run, while the `NavHost`'s own `rememberSaveable` back stack still restores the `reading` entry,
 * and `hostWindowRepository` throws `UninitializedPropertyAccessException` the moment that entry
 * composes. That is the durable-state corruption this batch's spec flagged; it is dormant only
 * while nothing routes a reading view here, and T8b is what routes one.
 *
 * The saved value is written by `onSaveInstanceState`, which pins `NavRoutes.READING` for a host
 * that has bootstrapped: owing a reading view is irreversible for a host's life, so a recreate must
 * put the destination that reads `hostWindowRepository` back on screen.
 *
 * **Why a missing route is a LOUD default rather than the `requireNotNull` it used to be.** That
 * check was right while every caller was in-app code holding [NavHostComposeActivity.EXTRA_ROUTE].
 * T8b's step 3 makes this host the `android:parentActivityName` of seven Activities, and the
 * platform synthesises a bare `Intent(context, NavHostComposeActivity::class.java)` — no extras —
 * for Up navigation and for `TaskStackBuilder.addParentStack`. A `requireNotNull` there is a crash
 * on a back arrow. Defaulting is right because this host's reading route IS the app's main screen
 * now; being LOUD about it is required because a silent fallback would hide a caller that forgot
 * `intentFor()`, which is a routing bug and not a state to render.
 *
 * @param onMissing called exactly when neither source supplies a route, i.e. when the default is
 *   being taken. Never called when one does.
 */
internal fun navHostStartRoute(
    savedStartRoute: String?,
    intentRoute: String?,
    onMissing: () -> Unit,
): String {
    savedStartRoute?.let { return it }
    intentRoute?.let { return it }
    onMissing()
    return NavRoutes.READING
}

/**
 * Is [intent] the bare Intent the PLATFORM synthesises for an Up affordance, rather than one an
 * in-app caller built and forgot to put [NavHostComposeActivity.EXTRA_ROUTE] on?
 *
 * **T8b fix round 2.** Fix round 1 claimed the two were indistinguishable. They are not, and the
 * platform sources in this container say exactly how. `Activity.getParentActivityIntent()`
 * (`android/app/Activity.java:8912-8932`) resolves the child's `android:parentActivityName`, then
 * looks up **that parent's own** `parentActivityName` and branches:
 *
 * ```java
 *     final Intent parentIntent = parentActivity == null
 *             ? Intent.makeMainActivity(target)
 *             : new Intent().setComponent(target);
 * ```
 *
 * T8b step 3 removed this host's own `parentActivityName` (`src/main/AndroidManifest.xml:134-140`),
 * so for all seven Activities that now name it the first branch is taken, and
 * `Intent.makeMainActivity` (`android/content/Intent.java:8113-8118`) is three lines:
 * `ACTION_MAIN`, the component, `CATEGORY_LAUNCHER`. `NavUtils` and `TaskStackBuilder` both delegate
 * to that same method, so every synthesised route into this host carries that signature and nothing
 * an in-app caller builds by hand does.
 *
 * **No false positive from the real launcher**: this host declares no `<intent-filter>` in any of the
 * four manifests, so a `MAIN`/`LAUNCHER` intent from the home screen cannot reach it -- the launcher
 * alias targets `.StartupActivity`.
 *
 * **Coupled to step 3, in the safe direction.** If this host is ever given a `parentActivityName` of
 * its own, `getParentActivityIntent` switches to the `new Intent().setComponent(target)` branch and a
 * synthesised intent would read here as an in-app caller -- i.e. it would be logged louder, never
 * quieter. `ReadingHostLauncherGuardTest.theNavHostIsNotItsOwnUpParent` pins the premise.
 */
internal fun isSynthesisedUpIntent(intent: Intent, hostClassName: String): Boolean {
    // The component is read through a null CHECK rather than `intent.component?.className`, matching
    // [aCancelFromThisIntentWouldBeTheUsers] below: `ActivityResultDispatchGuardTest` text-scans all
    // of `src/main/java` for that exact spelling, because dispatching a RESULT on the result
    // Intent's component class name is the channel it exists to keep out. This reads an OUTGOING
    // intent to classify it, which is not that -- but the scan is textual, and the honest response
    // to a textual guard's false positive is to write the code the way its siblings do, not to
    // weaken the guard.
    val component = intent.component ?: return false
    return intent.action == Intent.ACTION_MAIN &&
        intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true &&
        component.className == hostClassName
}

/**
 * Could a `RESULT_CANCELED` coming back at `ActivityBase.STD_REQUEST_CODE` from [intent] be a cancel
 * the USER performed, on one of THIS app's choosers?
 *
 * **reading-host re-typing T8b fix round 1, C2.** `NavHostComposeActivity` ported classic
 * `MainBibleActivity.onActivityResult`'s first statement (`:1727-1733`): a cancelled
 * `STD_REQUEST_CODE` chooser that left the page with no key at all steps back in history, because
 * the user has been dropped on a general book that cannot render. That is right for a chooser. It is
 * wrong for every other thing dispatched at that same default request code — and
 * `MenuCommandHandler`, `CommonUtils.openLink` and `IntentHistoryItem.revertTo` all dispatch at it.
 *
 * Three shapes are excluded. **The first is the one that carries every live call site today**; the
 * other two are narrower and are documented here so a future caller cannot reintroduce the defect.
 *
 *  - **An intent with no component** — every implicit dispatch at this request code:
 *    `Intent.createChooser` for the "tell a friend" share row (`MenuCommandHandler.kt:339`), the
 *    `ACTION_VIEW` market/Play links behind "Rate AndBible" (`:160-170`), and
 *    `CommonUtils.openLink`'s external browser link (`:1130`, `:1141`). Dismissing a system chooser
 *    or coming back from the Play Store is a real user action, but it is not one of THIS app's key
 *    choosers and cannot have left a page keyless.
 *  - **`FLAG_ACTIVITY_NEW_TASK`** — `Activity.startActivityForResult`'s own javadoc (platform
 *    sources, `android/app/Activity.java:5964-5969`): *"if the activity you are launching uses
 *    FLAG_ACTIVITY_NEW_TASK, it will not run in your task and thus you will immediately receive a
 *    cancel result."* **No live `STD_REQUEST_CODE` site in `app/src/main` sets that flag**, so this
 *    arm is defensive rather than load-bearing. In particular the "Rate AndBible" intent does NOT:
 *    it sets `FLAG_ACTIVITY_NO_HISTORY or FLAG_ACTIVITY_MULTIPLE_TASK` then
 *    `FLAG_ACTIVITY_NEW_DOCUMENT` (`MenuCommandHandler.kt:160-164`), and it is excluded by the
 *    no-component arm above. (`NEW_DOCUMENT | MULTIPLE_TASK` is `documentLaunchMode="always"`,
 *    `Intent.java:7442-7449`, so that intent does leave this task — which is the javadoc's stated
 *    REASON for the cancel — but the guarantee is written against `NEW_TASK` and is not claimed
 *    here for any other flag.)
 *  - **[hostClassName] itself** — the nav host starting itself, which every `MenuCommandHandler`
 *    row for a MIGRATED screen now does (`dailyReadingPlanButton`, `readingProgressButton`,
 *    `managePrompts`, `bookmarksButton`, `myDocumentsButton`, …). With `android:launchMode="singleTop"`
 *    and this activity on top, the platform documents the outcome directly, in
 *    `Activity.startActivityIfNeeded`'s javadoc (`android/app/Activity.java:6586-6592`): *"if you are
 *    using the FLAG_ACTIVITY_SINGLE_TOP flag, or singleTask or singleTop launchMode, and the
 *    activity that handles intent is the same as your currently running activity, then a new
 *    instance is not needed. In this case, instead of the normal behavior of calling onNewIntent
 *    this function will return"* — i.e. the NORMAL behaviour of `startActivityForResult` there is
 *    `onNewIntent` on the same instance. No second `ActivityRecord` is created, so nothing can ever
 *    finish and deliver a result for that request code: measured against the platform sources, such
 *    a launch yields NEITHER a result nor a cancel. Excluding it is therefore belt-and-braces rather
 *    than the load-bearing arm — but it costs one line and it makes the guard correct under the
 *    other reading too, which matters because the result-dropping half of that same self-launch is a
 *    live, separately-tracked defect.
 *
 * Deliberately NOT expressed as "was the intent a chooser": this host cannot enumerate its callers'
 * intents, and a list of chooser components would go stale silently. What it CAN answer is whether
 * the platform, or this host's own `singleTop` reuse, might hand back a cancel nobody asked for.
 */
internal fun aCancelFromThisIntentWouldBeTheUsers(intent: Intent, hostClassName: String): Boolean {
    val component = intent.component ?: return false
    if (component.className == hostClassName) return false
    return intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0
}

/**
 * A destination of `NavHostComposeActivity`'s OWN graph that the reading view launches FOR A
 * RESULT, keyed by the route base that addresses it (reading-host re-typing T8c).
 *
 * Route BASE, not the whole route: `NavRoutes.manageLabels(data)` carries its payload in the query
 * string, so a launch route and its pattern only agree up to the `?`. The bases are DERIVED from
 * the patterns rather than spelled a second time, so a renamed route cannot leave this enum quietly
 * matching nothing.
 *
 * Nine entries, one per channel the reading view can fill (slice 8 added the key choosers, the passage grid,
 * the document chooser and the workspace selector). The five T8c ones between them carry nine call
 * sites: `ManageLabels` alone is reached from `BibleView.assignLabels`, `HideLabelsPreference`,
 * `AutoAssignPreference`, `MenuCommandHandler`'s StudyPads row and `CurrentGeneralBookPage`'s
 * StudyPad arm; `ReadingProgress` from the menu row and from `BibleJavascriptInterface
 * .openReadingProgress`.
 *
 * A route with no entry here is a self-launch that produces NO result (`download`, `settings`,
 * `search`) and so has nothing to wait for. That is the common case and it is silent on purpose.
 */
internal enum class ReadingResultKind(
    /**
     * Slice 8 (plan Correction 3): whether a request of this kind still open when the graph returns to
     * `reading` with nothing pending is answered as a CANCEL. True for the three chooser kinds, whose
     * callers relied on a real chooser Activity's `RESULT_CANCELED` before slice 8 moved them in-graph
     * (classic's "no key -> go back in history" arm). The five T8c kinds keep their pre-slice-8
     * behaviour.
     */
    val answersAbandonment: Boolean,
    /** Every route BASE that addresses this kind's destination(s) -- the three key choosers share one channel. */
    vararg val routeBases: String,
) {
    ManageLabels(false, NavRoutes.MANAGE_LABELS_PATTERN.substringBefore('?')),
    MyDocumentPages(false, NavRoutes.MY_DOCUMENT_PAGES_PATTERN.substringBefore('?')),
    ReadingProgress(false, NavRoutes.READING_PROGRESS_PATTERN.substringBefore('?')),
    Bookmarks(false, NavRoutes.BOOKMARKS_PATTERN.substringBefore('?')),
    MyDocuments(false, NavRoutes.MY_DOCUMENTS_PATTERN.substringBefore('?')),
    // ——— slice 8 B1: slice 7's destinations, now reached from the reading view ———
    KeyChooser(true, NavRoutes.CHOOSE_GENERAL_BOOK_KEY, NavRoutes.CHOOSE_MAP_KEY, NavRoutes.CHOOSE_DICTIONARY_WORD),
    PassageGrid(true, NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.substringBefore('?')),
    ChooseDocument(true, NavRoutes.CHOOSE_DOCUMENT_PATTERN.substringBefore('?')),
    Workspace(false, NavRoutes.WORKSPACE_SELECTOR),
    ;

    companion object {
        fun forRoute(route: String): ReadingResultKind? {
            val base = route.substringBefore('?')
            return entries.firstOrNull { base in it.routeBases }
        }
    }
}

/**
 * Which of this host's own result-producing destinations [intent] opens — or null when [intent] is
 * not a self-launch, or is one that produces nothing.
 *
 * A plain function of an Intent and a class name, for [aCancelFromThisIntentWouldBeTheUsers]' reason:
 * this is the decision that says whether the reading view's answer will be collected at all, and it
 * cannot be tested while it is a private method of a launched Activity.
 *
 * The component is read through a null CHECK rather than `intent.component?.className`, matching
 * [isSynthesisedUpIntent] above: `ActivityResultDispatchGuardTest` text-scans all of
 * `src/main/java` for that exact spelling. This reads an OUTGOING intent to classify it, which is
 * not the result-dispatch channel that guard exists to keep out — but the scan is textual, and the
 * honest response to a textual guard's false positive is to write the code the way its siblings do,
 * not to weaken the guard.
 */
internal fun readingResultKindForLaunch(intent: Intent, hostClassName: String): ReadingResultKind? {
    val component = intent.component ?: return null
    if (component.className != hostClassName) return null
    val route = intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE) ?: return null
    return ReadingResultKind.forRoute(route)
}

/**
 * What the reading view has asked this host's own destinations for, and at which request code it is
 * waiting — the GATE half of every [ReadingResultCollector] the reading destination composes
 * (reading-host re-typing T8c).
 *
 * A class of its own, not a `mutableMapOf` inside the Activity, for [readingResultKindForLaunch]'s
 * reason: the three properties below are the whole correctness argument for the fix and none of
 * them is reachable from a test while they are an Activity's private field.
 *
 * **It cannot let a collector consume twice.** [claim] REMOVES the entry as it reads it, and
 * `NavResultChannel.consume()` clears the channel as it reads it, so the `LaunchedEffect` re-running
 * with the now-null pending value hits its early return and a second delivery of the same value
 * finds no request.
 *
 * **It cannot let one consume never.** Every launch that can produce one of these results passes
 * through `NavHostComposeActivity.startActivityForResult` — `ActivityBase.awaitIntent` calls it too
 * — which is where [record] is called; and a child that publishes to `pending` pops to the reading
 * destination, whose composition runs the collectors.
 *
 * **A LEFT-OVER entry cannot be mis-spent.** A request the user abandoned (backing out of the
 * bookmark list without picking a row) stays recorded until the next launch overwrites it — but a
 * result can only reach the reading destination if that destination is the one the producing route
 * was pushed onto, i.e. if a launch was made, which rewrites the entry first. So a stale entry can
 * be overwritten, never spent. (Slice 8: an abandoned request of an
 * [ReadingResultKind.answersAbandonment] kind does not linger at all -- [claimAbandoned] forgets it
 * when the graph returns to `reading` and the host answers it as a cancel.)
 */
internal class ReadingResultRequests {
    private val byKind = mutableMapOf<ReadingResultKind, Int>()

    fun record(kind: ReadingResultKind, requestCode: Int) {
        byKind[kind] = requestCode
    }

    fun isAwaiting(kind: ReadingResultKind): Boolean = byKind.containsKey(kind)

    /** The request code that asked, FORGOTTEN in the same breath — see this class's kdoc. */
    fun claim(kind: ReadingResultKind): Int? = byKind.remove(kind)

    /**
     * Slice 8: the requests of an [ReadingResultKind.answersAbandonment] kind still open when the graph
     * is back on `reading` and [isPending] says no answer is waiting -- the user left the chooser without
     * choosing. Forgotten in the same breath, like [claim], so they cannot be answered twice.
     */
    fun claimAbandoned(isPending: (ReadingResultKind) -> Boolean): List<Pair<ReadingResultKind, Int>> {
        val abandoned = byKind.entries
            .filter { (kind, _) -> kind.answersAbandonment && !isPending(kind) }
            .map { it.key to it.value }
        abandoned.forEach { (kind, _) -> byKind.remove(kind) }
        return abandoned
    }
}

/**
 * One gated collector: the channel, the gate that reads [requests], the apply that clears the
 * request before spending it, and Ruling D's loud drop for an answer nobody asked for.
 *
 * A top-level function rather than a method, again so that the gate discipline is reachable by a
 * test without launching a host — [deliver] is the only part that needs one.
 *
 * @param log where the two Ruling-D lines go; `Log.w` in production.
 */
internal fun <T> readingResultCollector(
    resultChannel: NavResultChannel<T>,
    kind: ReadingResultKind,
    requests: ReadingResultRequests,
    log: (String) -> Unit,
    deliver: (result: T, requestCode: Int) -> Unit,
): ReadingResultCollector<T> = ReadingResultCollector(
    resultChannel = resultChannel,
    awaiting = { requests.isAwaiting(kind) },
    apply = { result ->
        val requestCode = requests.claim(kind)
        if (requestCode == null) {
            // Unreachable: `awaiting` is read in the same effect, before `consume()`. Logged rather
            // than ignored because the alternative is losing the user's answer in silence, which is
            // the whole defect this block exists to end.
            log("A $kind answer was claimed but its request was gone; not applied.")
        } else {
            deliver(result, requestCode)
        }
    },
    dropUnclaimed = {
        log(
            "A $kind answer reached the reading destination that nothing asked for. Dropped -- " +
                "leaving it pending would let the next request spend somebody else's answer."
        )
    },
)

/**
 * F60 fix round 2 (final-review C2): the host-owned bookkeeping [MyDocumentPagesDeps.beforeSwitchDocument]
 * runs immediately before `MyDocumentsNavGraph`'s `onSwitchDocument` pops the leaving `MyDocumentPages`
 * entry and navigates to `MyDocuments` — a plain in-graph `navigate`/`popUpTo` that never passes
 * through [ActivityBase.startActivityForResult]'s funnel, so nothing here records or completes
 * anything by itself the way an ordinary launch would.
 *
 * A plain function of [requests] and two callbacks, again so the gate discipline is reachable by a
 * test without an Activity — [readingResultCollector]'s own reason.
 *
 * Two independent bookkeeping steps, both needed because the switch bypasses the funnel entirely:
 *
 *  1. **The reading-view scenario — F60's own motivating case.** When [enteredFromReading] is true
 *     (`Pages` sat directly on `reading`, F53's `CurrentGeneralBookPage` entry, with no `MyDocuments`
 *     below it yet), the `MyDocuments` answer this switch is about to produce has nobody recorded
 *     as awaiting it: [recordReadingResultRequest] only fires from the `startActivityForResult`
 *     funnel, and this hop never goes through it. Recording the request at
 *     [ActivityBase.STD_REQUEST_CODE] — the in-graph code every other `applyChosenX` arm uses — is
 *     what makes the reading destination's `MyDocuments` collector apply the eventual answer
 *     instead of dropping it (`dropUnclaimed`).
 *  2. **The orphaned `MyDocumentPages` await (final review M1).** The `Pages` entry being left
 *     behind may have a live, parked `awaitChosenKey`/`awaitIntent` deferred from the ORIGINAL
 *     launch that pushed it — popping it via `popUpTo` rather than `NavResultChannel.deliver` never
 *     completes that deferred, which then hangs for the life of the Activity. [claim]ing it here and
 *     handing the request code to [resolveOrphanedMyDocumentPagesAwait] lets the caller complete it
 *     (with `RESULT_CANCELED`, mirroring backing out of `Pages` with nothing chosen), so its "no key
 *     -> go back in history" arm stays reachable rather than dangling. Safe to attempt
 *     unconditionally: a switch reached through `MyDocuments` (the ordinary `MyDocuments -> open doc
 *     -> Pages` path) never recorded this kind async in the first place, so [claim] returns null and
 *     nothing is called.
 */
internal fun beforeSwitchMyDocument(
    requests: ReadingResultRequests,
    enteredFromReading: Boolean,
    resolveOrphanedMyDocumentPagesAwait: (requestCode: Int) -> Unit,
) {
    if (enteredFromReading) {
        requests.record(ReadingResultKind.MyDocuments, ActivityBase.STD_REQUEST_CODE)
    }
    requests.claim(ReadingResultKind.MyDocumentPages)?.let(resolveOrphanedMyDocumentPagesAwait)
}
