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
 * A key press the reading view claims from its host, decoded by the host so that no platform key
 * type crosses into `commonMain`.
 *
 * Exactly the three the classic `MainBibleActivity.onKeyDown` claimed, and nothing else: the two
 * volume keys, plus BACK **from an external keyboard** (`InputDevice.isExternal` +
 * `SOURCE_KEYBOARD`), which is a different gesture from the on-screen/system back the
 * `PlatformBackHandler`s handle. The host decides which of these an incoming key event IS; the
 * destination decides what to do about it.
 */
enum class ReadingViewKey {
    VolumeUp,
    VolumeDown,
    ExternalKeyboardBack,
}

/**
 * The per-destination Activity callbacks the reading view needs, as published by the reading
 * destination while it is on screen. See [ReadingViewHostCallbacks].
 *
 * - [onKey] returns whether the reading view CONSUMED the key. `false` lets the host fall through to
 *   `super.onKeyDown`, which is what classic did when its own gates (`volume_keys_scroll`, speaking,
 *   music playing) said no.
 * - [onScreenTurnedOn]/[onScreenTurnedOff] are `ActivityBase`'s screen-state callbacks, which only
 *   `MainBibleActivity` ever overrode (design §4.1): they forward to the active window's `BibleView`,
 *   and the on-side also re-reads night mode.
 */
class ReadingViewHostHandlers(
    val onKey: (ReadingViewKey) -> Boolean,
    val onScreenTurnedOn: () -> Unit,
    val onScreenTurnedOff: () -> Unit,
)

/**
 * How the reading DESTINATION gets at the two Activity-level callback families that are its own and
 * nobody else's — design §4.1's "only three members of `ActivityBase` are per-destination". Design
 * §9's other pair, `freeze`/`unFreeze`, is NOT moved here and is NOT deleted either: see
 * `ActivityBase.freeze` for why a second live `MainBibleActivity` keeps it alive until Task 13.
 *
 * **Why a seam at all.** A key event and a screen-on broadcast arrive at an *Activity*; a
 * destination is a composition. `NavHostComposeActivity` is the one host for every destination, so
 * it cannot answer either callback the way one screen wants without knowing which screen is on
 * screen. So the reading destination publishes its handlers here from the same `DisposableEffect`
 * that owns [ReadingViewVisibility], and the host consults [current] from its own overrides. The
 * shape is deliberately the same as [ReadingViewVisibility]'s — one small `commonMain` object the
 * destination writes and the host reads — because it is the same problem.
 *
 * **What the handlers themselves contain is HOST code.** Everything the classic
 * `MainBibleActivity.onKeyDown`/`onScreenTurnedOn` did — `AudioManager.isMusicActive`,
 * `speakControl.isSpeaking`, `settings.getBoolean("volume_keys_scroll")`, `BibleView
 * .volumeDownPressed()`, `ScreenSettings.refreshNightMode()` — is Android or `:app`, so the lambdas
 * are built in `NavHostComposeActivity` and handed to the destination as deps. The destination owns
 * only WHEN they are live, which is the part the Activity cannot know.
 *
 * **`enableGenericVolumeScroll` moves with them, by inversion.** `ActivityBase`'s generic volume
 * scroll defaults to ON and `MainBibleActivity` overrode it to **false** — the reading view opts OUT
 * of the base's `VolumeButtonScroll.findScrollableView(android.R.id.content)` and does its own thing.
 * The host therefore reports the flag as `current == null`: OFF exactly while the reading
 * destination is published (classic's `false`), ON for every other destination (classic's other
 * Activities, which never overrode it). One published fact, not two that could disagree.
 *
 * A LIST, not a single nullable field, for [ReadingViewVisibility]'s reason: `FLAG_ACTIVITY_MULTIPLE_TASK`
 * can make a second reading instance real, and the one that leaves must not unpublish the one still
 * on screen. All access is on the main thread (a composition effect, or an Activity callback).
 *
 * **[current] is LAST PUBLISHED, which is not the same thing as "the foreground host".** It is the
 * same divergence [ReadingViewVisibility] has, in the same direction and for the same reason: a
 * composition-scoped effect stays entered while its host Activity is in the background, so with two
 * reading views alive the one on top of the *publish stack* can be the one the user cannot see.
 * The host then dispatches volume keys and screen-on broadcasts — which arrive at the FOREGROUND
 * Activity — into the backgrounded destination's handlers, and reports
 * `enableGenericVolumeScroll` for it too. Latent at this commit, since nothing routes to
 * `NavRoutes.READING` and only `MainBibleActivity` can be a second instance; **resolving it is a
 * precondition for the task that makes `ReadingNavDeps.content` real**, alongside the
 * [ReadingViewVisibility] item, and it wants the same answer as that one (whatever tells a
 * published destination that its host is actually resumed). Not fixed here on purpose: the shape
 * of that answer is the next batch's design decision, and half of it would be worse than an
 * accurate comment.
 */
object ReadingViewHostCallbacks {
    private val published = mutableListOf<ReadingViewHostHandlers>()

    /**
     * The handlers of the LAST PUBLISHED reading view, or `null` when no reading view is composed.
     * Read the class kdoc before treating "last published" as "the one the user is looking at" —
     * with two instances alive they are not the same thing.
     */
    val current: ReadingViewHostHandlers? get() = published.lastOrNull()

    /** Publishes [handlers] until the returned function is called. Call it exactly once. */
    fun publish(handlers: ReadingViewHostHandlers): () -> Unit {
        published.add(handlers)
        return { published.remove(handlers) }
    }
}
