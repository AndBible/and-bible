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
import net.bible.sharedcore.nav.NavRoutes

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
