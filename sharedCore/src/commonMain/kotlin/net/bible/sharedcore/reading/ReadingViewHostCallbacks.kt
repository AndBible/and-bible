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
 * **[current] is the FOREGROUND host's reading view, not the last published one (reading-host
 * re-typing R7b).** Every publication carries the token of the host it belongs to and [current]
 * resolves it through [ReadingHostPresence], because "last published" and "the one the user is
 * looking at" are not the same thing: a composition-scoped effect stays entered while its host
 * Activity is in the background, so with two reading views alive the top of the publish stack could
 * be the one the user cannot see — and the host would then dispatch volume keys and screen-on
 * broadcasts, which arrive at the FOREGROUND Activity, into a backgrounded destination's handlers,
 * and report `enableGenericVolumeScroll` for it too. [ReadingViewVisibility] resolves the same
 * question through the same object, so the two seams cannot disagree about which reading view is on
 * screen; see [ReadingHostPresence]'s kdoc for the whole argument.
 */
object ReadingViewHostCallbacks {
    private class Publication(val handlers: ReadingViewHostHandlers, val host: Any)

    private val published = mutableListOf<Publication>()

    /**
     * The handlers of the reading view published by the FOREGROUND host, or `null` when no reading
     * view the user can see is composed — which is also the answer while every published reading
     * view's host is in the background. `lastOrNull` among the foreground host's own publications,
     * for the same reason the collection is a list at all.
     */
    val current: ReadingViewHostHandlers?
        get() = published.lastOrNull { ReadingHostPresence.isForeground(it.host) }?.handlers

    /**
     * How many reading views are published, foreground or not. **Tests only**, and specifically so
     * that a test's "nothing leaked from the previous test" check can still see a publication whose
     * host is not foreground: [current] alone would answer `null` for a real leak and the check
     * would be one that cannot fail.
     */
    val publishedCount: Int get() = published.size

    /**
     * Publishes [handlers] on behalf of [host] until the returned function is called. Call it
     * exactly once. [host] is the reading view's HOST (the Activity), the token [ReadingHostPresence]
     * compares by identity — not the destination and not the handlers.
     */
    fun publish(handlers: ReadingViewHostHandlers, host: Any): () -> Unit {
        val publication = Publication(handlers, host)
        published.add(publication)
        return { published.remove(publication) }
    }
}
