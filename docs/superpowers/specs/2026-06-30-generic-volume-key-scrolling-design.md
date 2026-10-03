# Generic volume-key scrolling in list/scroll views

**Date:** 2026-06-30
**Status:** Approved (design)

## Problem

On e-ink devices, users scroll with the hardware volume up/down buttons instead
of touch. This works well in the main `BibleView`, but it is implemented only in
`MainBibleActivity` and is unavailable in every other screen. AndBible has many
list-based screens (bookmarks, labels, history, search results, reading-plan
lists, workspace selector, document management, downloads, …). Volume-key
scrolling should work in all of them, implemented as generically as possible.

## Goal

Volume up/down scrolls the active scrollable view in every `ActivityBase`-derived
screen, mirroring the existing BibleView behaviour (page-at-a-time, honouring the
"disable animations" setting). One shared toggle, one shared implementation.

## Current behaviour (reference)

- `MainBibleActivity.onKeyDown` (app/src/main/java/.../page/MainBibleActivity.kt,
  ~lines 2031–2058) intercepts `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` when
  the `volume_keys_scroll` setting is on (default `true`), speech is not active,
  and music is not playing. It calls `bibleView.volumeUpPressed()` /
  `volumeDownPressed()`, which emit Vue events `scroll_up` / `scroll_down`.
- The Vue handler (`BibleView.vue` `scrollUpDown`) scrolls by `scrollAmount` —
  roughly one page height (`pageScrollAmount`, default a full page minus a small
  overlap so the last line is not cut off).
- All list screens extend `ActivityBase`, either via `ListActivityBase`
  (ListView screens) or directly (`ActivityBase` + RecyclerView). None currently
  handle volume keys.
- `CommonUtils.settings.disableAnimations` (default `true` on Onyx/e-ink devices)
  already exists and is the right signal for instant-vs-smooth scrolling.

## Design

### 1. `VolumeButtonScroll` utility object

New file `app/src/main/java/net/bible/android/view/util/VolumeButtonScroll.kt`.

```
object VolumeButtonScroll {
    /** Depth-first search for the first visible, laid-out scrollable container. */
    fun findScrollableView(root: View): View?

    /** Page-scroll the given view up or down. */
    fun scroll(view: View, down: Boolean, animate: Boolean)
}
```

- `findScrollableView` walks the view tree depth-first and returns the first view
  that is a `RecyclerView`, `AbsListView` (covers `ListView`), or
  `ScrollView` / `NestedScrollView`, **and** is `View.VISIBLE` with `height > 0`.
  Detection is type-based, not `canScrollVertically`, so the view is still found
  when it is already scrolled to an edge (so the opposite direction still works).
- `scroll` computes a page delta of ~90% of `view.height` (slight overlap, like
  BibleView). It dispatches per type:
  - `RecyclerView`: `smoothScrollBy(0, delta)` (animate) / `scrollBy(0, delta)` (instant)
  - `AbsListView`: `smoothScrollBy(delta, duration)` (animate) / `scrollListBy(delta)` (instant)
  - `ScrollView` / `NestedScrollView`: `smoothScrollBy(0, delta)` / `scrollBy(0, delta)`
  - `delta` is negated when `down` is false.

### 2. `ActivityBase`

- Add `protected open val enableGenericVolumeScroll: Boolean get() = true`.
- Override `onKeyDown(keyCode, event)`:
  - If `enableGenericVolumeScroll`, `keyCode` is `KEYCODE_VOLUME_UP` /
    `KEYCODE_VOLUME_DOWN`, `CommonUtils.settings.getBoolean("volume_keys_scroll", true)`
    is on, and `AudioManager.isMusicActive` is false:
    find the scrollable view under `findViewById(android.R.id.content)`; if found,
    `VolumeButtonScroll.scroll(view, down = keyCode == KEYCODE_VOLUME_DOWN,
    animate = !CommonUtils.settings.disableAnimations)` and return `true` (consume).
    If no scrollable view is found, fall through to `super`.
  - Otherwise `super.onKeyDown(...)`.
- The `isMusicActive` guard transitively covers TTS, which plays on the music
  stream, so `ActivityBase` does not need a reference to `SpeakControl`.

### 3. `MainBibleActivity`

- Override `enableGenericVolumeScroll = false`.
- Rationale: its existing `onKeyDown` already owns volume keys (WebView/JS path
  with its own speech guard). Without the opt-out, when it delegates to `super`
  (e.g. while speaking) the generic handler could scroll the WebView natively,
  bypassing the JS mechanism and the speech guard. No behaviour change in
  `MainBibleActivity`.

### 4. Setting and strings

- Reuse the existing `volume_keys_scroll` setting — no new preference.
- Update the summary string `prefs_volume_keys_scroll_summary` from
  "Use volume up/down to scroll Bible text" to also mention lists, e.g.
  "Use volume up/down to scroll Bible text and lists". English only (other
  languages handled separately).

## Testing

Robolectric unit tests for `VolumeButtonScroll`:

- `findScrollableView` returns the `RecyclerView` / `ListView` /
  `ScrollView` nested inside a container hierarchy.
- Skips views that are `GONE` / `INVISIBLE` / zero-height.
- Returns `null` when the tree contains no scrollable container.
- Returns the first match in depth-first order when several exist.
- `scroll` delta sign: down scrolls positive, up scrolls negative; magnitude is a
  sensible fraction of the view height. (Assert via a spy/fake or by checking
  `scrollY` on a measured `ScrollView`.)

## Out of scope

- Changing BibleView's existing scroll mechanism.
- Per-screen item-by-item (single-row) scrolling.
- Any new user-facing setting.
- Translating the updated summary string (handled by the separate translation
  workflow).
