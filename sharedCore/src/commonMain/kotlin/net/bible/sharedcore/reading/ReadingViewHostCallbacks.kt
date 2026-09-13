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
 * nobody else's — design §4.1's "only three members of `ActivityBase` are per-destination", minus
 * `freeze`/`unFreeze`, which are deleted rather than moved (§9).
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
 * on screen. [current] is the last published — the one on top. All access is on the main thread (a
 * composition effect, or an Activity callback).
 */
object ReadingViewHostCallbacks {
    private val published = mutableListOf<ReadingViewHostHandlers>()

    /** The handlers of the reading view on top, or `null` when no reading view is composed. */
    val current: ReadingViewHostHandlers? get() = published.lastOrNull()

    /** Publishes [handlers] until the returned function is called. Call it exactly once. */
    fun publish(handlers: ReadingViewHostHandlers): () -> Unit {
        published.add(handlers)
        return { published.remove(handlers) }
    }
}
