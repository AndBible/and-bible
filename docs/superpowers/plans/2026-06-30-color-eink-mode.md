# Color e-ink mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a third color treatment — `normal` / `bw` / `color_eink` — where color e-ink keeps the monochrome (black & white) base but selectively shows color for bookmarks, the active-window indicator, scroll helper lines and the top-margin line.

**Architecture:** Replace the boolean `monochrome_mode` setting with a tri-state `display_color_mode`. Internally `colorEinkMode ⇒ monochromeMode`, so all existing grayscale code is untouched; only the four accent points add a `colorEinkMode` override (the grayscale branch becomes "monochrome AND NOT colorEink"). The four accents reuse one unified accent blue.

**Tech Stack:** Kotlin/Android (Room settings DAOs, AndroidX preferences), Vue 3 + TypeScript (BibleView WebView), SCSS, Vitest.

## Global Constraints

- New files use the 2026 copyright header: "Sykerö Software / Tuomas Airaksinen and the AndBible contributors" (NO "Martin Denham" in new files). Existing files: bump year to 2026 and use "Sykerö Software / Tuomas Airaksinen" (keep "Martin Denham" if already present).
- Java/Kotlin: import classes, use simple names (no fully-qualified names in code).
- All user-facing strings go through the translation system; **English only** (no other languages).
- Do NOT use `isolation: worktree`. All work on the current branch `color-eink-mode`.
- Gradle commands require `dangerouslyDisableSandbox: true`.
- Run only the tests relevant to the change: Vue tests for TS/Vue changes, Android unit tests for Kotlin changes.
- Default `display_color_mode`: `bw` on Onyx devices, `normal` elsewhere. Color e-ink is always an explicit user choice (no auto-detection).
- Accent blue (reuse existing literals): day `rgba(0, 0, 255, 0.6)`, night `rgba(196, 196, 255, 0.8)`.

---

## File Structure

- `app/src/main/java/net/bible/service/common/DisplayColorMode.kt` — **new** — the tri-state enum (pure, testable).
- `app/src/main/java/net/bible/service/common/CommonUtils.kt` — modify — derived `displayColorMode` / `monochromeMode` / `colorEinkMode`; one-time migration.
- `app/src/test/java/net/bible/service/common/DisplayColorModeTest.kt` — **new** — unit test for the enum parsing.
- `app/src/main/res/xml/settings.xml` — modify — switch → ListPreference.
- `app/src/main/res/values/strings.xml` — modify — new strings.
- `app/src/main/res/values/arrays.xml` — modify — new string-arrays.
- `app/src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt` — modify — reset-key list + device default on the ListPreference.
- `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt` — modify — pass `colorEinkMode` to the WebView.
- `app/bibleview-js/src/composables/config.ts` — modify — `colorEinkMode` in `AppSettings` type + reactive init.
- `app/bibleview-js/src/composables/bookmarks.ts` — modify — extract `bookmarkHighlightColor`, accent overrides for highlight/underline/markers.
- `app/bibleview-js/src/__tests__/bookmarks.spec.js` — modify — tests for the three modes.
- `app/bibleview-js/src/components/BibleView.vue` — modify — `colorEink` class + SCSS accent overrides.

---

## Task 1: Settings data model + migration (Android)

**Files:**
- Create: `app/src/main/java/net/bible/service/common/DisplayColorMode.kt`
- Modify: `app/src/main/java/net/bible/service/common/CommonUtils.kt:453` (replace `monochromeMode`), and `:1748-1753` area (add migration before the closing brace of `migrateOldSettingsKeys`)
- Test: `app/src/test/java/net/bible/service/common/DisplayColorModeTest.kt`

**Interfaces:**
- Produces:
  - `enum class DisplayColorMode(val value: String) { NORMAL("normal"), BW("bw"), COLOR_EINK("color_eink") }` with `companion object { fun fromValue(v: String?): DisplayColorMode? }` (returns `null` for null/unknown).
  - `CommonUtils.settings.displayColorMode: DisplayColorMode`
  - `CommonUtils.settings.monochromeMode: Boolean` (now `displayColorMode != NORMAL`)
  - `CommonUtils.settings.colorEinkMode: Boolean` (`displayColorMode == COLOR_EINK`)

- [ ] **Step 1: Create the enum file**

Create `app/src/main/java/net/bible/service/common/DisplayColorMode.kt`:

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

