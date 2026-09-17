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
package net.bible.sharedcore.reading

/**
 * Which reading HOST the user is actually looking at (reading-host re-typing R7b, spec §3.5).
 *
 * **Why this exists.** Both [ReadingViewVisibility] and [ReadingViewHostCallbacks] used to answer
 * "is the reading view on screen?" from state a reading view writes about ITSELF — a depth counter
 * entered by the destination's `DisposableEffect`, and a publish stack whose top was called
 * "current". Neither is the same thing as "on screen", because a composition-scoped effect stays
 * entered while its host Activity is in the background: navigation-compose does not dispose the
 * current entry's content when the Activity stops. That produced two recorded divergences with one
 * root cause, both of which became user-visible the moment the `reading` destination rendered the
 * real reading view:
 *
 *  - **A dead back key.** With a classic secondary Activity over a backgrounded host whose reading
 *    destination is still composed, `depth > 0` kept `ReadingViewVisibility.isVisible` true, so
 *    `HistoryManager.goBack()`'s `if (!isVisible) currentActivity?.finish()` never fired — and
 *    `ActivityBase.onBackPressed` returns WITHOUT `super` whenever `historyTraversal.goBack()`
 *    returned true, so the user got a back press that reverted history and left them on the same
 *    secondary screen.
 *  - **Keys dispatched into a backgrounded destination.** `ReadingViewHostCallbacks.current` was
 *    "last published", so with two reading views alive the handlers on top of the publish stack
 *    could belong to the one the user cannot see — while volume keys and screen-state broadcasts
 *    arrive at the FOREGROUND Activity.
 *
 * The answer is one published fact instead of two that can disagree: a reading view's publication
 * carries the token of the host it belongs to, the host reports which token is foreground, and both
 * seams resolve through [isForeground]. It is the same argument that already derives
 * `NavHostComposeActivity.enableGenericVolumeScroll` from `ReadingViewHostCallbacks.current == null`.
 *
 * **The token is `Any?` on purpose.** `:sharedCore` is Android-free (the batch's global
 * constraints), so no `Activity` type may cross into `commonMain`; the hosts pass `this` and this
 * object only ever compares it by IDENTITY. It never holds anything but the ONE current token, so
 * it cannot leak a chain of dead Activities — though the foreground one is by definition alive.
 *
 * **Who writes it.** Every host that can show a reading view, at the same moments the OLD predicate
 * (`CurrentActivityHolder.currentActivity is MainBibleActivity`) changed:
 *
 *  - [setForeground] wherever that host also declares its reading view present —
 *    `MainBibleActivity.onCreate`/`onResume`/`onActivityResult` and
 *    `NavHostComposeActivity.bootstrapIfNeeded`/`onResume`. `onCreate` matters as much as
 *    `onResume`: `ActivityBase.onCreate`'s first line is `CurrentActivityHolder.activate(this)`, so
 *    the old predicate was true for the whole of `onCreate` — and both hosts post `AddHistoryItem`
 *    synchronously inside that window through their `openLink` deep-link branch. A presence first
 *    declared at `onResume` would make [ReadingViewVisibility.isVisible] false there and bring back
 *    the wrong-`IntentHistoryItem` defect that `onCreate` setter exists to prevent.
 *  - [clearForeground] in `onPause`, which is where the flag has always gone false (the remaining,
 *    harmless divergence from the old predicate is the `onPause`..`onStop` window, documented in
 *    `HistoryManager.goBack`).
 *
 * **The asymmetry between the two is the whole point of the token.** Android's lifecycle overlaps
 * hosts — `A.onPause` runs before `B.onCreate`/`onResume`, but a *stale* pause can still arrive
 * after another host has made itself foreground (a paused-then-immediately-resumed host, a
 * `MULTIPLE_TASK` second instance, an out-of-order callback). A bare `setForeground(null)` in
 * `onPause` would then clear the host the user IS looking at and blank the reading view's history
 * for as long as it lasted. [clearForeground] retracts a host's OWN presence and nothing else.
 *
 * Main thread only — every caller is an Activity lifecycle callback, a composition effect, or a
 * test — so the field is plain.
 */
object ReadingHostPresence {
    private var foreground: Any? = null

    /** [host] is now the reading host the user is looking at; `null` means no host is. */
    fun setForeground(host: Any?) {
        foreground = host
    }

    /**
     * [host] is going away — retract its presence, but ONLY if it is still the foreground one. See
     * the class kdoc: a stale `onPause` must not clear the host that resumed after it.
     */
    fun clearForeground(host: Any?) {
        if (isForeground(host)) foreground = null
    }

    /**
     * Whether [host] is the one the user is looking at. Identity, not equality: the token is an
     * Activity, and two distinct instances of one Activity class are two different hosts. A `null`
     * host is never foreground, so an unowned publication can never pass this gate.
     */
    fun isForeground(host: Any?): Boolean = host != null && host === foreground
}
