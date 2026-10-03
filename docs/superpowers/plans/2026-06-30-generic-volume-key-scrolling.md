# Generic Volume-Key Scrolling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the hardware volume up/down buttons page-scroll the active scrollable view in every `ActivityBase`-derived screen (lists and scroll views), mirroring the existing BibleView behaviour.

**Architecture:** A small stateless utility (`VolumeButtonScroll`) locates the first visible scrollable container in a view tree and page-scrolls it. `ActivityBase.onKeyDown` calls this utility for volume keys, gated by the existing `volume_keys_scroll` setting and a music-active guard. `MainBibleActivity` opts out (it keeps its own WebView/JS path).

**Tech Stack:** Kotlin, Android (minSdk 23), AndroidX RecyclerView + NestedScrollView, Robolectric + JUnit4 for unit tests.

## Global Constraints

- Kotlin/Android only — no Vue.js changes (Vue tests must NOT be run for this work).
- Import classes and use simple names; never fully-qualified names in code.
- New file copyright header: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham" in new files). When editing an existing file, update its copyright year to `2026`.
- All user-facing strings go through `app/src/main/res/values/strings.xml`. English only — do NOT add other-language translations.
- Reuse the existing setting key `volume_keys_scroll` (default `true`). Do NOT add a new preference.
- Android test command: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*VolumeButtonScroll*"` — **requires `dangerouslyDisableSandbox: true`** (Gradle daemon does not work in sandbox). To skip the Vue bundle in Kotlin-only runs, add `-x npmInstall -x npmUpgrade -x jsBuild`.
- Robolectric test annotations used in this repo: `@RunWith(RobolectricTestRunner::class)` and `@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])` where `TEST_SDK = 33`.

---

### Task 1: `VolumeButtonScroll` utility + unit tests

**Files:**
- Create: `app/src/main/java/net/bible/android/view/util/VolumeButtonScroll.kt`
- Test: `app/src/test/java/net/bible/android/view/util/VolumeButtonScrollTest.kt`

**Interfaces:**
- Consumes: nothing (leaf utility).
- Produces (used by Task 2):
  - `object VolumeButtonScroll`
  - `fun findScrollableView(root: View): View?`
  - `fun scroll(view: View, down: Boolean, animate: Boolean)`
  - `fun pageDelta(viewHeight: Int, down: Boolean): Int`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/net/bible/android/view/util/VolumeButtonScrollTest.kt`:

```kotlin
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
package net.bible.android.view.util