package net.bible.service.common

/**
 * Display color treatment for the app.
 *
 * - [NORMAL]: full color.
 * - [BW]: monochrome (black & white) — the whole UI is grayscale.
 * - [COLOR_EINK]: monochrome base but selected accents (bookmark colors, active-window
 *   indicator, scroll helper lines, top-margin line) are shown in color. Intended for
 *   color e-ink screens where high contrast still matters.
 */
enum class DisplayColorMode(val value: String) {
    NORMAL("normal"),
    BW("bw"),
    COLOR_EINK("color_eink");

    companion object {
        /** Returns null for a null or unrecognized value so callers can apply a device default. */
        fun fromValue(v: String?): DisplayColorMode? = values().firstOrNull { it.value == v }
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/net/bible/service/common/DisplayColorModeTest.kt`:

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

package net.bible.service.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayColorModeTest {
    @Test
    fun parsesKnownValues() {
        assertEquals(DisplayColorMode.NORMAL, DisplayColorMode.fromValue("normal"))
        assertEquals(DisplayColorMode.BW, DisplayColorMode.fromValue("bw"))
        assertEquals(DisplayColorMode.COLOR_EINK, DisplayColorMode.fromValue("color_eink"))
    }

    @Test
    fun returnsNullForUnknownOrNull() {
        assertNull(DisplayColorMode.fromValue(null))
        assertNull(DisplayColorMode.fromValue("garbage"))
        assertNull(DisplayColorMode.fromValue(""))
    }
}
```

- [ ] **Step 3: Run the test (must pass — enum already created in Step 1)**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*DisplayColorModeTest"
```
Expected: PASS.

- [ ] **Step 4: Replace the `monochromeMode` getter in CommonUtils.kt**

In `app/src/main/java/net/bible/service/common/CommonUtils.kt`, replace line 453:

```kotlin
        val monochromeMode: Boolean get() = getBoolean("monochrome_mode", isOnyxDevice)
```

with:

```kotlin
        val displayColorMode: DisplayColorMode get() =
            DisplayColorMode.fromValue(getString("display_color_mode", null))
                ?: if (isOnyxDevice) DisplayColorMode.BW else DisplayColorMode.NORMAL
        val monochromeMode: Boolean get() = displayColorMode != DisplayColorMode.NORMAL
        val colorEinkMode: Boolean get() = displayColorMode == DisplayColorMode.COLOR_EINK
```

(`DisplayColorMode` is in the same package `net.bible.service.common`, so no import is needed.)

- [ ] **Step 5: Add the one-time migration**

In `migrateOldSettingsKeys()`, immediately before its closing brace (currently after the `gdrive_sync_interval` block at `CommonUtils.kt:1753`), add:

```kotlin
        // Migrate boolean monochrome_mode → tri-state display_color_mode
        val strDao = stringSettings
        if (strDao.byKey("display_color_mode") == null) {
            val oldMono = boolDao.byKey("monochrome_mode")
            if (oldMono != null) {
                val newValue = if (oldMono.value) "bw" else "normal"
                Log.i(TAG, "Migrating 'monochrome_mode'=${oldMono.value} → 'display_color_mode'=$newValue")
                strDao.set("display_color_mode", newValue)
                boolDao.set("monochrome_mode", null)
            }
        }
```

(`boolDao`/`stringSettings`/`Log`/`TAG` are already in scope in this function.)

- [ ] **Step 6: Verify compilation + tests still pass**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*DisplayColorModeTest"
```
Expected: PASS, no compile errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/service/common/DisplayColorMode.kt \
        app/src/test/java/net/bible/service/common/DisplayColorModeTest.kt \
        app/src/main/java/net/bible/service/common/CommonUtils.kt
git commit -m "Add display_color_mode tri-state setting and monochrome migration"
```

---

## Task 2: Settings UI (ListPreference + strings)

**Files:**
- Modify: `app/src/main/res/xml/settings.xml:195-200`
- Modify: `app/src/main/res/values/strings.xml` (near line 118-120)
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt:164` and `onCreatePreferences`

**Interfaces:**
- Consumes: the `display_color_mode` key + the device-default rule from Task 1.
- Produces: a working `ListPreference` with values `normal`/`bw`/`color_eink`.

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, add (next to the existing `prefs_eink_*` strings around line 120):

```xml
    <string name="prefs_display_color_mode_title">Color mode</string>
    <string name="prefs_display_color_mode_summary">Choose how colors are displayed. Color e-ink keeps a black &amp; white base but shows bookmark colors, the active-window indicator and helper lines in color — for color e-ink screens.</string>
    <string name="prefs_display_color_mode_normal">Normal</string>
    <string name="prefs_display_color_mode_bw">Black &amp; white</string>
    <string name="prefs_display_color_mode_color_eink">Color e-ink</string>
```

- [ ] **Step 2: Add string-arrays**

In `app/src/main/res/values/arrays.xml`, add (e.g. after the night-mode arrays around line 116):

```xml
	<string-array name="prefs_display_color_mode_names">
		<item>@string/prefs_display_color_mode_normal</item>
		<item>@string/prefs_display_color_mode_bw</item>
		<item>@string/prefs_display_color_mode_color_eink</item>
	</string-array>
	<string-array name="prefs_display_color_mode_values">
		<item>normal</item>
		<item>bw</item>
		<item>color_eink</item>
	</string-array>
```

- [ ] **Step 3: Replace the switch with a ListPreference**

In `app/src/main/res/xml/settings.xml`, replace lines 195-200:

```xml
		<SwitchPreferenceCompat android:key="monochrome_mode"
			android:title="@string/prefs_e_ink_mode_title"
			android:summary="@string/prefs_eink_mode_summary"
			android:defaultValue="false"
			android:icon="@drawable/ic_eink_24dp"
			/>
```

with:

```xml
		<ListPreference android:key="display_color_mode"
			android:title="@string/prefs_display_color_mode_title"
			android:summary="@string/prefs_display_color_mode_summary"
			android:entries="@array/prefs_display_color_mode_names"
			android:entryValues="@array/prefs_display_color_mode_values"
			android:defaultValue="normal"
			android:icon="@drawable/ic_eink_24dp"
			/>
```

- [ ] **Step 4: Update the reset-key list**

In `app/src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt:164`, replace:

```kotlin
                    "monochrome_mode",
```

with:

```kotlin
                    "display_color_mode",
```

- [ ] **Step 5: Set the device default on the ListPreference**

In `SettingsActivity.SettingsFragment.onCreatePreferences`, after the existing `night_mode_pref3` block (around line 242), add:

```kotlin
        val displayColorModePref = preferenceScreen.findPreference<ListPreference>("display_color_mode") as ListPreference
        displayColorModePref.setDefaultValue(if (CommonUtils.isOnyxDevice) "bw" else "normal")
```

(`ListPreference` and `CommonUtils` are already imported in this file — verify; add the import if missing, using the simple name.)

- [ ] **Step 6: Verify compilation**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew compileStandardGoogleplayDebugSources
```
Expected: BUILD SUCCESSFUL (resources + Kotlin compile, no missing-symbol errors).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/xml/settings.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values/arrays.xml \
        app/src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt
git commit -m "Replace b&w switch with tri-state color-mode ListPreference"
```

---

## Task 3: Pass colorEinkMode to the WebView

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt:1508` and `:1531`
- Modify: `app/bibleview-js/src/composables/config.ts:134` (type) and `:243` (reactive init)

**Interfaces:**
- Consumes: `CommonUtils.settings.colorEinkMode` (Task 1).
- Produces: `appSettings.colorEinkMode: boolean` available in the Vue layer (used by Tasks 4 & 5).

- [ ] **Step 1: Read the flag in BibleView.kt**

In `getUpdateConfigCommand()`, after the `einkMode` line (`BibleView.kt:1508`):

```kotlin
        val einkMode = CommonUtils.settings.einkMode
```

add:

```kotlin
        val colorEinkMode = CommonUtils.settings.colorEinkMode
```

- [ ] **Step 2: Emit the flag in the appSettings object**

In the same function, after the `einkMode: $einkMode,` line (`BibleView.kt:1531`):

```kotlin
                        einkMode: $einkMode,
```

add:

```kotlin
                        colorEinkMode: $colorEinkMode,
```

- [ ] **Step 3: Add to the AppSettings type**

In `app/bibleview-js/src/composables/config.ts`, after `monochromeMode: boolean,` (line 134):

```typescript
    monochromeMode: boolean,
    colorEinkMode: boolean,
```

- [ ] **Step 4: Initialize in the reactive object**

In `config.ts`, in the `appSettings` reactive object, after `monochromeMode: false,` (line ~243):

```typescript
        monochromeMode: false,
        colorEinkMode: false,
```

(The existing `set_config` listener iterates over incoming keys generically, so no listener change is needed.)

- [ ] **Step 5: Verify type-check**

Run:
```bash
cd app/bibleview-js && npm run type-check
```
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/page/BibleView.kt \
        app/bibleview-js/src/composables/config.ts
git commit -m "Plumb colorEinkMode flag from Kotlin settings to BibleView config"
```

---

## Task 4: Bookmark accent colors (bookmarks.ts) + tests

**Files:**
- Modify: `app/bibleview-js/src/composables/bookmarks.ts:164-166` (underline), `:633-647` (extract highlight color), `:799`, `:830`, `:921` (marker icons)
- Test: `app/bibleview-js/src/__tests__/bookmarks.spec.js`

**Interfaces:**
- Consumes: `appSettings.colorEinkMode` (Task 3), existing `appSettings.monochromeMode`, `appSettings.nightMode`.
- Produces: `export function bookmarkHighlightColor(label: LabelAndStyle, count: number, appSettings: AppSettings): Color`.

- [ ] **Step 1: Write the failing tests**

In `app/bibleview-js/src/__tests__/bookmarks.spec.js`, update the import line (currently imports `useBookmarks, useGlobalBookmarks, verseHighlighting`) to also import the new function:

```javascript
import {useBookmarks, useGlobalBookmarks, verseHighlighting, bookmarkHighlightColor} from "@/composables/bookmarks";
```

and add a new `describe` block:

```javascript
describe("color e-ink accent colors", () => {
    const label = {color: 0xFF0000};

    it("highlight: bw mode returns gray, color-eink returns the real color (day)", () => {
        const normal = bookmarkHighlightColor(label, 1, {monochromeMode: false, colorEinkMode: false, nightMode: false});
        const bw = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: false, nightMode: false});
        const colorEink = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: true, nightMode: false});
        expect(bw.hex()).toEqual("#D2D2D2");          // 210,210,210
        expect(colorEink.string()).toEqual(normal.string());
        expect(colorEink.hex()).not.toEqual(bw.hex());
    });

    it("highlight: bw night mode returns darker gray", () => {
        const bwNight = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: false, nightMode: true});
        expect(bwNight.hex()).toEqual("#B4B4B4");      // 180,180,180
    });

    function underlineCss(appSettings) {
        return verseHighlighting({
            highlightLabels: [],
            highlightLabelCount: new Map(),
            underlineLabels: [{label: {color: 0xFF0000}, id: 1}],
            underlineLabelCount: new Map([[1, 1]]),
            highlightColorFn: (v) => Color(v.color),
            appSettings,
        });
    }

    it("underline: bw mode uses black, color-eink uses the label color (day)", () => {
        const bw = underlineCss({monochromeMode: true, colorEinkMode: false, nightMode: false});
        const colorEink = underlineCss({monochromeMode: true, colorEinkMode: true, nightMode: false});
        expect(bw).toContain(Color("black").string());
        expect(colorEink).toContain(new Color(0xFF0000).hsl().string());
        expect(colorEink).not.toContain(Color("black").string());
    });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd app/bibleview-js && npm run test:ci -- bookmarks
```
Expected: FAIL — `bookmarkHighlightColor` is not exported, and color-eink branches not yet implemented.

- [ ] **Step 3: Extract and export `bookmarkHighlightColor`**

In `app/bibleview-js/src/composables/bookmarks.ts`, add this top-level exported function (e.g. directly after the `verseHighlighting` function, before `AI_DOC_COLOR` at line 197):

```typescript
export function bookmarkHighlightColor(label: LabelAndStyle, count: number, appSettings: AppSettings): Color {
    if (appSettings.monochromeMode && !appSettings.colorEinkMode) {
        return appSettings.nightMode ? Color.rgb(180, 180, 180) : Color.rgb(210, 210, 210);
    }
    let c = new Color(label.color);
    c = c.alpha(appSettings.nightMode ? 0.4 : 0.3);
    for (let i = 0; i < count - 1; i++) {
        c = c.opaquer(0.3).darken(0.2);
    }
    return c;
}
```

Then replace the in-`useBookmarks` definition at lines 633-647:

```typescript
    const monochromeHighlightColor = appSettings.nightMode
        ? Color.rgb(180, 180, 180)
        : Color.rgb(210, 210, 210);

    function highlightColor(label: LabelAndStyle, count: number): Color {
        if (appSettings.monochromeMode) {
            return monochromeHighlightColor;
        }
        let c = new Color(label.color)
        c = c.alpha(appSettings.nightMode ? 0.4 : 0.3)
        for (let i = 0; i < count - 1; i++) {
            c = c.opaquer(0.3).darken(0.2);
        }
        return c;
    }
```

with:

```typescript
    function highlightColor(label: LabelAndStyle, count: number): Color {
        return bookmarkHighlightColor(label, count, appSettings);
    }
```

- [ ] **Step 4: Update the underline branch**

At `bookmarks.ts:164-166`, replace:

```typescript
                const color = appSettings.monochromeMode
                    ? monochromeUnderlineColor
                    : new Color(s.color).hsl();
```

with:

```typescript
                const color = (appSettings.monochromeMode && !appSettings.colorEinkMode)
                    ? monochromeUnderlineColor
                    : new Color(s.color).hsl();
```

- [ ] **Step 5: Update the three marker-icon color branches**

At `bookmarks.ts:799`, replace:

```typescript
                    const color = adjustedColor(appSettings.monochromeMode ? "black" : "red").string();
```

with:

```typescript
                    const color = adjustedColor((appSettings.monochromeMode && !appSettings.colorEinkMode) ? "black" : "red").string();
```

At `bookmarks.ts:830` and `bookmarks.ts:921` (identical lines), replace each:

```typescript
                const color = adjustedColor(appSettings.monochromeMode ? "black" : bookmarkLabel.color).string();
```

with:

```typescript
                const color = adjustedColor((appSettings.monochromeMode && !appSettings.colorEinkMode) ? "black" : bookmarkLabel.color).string();
```

(There are exactly three marker spots: 799, 830, 921. Verify with `grep -n 'appSettings.monochromeMode ? ' src/composables/bookmarks.ts` — it should return no matches after this step.)

- [ ] **Step 6: Run the tests to verify they pass**

Run:
```bash
cd app/bibleview-js && npm run test:ci -- bookmarks
```
Expected: PASS (all bookmark tests, including the new block).

- [ ] **Step 7: Lint + type-check**

Run:
```bash
cd app/bibleview-js && npm run lint && npm run type-check
```
Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add app/bibleview-js/src/composables/bookmarks.ts \
        app/bibleview-js/src/__tests__/bookmarks.spec.js
git commit -m "Show bookmark colors in color e-ink mode"
```

---

## Task 5: CSS accents — active-window indicator, scroll helper lines, top margin

**Files:**
- Modify: `app/bibleview-js/src/components/BibleView.vue:21` (class binding), `:506-508` (SCSS vars), `:510-585` (indicator), `:587-608` (top-margin), `:749-771` (scroll helper lines)

**Interfaces:**
- Consumes: `appSettings.colorEinkMode` (Task 3).
- Produces: a `colorEink` body class and SCSS overrides reusing `$colorEinkAccent` / `$colorEinkAccentNight`.

> No unit test: this is CSS rendering, verified by build + the manual theme matrix in Task 6. Normal and plain b&w modes must remain visually identical to today.

- [ ] **Step 1: Add the `colorEink` body class**

In `BibleView.vue:21`, replace:

```html
      :class="{night: appSettings.nightMode, noAnimation: appSettings.disableAnimations, monochrome: appSettings.monochromeMode}"
```

with:

```html
      :class="{night: appSettings.nightMode, noAnimation: appSettings.disableAnimations, monochrome: appSettings.monochromeMode, colorEink: appSettings.colorEinkMode}"
```

- [ ] **Step 2: Add shared accent SCSS variables**

In `BibleView.vue`, after line 508 (`$borderDistance: 0;`), add:

```scss
$colorEinkAccent: rgba(0, 0, 255, 0.6);
$colorEinkAccentNight: rgba(196, 196, 255, 0.8);
```

- [ ] **Step 3: Restore color on the active-window corner indicator**

In `.active-window-corner`, after the existing monochrome rules (after line 528, `.monochrome.night & { border-color: white; }`), add (so the colorEink rules come later in source and win over `.monochrome &`):

```scss
  .colorEink & {
    border-color: $colorEinkAccent;
  }
  .colorEink.night & {
    border-color: $colorEinkAccentNight;
  }
```

- [ ] **Step 4: Restore color on the full active-window indicator border**

In `.active-window-indicator`, after the existing monochrome rules (after line 584, `.monochrome.night & { border-color: white; }`), add:

```scss
  .colorEink & {
    border-color: $colorEinkAccent;
  }
  .colorEink.night & {
    border-color: $colorEinkAccentNight;
  }
```

- [ ] **Step 5: Color the top-margin line**

In `.top-margin`, after the existing `.night.noAnimation &` rule (after line 607), add:

```scss
  .colorEink & {
    background-color: rgba(0, 0, 255, 0.25);
  }
  .colorEink.night & {
    background-color: rgba(196, 196, 255, 0.35);
  }
  .colorEink.noAnimation & {
    background-color: unset;
    border-bottom: 1px dashed $colorEinkAccent;
  }
  .colorEink.night.noAnimation & {
    border-bottom: 1px dashed $colorEinkAccentNight;
  }
```

- [ ] **Step 6: Color the scroll helper lines**

In `.scroll-helper-line`, after the three style-variant blocks (after line 770, the `&.helper-line-thick-solid` block), add:

```scss
  .colorEink & {
    border-top-color: $colorEinkAccent;
    opacity: 1;
  }
  .colorEink.night & {
    border-top-color: $colorEinkAccentNight;
  }
```

- [ ] **Step 7: Build + lint to verify the SCSS compiles**

Run:
```bash
cd app/bibleview-js && npm run lint && npm run build-debug
```
Expected: build succeeds, no SCSS errors.

- [ ] **Step 8: Commit**

```bash
git add app/bibleview-js/src/components/BibleView.vue
git commit -m "Color active-window indicator, helper lines and top margin in color e-ink mode"
```

---

## Task 6: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Full Vue.js validation**

Run:
```bash
cd app/bibleview-js && npm run test:ci && npm run lint && npm run type-check
```
Expected: all tests pass, no lint/type errors.

- [ ] **Step 2: Android unit test**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*DisplayColorModeTest"
```
Expected: PASS.

- [ ] **Step 3: Manual theme-matrix checklist (document results)**

Verify in the app (or describe how it would be verified if no device is available). For each combination, the four accent categories and that `normal`/`bw` are unchanged from today:

- `normal` mode — day & night: full color everywhere (unchanged).
- `bw` mode — day & night: everything grayscale (unchanged); bookmarks gray, indicator black/white, helper lines + top margin use text color.
- `color_eink` mode — day & night, animations on & off:
  - bookmark highlights/underlines/markers show the label's real color
  - active-window indicator (corners + border) shows the accent blue (light blue at night)
  - scroll helper lines (with `eink_mode` on) show the accent blue
  - top-margin line shows the accent blue (solid bar with animations on; dashed blue border with animations off)
  - reading text/background remain high-contrast black/white

- [ ] **Step 4: Migration sanity check (document)**

Confirm the upgrade path: a user who previously had `monochrome_mode = true` should land on `display_color_mode = "bw"` after upgrade (and the old key removed); `monochrome_mode = false` → `normal`; a fresh Onyx install defaults to `bw`, fresh non-Onyx defaults to `normal`.

- [ ] **Step 5: Final commit (if any doc/notes were added)**

```bash
git add -A && git commit -m "Verify color e-ink mode across theme matrix" || echo "nothing to commit"
```

---

## Self-Review notes

- **Spec coverage:** data model + migration (Task 1), settings UI/strings (Task 2), Kotlin→Vue plumbing (Task 3), bookmarks (Task 4), active-window indicator + scroll helper lines + top margin (Task 5), testing + theme matrix (Tasks 4/6). Native chrome intentionally stays monochrome (no task needed — `monochromeMode` is true for color e-ink). Out-of-scope items match the spec.
- **Type consistency:** `bookmarkHighlightColor(label, count, appSettings)` is defined in Task 4 and used only there. `colorEinkMode` added to the type (Task 3) before first use (Tasks 4/5). `DisplayColorMode` / `displayColorMode` / `monochromeMode` / `colorEinkMode` names consistent across Tasks 1–3.
- **Placeholders:** none — all steps contain concrete code and exact commands.
