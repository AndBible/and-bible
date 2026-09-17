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