import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class VolumeButtonScrollTest {
    private val context get() = ApplicationProvider.getApplicationContext<TestBibleApplication>()

    /** Give a view a non-zero height so the visibility/size guard passes. */
    private fun <T : View> T.laidOut(h: Int = 200): T = apply { layout(0, 0, 100, h) }

    @Test
    fun `finds RecyclerView nested in a container`() {
        val recycler = RecyclerView(context).laidOut()
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), sameInstance<View>(recycler))
    }

    @Test
    fun `finds ListView (AbsListView) nested in a container`() {
        val list = ListView(context).laidOut()
        val root = LinearLayout(context).apply { addView(TextView(context).laidOut()); addView(list) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), instanceOf(ListView::class.java))
    }

    @Test
    fun `finds NestedScrollView`() {
        val scroll = NestedScrollView(context).laidOut()
        val root = LinearLayout(context).apply { addView(scroll) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), instanceOf(NestedScrollView::class.java))
    }

    @Test
    fun `skips GONE scrollable view`() {
        val recycler = RecyclerView(context).laidOut().apply { visibility = View.GONE }
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `skips zero-height scrollable view`() {
        val recycler = RecyclerView(context) // never laid out -> height 0
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `returns null when nothing scrollable`() {
        val root = LinearLayout(context).apply { addView(TextView(context).laidOut()) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `returns first scrollable in depth-first order`() {
        val first = ScrollView(context).laidOut()
        val second = RecyclerView(context).laidOut()
        val root = LinearLayout(context).apply { addView(first); addView(second) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), sameInstance<View>(first))
    }

    @Test
    fun `pageDelta scrolls down by ~90 percent of height`() {
        assertThat(VolumeButtonScroll.pageDelta(200, down = true), equalTo(180))
    }

    @Test
    fun `pageDelta scrolls up by negative ~90 percent of height`() {
        assertThat(VolumeButtonScroll.pageDelta(200, down = false), equalTo(-180))
    }

    @Test
    fun `pageDelta scales with height`() {
        assertThat(VolumeButtonScroll.pageDelta(1000, down = true), equalTo(900))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (with `dangerouslyDisableSandbox: true`):
`./gradlew testStandardGoogleplayDebugUnitTest --tests "*VolumeButtonScrollTest*" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: FAIL — compilation error, `VolumeButtonScroll` is unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/net/bible/android/view/util/VolumeButtonScroll.kt`:

```kotlin
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
package net.bible.android.view.util

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * Generic hardware volume-key page scrolling for list/scroll screens.
 *
 * Locates the first visible scrollable container in a view tree and page-scrolls it,
 * so e-ink users can navigate any [net.bible.android.view.activity.base.ActivityBase]
 * screen with the volume buttons, the same way the main BibleView already works.
 */
object VolumeButtonScroll {
    /** Fraction of the visible height scrolled per key press (leaves a small overlap). */
    private const val PAGE_FRACTION = 0.9

    /** Smooth-scroll animation duration (ms) for AbsListView, which needs an explicit duration. */
    private const val LIST_SMOOTH_DURATION = 250

    /**
     * Depth-first search for the first visible, laid-out scrollable container.
     * Type-based (not [View.canScrollVertically]) so the view is still found when
     * already scrolled to an edge, leaving the opposite direction usable.
     */
    fun findScrollableView(root: View): View? {
        if (root.visibility != View.VISIBLE || root.height <= 0) return null
        if (isScrollable(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findScrollableView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun isScrollable(view: View): Boolean =
        view is RecyclerView || view is AbsListView || view is ScrollView || view is NestedScrollView

    /** Signed page delta in pixels: positive scrolls down, negative scrolls up. */
    fun pageDelta(viewHeight: Int, down: Boolean): Int {
        val amount = (viewHeight * PAGE_FRACTION).toInt()
        return if (down) amount else -amount
    }

    /** Page-scroll [view] one screenful [down] or up, smoothly unless [animate] is false. */
    fun scroll(view: View, down: Boolean, animate: Boolean) {
        val delta = pageDelta(view.height, down)
        when (view) {
            is RecyclerView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is NestedScrollView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is ScrollView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is AbsListView -> if (animate) view.smoothScrollBy(delta, LIST_SMOOTH_DURATION) else view.scrollListBy(delta)
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (with `dangerouslyDisableSandbox: true`):
`./gradlew testStandardGoogleplayDebugUnitTest --tests "*VolumeButtonScrollTest*" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/util/VolumeButtonScroll.kt \
        app/src/test/java/net/bible/android/view/util/VolumeButtonScrollTest.kt
git commit -m "Add VolumeButtonScroll utility for generic volume-key page scrolling

Locates the first visible scrollable container (RecyclerView, AbsListView,
ScrollView, NestedScrollView) in a view tree and page-scrolls it.

Claude-Session: https://claude.ai/code/session_0171dmohTnmPqb5MeCwA4e1M"
```

---

### Task 2: Wire volume keys into `ActivityBase`, opt `MainBibleActivity` out, update string

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/base/ActivityBase.kt` (add import, flag, `onKeyDown` override; update copyright year to 2026)
- Modify: `app/src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt` (add opt-out flag; update copyright year to 2026)
- Modify: `app/src/main/res/values/strings.xml:1336` (summary string)

**Interfaces:**
- Consumes (from Task 1): `VolumeButtonScroll.findScrollableView(root)`, `VolumeButtonScroll.scroll(view, down, animate)`.
- Produces: `protected open val ActivityBase.enableGenericVolumeScroll: Boolean` (default `true`; `MainBibleActivity` overrides to `false`).

- [ ] **Step 1: Add the import to `ActivityBase.kt`**

In the import block (after `import android.os.Bundle`, keeping alphabetical-ish order with the other `android.*` imports), add:

```kotlin
import android.media.AudioManager
```

And after `import net.bible.android.view.util.UiUtils.setActionBarColor` add:

```kotlin
import net.bible.android.view.util.VolumeButtonScroll
```

- [ ] **Step 2: Add the opt-out flag and `onKeyDown` override to `ActivityBase`**

Insert just before the existing `onKeyLongPress` override (around line 227):

```kotlin
    /**
     * Whether this activity should let the base class handle volume-key page scrolling.
     * Screens that own the volume keys themselves (e.g. MainBibleActivity) override to false.
     */
    protected open val enableGenericVolumeScroll: Boolean get() = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (enableGenericVolumeScroll &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            CommonUtils.settings.getBoolean("volume_keys_scroll", true)
        ) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.isMusicActive != true) {
                val root = findViewById<View>(android.R.id.content)
                val scrollable = root?.let { VolumeButtonScroll.findScrollableView(it) }
                if (scrollable != null) {
                    VolumeButtonScroll.scroll(
                        scrollable,
                        down = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN,
                        animate = !CommonUtils.settings.disableAnimations
                    )
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
```

- [ ] **Step 3: Update the `ActivityBase.kt` copyright year**

The header is already `Copyright (c) 2020-2026 ...` — verify it ends in `2026`; if it shows an older end year, change it to `2026`. (No change needed if already `2026`.)

- [ ] **Step 4: Opt `MainBibleActivity` out**

In `app/src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt`, near the other class-level `override val` declarations (top of the class body), add:

```kotlin
    override val enableGenericVolumeScroll: Boolean get() = false
```

Verify the copyright header end year is `2026`; update if older. Do NOT change the existing `onKeyDown` in `MainBibleActivity` — it remains the sole owner of volume keys there.

- [ ] **Step 5: Update the summary string**

In `app/src/main/res/values/strings.xml`, replace line 1336:

```xml
    <string name="prefs_volume_keys_scroll_summary">Use volume up/down to scroll Bible text</string>
```

with:

```xml
    <string name="prefs_volume_keys_scroll_summary">Use volume up/down to scroll Bible text and lists</string>
```

- [ ] **Step 6: Compile and run the unit test suite to verify nothing broke**

Run (with `dangerouslyDisableSandbox: true`):
`./gradlew testStandardGoogleplayDebugUnitTest --tests "*VolumeButtonScrollTest*" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS, and the modules compile (the `ActivityBase`/`MainBibleActivity` edits compile as part of the test build).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/base/ActivityBase.kt \
        app/src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "Volume buttons scroll lists in all ActivityBase screens

Generalize e-ink volume up/down page scrolling beyond BibleView to every
ActivityBase-derived screen via VolumeButtonScroll, gated by the existing
volume_keys_scroll setting and a music-active guard. MainBibleActivity opts
out to keep its WebView/JS path. Honours the disable-animations setting.

Claude-Session: https://claude.ai/code/session_0171dmohTnmPqb5MeCwA4e1M"
```

---

## Self-Review

**Spec coverage:**
- VolumeButtonScroll utility (findScrollableView type-based + visible/laid-out guard; scroll per-type dispatch; ~90% page delta) → Task 1. ✓
- ActivityBase `enableGenericVolumeScroll` flag + `onKeyDown` (setting gate, music guard, `disableAnimations` → animate, consume on success) → Task 2 Steps 1-2. ✓
- MainBibleActivity opt-out → Task 2 Step 4. ✓
- Reuse `volume_keys_scroll`, no new setting → honoured (no preference added). ✓
- Summary string update, English only → Task 2 Step 5. ✓
- Tests (find right view; skip invisible/zero-height; null when none; first DFS match; delta sign/magnitude) → Task 1 Step 1. ✓
- Out of scope (BibleView mechanism, item-by-item, new setting, translations) → none added. ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases"; all code shown in full. ✓

**Type consistency:** `findScrollableView(root: View): View?`, `scroll(view, down, animate)`, `pageDelta(viewHeight, down)`, and `enableGenericVolumeScroll` names match between Task 1 (definition/tests) and Task 2 (call sites). ✓
