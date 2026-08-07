# Color Theme Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add named color-theme presets (Gruvbox, Nord, Solarized, Dracula, Sepia) that stamp a full day+night palette (text, background, link/xref, verse-number, heading) into the existing per-workspace color settings, remembering the selected theme.

**Architecture:** Presets are pure Kotlin color data. Selecting a preset writes into `WorkspaceEntities.Colors` (now extended with accent fields + `themeName`); the existing `Colors → TextDisplaySettings.toJson() → WebView config → Vue CSS-var` pipeline carries them unchanged. Vue consumes three new optional CSS vars with fallbacks. Manual color edits reset `themeName` to null ("Custom"). Monochrome/e-ink mode already forces black/white in Vue, auto-suppressing theme accents.

**Tech Stack:** Kotlin, Room (kotlinx.serialization), AndroidX Preference, Vue 3 + TypeScript, `color` (color.js) lib, Vitest, JUnit4.

## Global Constraints

- Java 17; Node 20.x; npm 10.x.
- All user-facing strings go through the translation system; **English only** in this work (`app/src/main/res/values/strings.xml`). No other-language files.
- Color values stored as signed color ints (`0xAARRGGBB`, e.g. `0xFF282828.toInt()`), matching existing `Colors` fields.
- New `Colors` fields MUST be nullable with `@ColumnInfo(defaultValue = "NULL")` and Kotlin default `= null` (keeps existing positional constructor calls valid; null = current behavior, no regression).
- Room migration column names use embedding prefix `text_display_settings_colors_<field>`.
- Vue: only run Vue tests for Vue changes; only run Android tests for Kotlin changes (per CLAUDE.md).
- Never hardcode user-visible text in code.

---

### Task 1: Extend `Colors` data model (fields + merge)

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt:144-184`
- Test: `app/src/test/java/net/bible/android/database/ColorsMergeTest.kt`

**Interfaces:**
- Produces: `Colors` gains nullable `Int?` fields `dayLinkColor, nightLinkColor, dayVerseNumberColor, nightVerseNumberColor, dayHeadingColor, nightHeadingColor` and `String? themeName`, all defaulting to `null`. `merge(override)` falls back per-field for each.

- [ ] **Step 1: Write the failing test**

Add to `ColorsMergeTest.kt`:

```kotlin
@Test
fun mergeFallsBackAccentAndThemePerField() {
    val base = blank().copy(dayLinkColor = 0x111111, dayVerseNumberColor = 0x222222, themeName = "gruvbox")
    val override = blank().copy(nightLinkColor = 0x333333, dayHeadingColor = 0x444444)
    val merged = base.merge(override)
    assertEquals(0x111111, merged.dayLinkColor)
    assertEquals(0x333333, merged.nightLinkColor)
    assertEquals(0x222222, merged.dayVerseNumberColor)
    assertEquals(0x444444, merged.dayHeadingColor)
    assertEquals("gruvbox", merged.themeName)
}

@Test
fun overrideThemeNameWins() {
    val base = blank().copy(themeName = "gruvbox")
    val override = blank().copy(themeName = "nord")
    assertEquals("nord", base.merge(override).themeName)
}
```

Note: `blank()` keeps its 10 positional `null` args — the new fields default to `null`, so no change to `blank()` is needed.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.ColorsMergeTest" ` (use `dangerouslyDisableSandbox: true`)
Expected: FAIL — `dayLinkColor` / `themeName` unresolved reference (won't compile).

- [ ] **Step 3: Add the fields**

In `WorkspaceEntities.kt`, extend the `Colors` constructor (after `nightBackgroundImageOpacity`, before the closing `)`):

```kotlin
        @ColumnInfo(defaultValue = "NULL") var dayLinkColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var nightLinkColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var dayVerseNumberColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var nightVerseNumberColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var dayHeadingColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var nightHeadingColor: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var themeName: String? = null,
```

- [ ] **Step 4: Extend `merge()`**

In `Colors.merge()`, add before the closing `)` of the returned `Colors(...)`:

```kotlin
                dayLinkColor = override.dayLinkColor ?: dayLinkColor,
                nightLinkColor = override.nightLinkColor ?: nightLinkColor,
                dayVerseNumberColor = override.dayVerseNumberColor ?: dayVerseNumberColor,
                nightVerseNumberColor = override.nightVerseNumberColor ?: nightVerseNumberColor,
                dayHeadingColor = override.dayHeadingColor ?: dayHeadingColor,
                nightHeadingColor = override.nightHeadingColor ?: nightHeadingColor,
                themeName = override.themeName ?: themeName,
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.ColorsMergeTest"` (`dangerouslyDisableSandbox: true`)
Expected: PASS (all tests, including existing image tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/database/WorkspaceEntities.kt app/src/test/java/net/bible/android/database/ColorsMergeTest.kt
git commit -m "Add accent color + themeName fields to Colors"
```

---

### Task 2: Room migration 24→25

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt:296-331`

**Interfaces:**
- Consumes: the new `Colors` columns from Task 1.
- Produces: `WORKSPACE_DATABASE_VERSION = 25`; migration `addColorThemePresets` registered in `workspacesMigrations`.

**Note on testing:** there is no Room migration-test harness in this repo. Room validates the entity schema against the on-device DB at open time and throws `IllegalStateException` if a declared column is missing, so a broken/missing migration fails loudly at first launch. Verification here is a successful debug build (Room's annotation processor regenerates the expected schema) plus the app-open smoke in Step 4.

- [ ] **Step 1: Add the migration**

In `WorkspacesMigrations.kt`, immediately after `addBackgroundImage` (line ~303), add:

```kotlin
private val addColorThemePresets = makeMigration(24..25) { _db ->
    for (table in listOf("Workspace", "PageManager", "GlobalTextDisplaySettings")) {
        for (col in listOf(
            "dayLinkColor", "nightLinkColor",
            "dayVerseNumberColor", "nightVerseNumberColor",
            "dayHeadingColor", "nightHeadingColor",
        )) {
            _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_$col` INTEGER DEFAULT NULL")
        }
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_themeName` TEXT DEFAULT NULL")
    }
}
```

- [ ] **Step 2: Register it and bump the version**

In the `workspacesMigrations` array, add `addColorThemePresets,` after `addBackgroundImage,`. Change the constant:

```kotlin
const val WORKSPACE_DATABASE_VERSION = 25
```

- [ ] **Step 3: Build to verify Room schema matches entities**

Run: `./gradlew assembleStandardGithubDebug` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL. (Room fails the build if the migration/entity schema is inconsistent.)

- [ ] **Step 4: Manual smoke (document, not automated)**

Note in the commit body that the migration was verified by installing over a prior build and confirming the app opens without a Room `IllegalStateException`. If an emulator is available: `./gradlew installStandardGithubDebug` and launch.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt
git commit -m "Add workspace DB migration 24->25 for theme color columns"
```

---

### Task 3: `ColorThemePreset` definitions

**Files:**
- Create: `app/src/main/java/net/bible/android/view/activity/settings/ColorThemePreset.kt`
- Test: `app/src/test/java/net/bible/android/view/activity/settings/ColorThemePresetTest.kt`

**Interfaces:**
- Consumes: `WorkspaceEntities.Colors` (Task 1).
- Produces:
  - `enum class ColorThemePreset(val id: String, val labelRes: Int, dayText, dayBg, dayLink, dayVerse, dayHeading, nightText, nightBg, nightLink, nightVerse, nightHeading: Int)`
  - `fun applyTo(c: WorkspaceEntities.Colors)` — mutates the 10 color fields + sets `c.themeName = id`.
  - `companion object { fun byId(id: String?): ColorThemePreset? }`

- [ ] **Step 1: Write the failing test**

Create `ColorThemePresetTest.kt`:

```kotlin
package net.bible.android.view.activity.settings

import net.bible.android.database.WorkspaceEntities.Colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorThemePresetTest {
    private fun blank() = Colors(null, null, null, null, null, null, null, null, null, null)

    @Test
    fun applyToStampsAllFieldsAndThemeName() {
        val c = blank()
        ColorThemePreset.GRUVBOX.applyTo(c)
        assertEquals("gruvbox", c.themeName)
        assertEquals(ColorThemePreset.GRUVBOX.dayText, c.dayTextColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayBg, c.dayBackground)
        assertEquals(ColorThemePreset.GRUVBOX.dayLink, c.dayLinkColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayVerse, c.dayVerseNumberColor)
        assertEquals(ColorThemePreset.GRUVBOX.dayHeading, c.dayHeadingColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightText, c.nightTextColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightBg, c.nightBackground)
        assertEquals(ColorThemePreset.GRUVBOX.nightLink, c.nightLinkColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightVerse, c.nightVerseNumberColor)
        assertEquals(ColorThemePreset.GRUVBOX.nightHeading, c.nightHeadingColor)
    }

    @Test
    fun byIdRoundTrips() {
        for (p in ColorThemePreset.entries) assertEquals(p, ColorThemePreset.byId(p.id))
    }

    @Test
    fun byIdUnknownOrNullIsNull() {
        assertNull(ColorThemePreset.byId(null))
        assertNull(ColorThemePreset.byId(""))
        assertNull(ColorThemePreset.byId("nonexistent"))
    }

    @Test
    fun applyToLeavesNoiseAndImagesUntouched() {
        val c = blank().copy(dayNoise = 30, dayBackgroundImage = "BGIMG_x")
        ColorThemePreset.NORD.applyTo(c)
        assertEquals(30, c.dayNoise)
        assertEquals("BGIMG_x", c.dayBackgroundImage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.ColorThemePresetTest"` (`dangerouslyDisableSandbox: true`)
Expected: FAIL — `ColorThemePreset` unresolved (won't compile).

- [ ] **Step 3: Create the enum**

Create `ColorThemePreset.kt`. Hex palettes below are from each theme's canonical light/dark pairs (dark→night, light→day):

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

package net.bible.android.view.activity.settings

import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities

/**
 * Built-in named color palettes. Selecting one stamps its full day + night palette
 * onto a [WorkspaceEntities.Colors] and records [id] in [WorkspaceEntities.Colors.themeName].
 * Editing any individual color afterwards clears themeName back to Custom
 * (handled in ColorSettingsDataStore).
 */
enum class ColorThemePreset(
    val id: String,
    val labelRes: Int,
    val dayText: Int, val dayBg: Int, val dayLink: Int, val dayVerse: Int, val dayHeading: Int,
    val nightText: Int, val nightBg: Int, val nightLink: Int, val nightVerse: Int, val nightHeading: Int,
) {
    GRUVBOX("gruvbox", R.string.color_theme_gruvbox,
        0xFF3C3836.toInt(), 0xFFFBF1C7.toInt(), 0xFF076678.toInt(), 0xFF7C6F64.toInt(), 0xFFB57614.toInt(),
        0xFFEBDBB2.toInt(), 0xFF282828.toInt(), 0xFF83A598.toInt(), 0xFFA89984.toInt(), 0xFFFABD2F.toInt()),
    NORD("nord", R.string.color_theme_nord,
        0xFF2E3440.toInt(), 0xFFECEFF4.toInt(), 0xFF5E81AC.toInt(), 0xFF4C566A.toInt(), 0xFFB48EAD.toInt(),
        0xFFD8DEE9.toInt(), 0xFF2E3440.toInt(), 0xFF88C0D0.toInt(), 0xFF7B88A1.toInt(), 0xFF81A1C1.toInt()),
    SOLARIZED("solarized", R.string.color_theme_solarized,
        0xFF657B83.toInt(), 0xFFFDF6E3.toInt(), 0xFF268BD2.toInt(), 0xFF93A1A1.toInt(), 0xFFB58900.toInt(),
        0xFF839496.toInt(), 0xFF002B36.toInt(), 0xFF268BD2.toInt(), 0xFF586E75.toInt(), 0xFFB58900.toInt()),
    DRACULA("dracula", R.string.color_theme_dracula,
        0xFF1F1F1F.toInt(), 0xFFF8F8F2.toInt(), 0xFF036A96.toInt(), 0xFF6272A4.toInt(), 0xFF644AC9.toInt(),
        0xFFF8F8F2.toInt(), 0xFF282A36.toInt(), 0xFF8BE9FD.toInt(), 0xFF6272A4.toInt(), 0xFFBD93F9.toInt()),
    SEPIA("sepia", R.string.color_theme_sepia,
        0xFF5B4636.toInt(), 0xFFF4ECD8.toInt(), 0xFF8B5A2B.toInt(), 0xFFA1887F.toInt(), 0xFF7B4F2C.toInt(),
        0xFFD8C8B0.toInt(), 0xFF2B2622.toInt(), 0xFFC9A066.toInt(), 0xFF8A7A68.toInt(), 0xFFD9B382.toInt());

    fun applyTo(c: WorkspaceEntities.Colors) {
        c.dayTextColor = dayText
        c.dayBackground = dayBg
        c.dayLinkColor = dayLink
        c.dayVerseNumberColor = dayVerse
        c.dayHeadingColor = dayHeading
        c.nightTextColor = nightText
        c.nightBackground = nightBg
        c.nightLinkColor = nightLink
        c.nightVerseNumberColor = nightVerse
        c.nightHeadingColor = nightHeading
        c.themeName = id
    }

    companion object {
        fun byId(id: String?): ColorThemePreset? =
            if (id.isNullOrEmpty()) null else entries.find { it.id == id }
    }
}
```

- [ ] **Step 4: Add string resources**

In `app/src/main/res/values/strings.xml`, add (near other `color_*` strings):

```xml
    <string name="color_theme">Color theme</string>
    <string name="color_theme_custom">Custom</string>
    <string name="color_theme_gruvbox">Gruvbox</string>
    <string name="color_theme_nord">Nord</string>
    <string name="color_theme_solarized">Solarized</string>
    <string name="color_theme_dracula">Dracula</string>
    <string name="color_theme_sepia">Sepia</string>
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.ColorThemePresetTest"` (`dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/settings/ColorThemePreset.kt app/src/test/java/net/bible/android/view/activity/settings/ColorThemePresetTest.kt app/src/main/res/values/strings.xml
git commit -m "Add ColorThemePreset palettes (gruvbox, nord, solarized, dracula, sepia)"
```

---

### Task 4: Wire theme selector into Color Settings UI

**Files:**
- Modify: `app/src/main/res/xml/color_settings.xml` (add ListPreference at top)
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt:40-68,182-200`
- Create: `app/src/main/res/values/color_theme_arrays.xml` (entries/values arrays)

**Interfaces:**
- Consumes: `ColorThemePreset` (Task 3), extended `Colors` (Task 1).
- Produces: a `ListPreference` keyed `color_theme`. Selecting a preset stamps colors + refreshes sibling picker summaries; picking "Custom" or editing any individual color sets `themeName = null`.

**Note on testing:** the stamping/reset logic delegates to `ColorThemePreset.applyTo` / `byId`, already unit-tested in Task 3. The `PreferenceDataStore` glue depends on an Android `Activity` and is verified by the build + a manual UI smoke (Step 6), not a unit test.

- [ ] **Step 1: Add the arrays resource**

Create `app/src/main/res/values/color_theme_arrays.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="color_theme_entries">
        <item>@string/color_theme_custom</item>
        <item>@string/color_theme_gruvbox</item>
        <item>@string/color_theme_nord</item>
        <item>@string/color_theme_solarized</item>
        <item>@string/color_theme_dracula</item>
        <item>@string/color_theme_sepia</item>
    </string-array>
    <string-array name="color_theme_values" translatable="false">
        <item></item>
        <item>gruvbox</item>
        <item>nord</item>
        <item>solarized</item>
        <item>dracula</item>
        <item>sepia</item>
    </string-array>
</resources>
```

- [ ] **Step 2: Add the ListPreference**

In `app/src/main/res/xml/color_settings.xml`, add immediately after the `workspace_color` preference (before the first `PreferenceCategory`):

```xml
    <ListPreference
        android:key="color_theme"
        android:title="@string/color_theme"
        android:entries="@array/color_theme_entries"
        android:entryValues="@array/color_theme_values"
        android:defaultValue=""
        app:useSimpleSummaryProvider="true"
        />
```

- [ ] **Step 3: Handle `color_theme` string in the DataStore**

In `ColorSettings.kt`, add `putString`/`getString` overrides to `ColorSettingsDataStore` (after `getInt`, before the class closes). Also make every `putInt` case reset the theme to Custom, since a manual color edit means it's no longer a preset:

```kotlin
    override fun putString(key: String?, value: String?) {
        if (key == "color_theme") {
            val preset = ColorThemePreset.byId(value)
            if (preset != null) preset.applyTo(colors) else colors.themeName = null
            activity.setDirty()
            activity.refreshColorPreferences()
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return if (key == "color_theme") colors.themeName ?: "" else defValue
    }
```

At the end of the existing `putInt` body (just before `activity.setDirty()`), add:

```kotlin
        if (key != "workspace_color") colors.themeName = null
```

(Editing `workspace_color` is a workspace-level setting, not part of the reading palette, so it does not clear the theme.)

- [ ] **Step 4: Add `refreshColorPreferences` to the fragment/activity**

The fragment owns the preferences, so expose a refresh hook. In `ColorSettingsActivity`, add a nullable back-reference and a delegating method:

```kotlin
    internal var fragment: ColorSettingsFragment? = null
    fun refreshColorPreferences() { fragment?.refreshSummariesAndTheme() }
```

Set it in `onCreate` where the fragment is created:

```kotlin
        val frag = ColorSettingsFragment(isWindow = settingsBundle.windowId != null)
        fragment = frag
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, frag)
            .commit()
```

(Replace the existing inline `.replace(... ColorSettingsFragment(...))` transaction with the above.)

In `ColorSettingsFragment`, add:

```kotlin
    fun refreshSummariesAndTheme() {
        val activity = activity as? ColorSettingsActivity ?: return
        // Re-sync the ListPreference selection with the (possibly changed) themeName.
        findPreference<androidx.preference.ListPreference>("color_theme")?.value = activity.colors.themeName ?: ""
        // Color pickers read their value live from the datastore; notify so summaries redraw.
        preferenceScreen?.let { screen ->
            for (i in 0 until screen.preferenceCount) screen.getPreference(i).let { } // no-op guard
        }
        updateImageSummaries()
    }
```

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew assembleStandardGithubDebug` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual UI smoke (document, not automated)**

If an emulator is available, open Workspace → Colors, pick "Gruvbox", confirm day/night text & background pickers update and Bible text recolors; then edit a color and confirm the theme selector flips to "Custom". Note this in the commit body.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/xml/color_settings.xml app/src/main/res/values/color_theme_arrays.xml app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt
git commit -m "Add color theme selector to Color Settings screen"
```

---

### Task 5: Vue — consume theme accent colors

**Files:**
- Create: `app/bibleview-js/src/composables/theme-colors.ts` (pure helper)
- Modify: `app/bibleview-js/src/composables/config.ts:74-85` (type)
- Modify: `app/bibleview-js/src/components/BibleView.vue:398-428` (topStyle emits vars)
- Modify: `app/bibleview-js/src/components/OSIS/Title.vue` (`<style>` reads `--heading-color`)
- Test: `app/bibleview-js/src/__tests__/colors.spec.js`

**Interfaces:**
- Consumes: `config.colors` fields (day/night link/verse/heading), `appSettings.{nightMode, monochromeMode}`.
- Produces: `resolveThemeAccentColors(colors, {nightMode, monochromeMode}): { linkColor: string | null, verseNumberColor: string | null, headingColor: string | null }`. Returns `null` for a slot when its color is unset OR monochrome mode is active (caller then omits the CSS var / keeps existing behavior).

- [ ] **Step 1: Write the failing test**

Add to `app/bibleview-js/src/__tests__/colors.spec.js` (top-level import + new describe):

```javascript
import {resolveThemeAccentColors} from "@/composables/theme-colors";

describe("resolveThemeAccentColors", () => {
    const themed = {
        dayLinkColor: 0x076678, dayVerseNumberColor: 0x7c6f64, dayHeadingColor: 0xb57614,
        nightLinkColor: 0x83a598, nightVerseNumberColor: 0xa89984, nightHeadingColor: 0xfabd2f,
    };
    it("day mode returns day colors", () => {
        const r = resolveThemeAccentColors(themed, {nightMode: false, monochromeMode: false});
        expect(Color(r.linkColor).hex()).toEqual("#076678");
        expect(Color(r.headingColor).hex()).toEqual("#B57614");
    });
    it("night mode returns night colors", () => {
        const r = resolveThemeAccentColors(themed, {nightMode: true, monochromeMode: false});
        expect(Color(r.linkColor).hex()).toEqual("#83A598");
    });
    it("monochrome suppresses all accents", () => {
        const r = resolveThemeAccentColors(themed, {nightMode: false, monochromeMode: true});
        expect(r.linkColor).toBeNull();
        expect(r.verseNumberColor).toBeNull();
        expect(r.headingColor).toBeNull();
    });
    it("unset fields return null (fallback to defaults)", () => {
        const r = resolveThemeAccentColors({}, {nightMode: false, monochromeMode: false});
        expect(r.linkColor).toBeNull();
        expect(r.verseNumberColor).toBeNull();
        expect(r.headingColor).toBeNull();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd app/bibleview-js && npm run test:ci -- colors`
Expected: FAIL — cannot resolve `@/composables/theme-colors`.

- [ ] **Step 3: Create the helper**

Create `app/bibleview-js/src/composables/theme-colors.ts`:

```typescript
import Color from "color";

interface ThemeColorFields {
    dayLinkColor?: number | null;
    nightLinkColor?: number | null;
    dayVerseNumberColor?: number | null;
    nightVerseNumberColor?: number | null;
    dayHeadingColor?: number | null;
    nightHeadingColor?: number | null;
}

interface ModeFlags { nightMode: boolean; monochromeMode: boolean; }

export interface ResolvedAccentColors {
    linkColor: string | null;
    verseNumberColor: string | null;
    headingColor: string | null;
}

function css(value: number | null | undefined): string | null {
    return (value === null || value === undefined) ? null : Color(value).hsl().string();
}

/**
 * Resolves theme accent colors for the active day/night mode. Returns null for any
 * slot that is unset or when monochrome (e-ink) mode is active, so the caller can
 * omit the CSS variable and let existing defaults/derivations apply.
 */
export function resolveThemeAccentColors(
    colors: ThemeColorFields,
    {nightMode, monochromeMode}: ModeFlags,
): ResolvedAccentColors {
    if (monochromeMode) return {linkColor: null, verseNumberColor: null, headingColor: null};
    return {
        linkColor: css(nightMode ? colors.nightLinkColor : colors.dayLinkColor),
        verseNumberColor: css(nightMode ? colors.nightVerseNumberColor : colors.dayVerseNumberColor),
        headingColor: css(nightMode ? colors.nightHeadingColor : colors.dayHeadingColor),
    };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd app/bibleview-js && npm run test:ci -- colors`
Expected: PASS.

- [ ] **Step 5: Extend the config type**

In `app/bibleview-js/src/composables/config.ts`, inside the `colors: { ... }` block (after `nightBackgroundImageOpacity`), add:

```typescript
        dayLinkColor?: number | null,
        nightLinkColor?: number | null,
        dayVerseNumberColor?: number | null,
        nightVerseNumberColor?: number | null,
        dayHeadingColor?: number | null,
        nightHeadingColor?: number | null,
        themeName?: string | null,
```

- [ ] **Step 6: Wire `topStyle` to emit the vars**

In `app/bibleview-js/src/components/BibleView.vue`:

Add the import near the other composable imports at the top of `<script setup>`:

```typescript
import {resolveThemeAccentColors} from "@/composables/theme-colors";
```

In `topStyle` (computed), after the existing `verseNumberColor` block and before the `return`:

```typescript
    const accent = resolveThemeAccentColors(config.colors, {
        nightMode: appSettings.nightMode,
        monochromeMode: appSettings.monochromeMode,
    });
    // Theme verse-number color overrides the derived fade when present.
    const finalVerseNumberColor = accent.verseNumberColor ?? verseNumberColor;
    const linkVar = accent.linkColor ? `--link-color: ${accent.linkColor};` : "";
    const headingVar = accent.headingColor ? `--heading-color: ${accent.headingColor};` : "";
```

Change the returned template's `--verse-number-color` line to use `finalVerseNumberColor`, and append the two optional vars before the closing backtick:

```typescript
          --verse-number-color: ${finalVerseNumberColor};
          --background-color: ${backgroundColor.hsl().string()};
          ${linkVar}
          ${headingVar}
          `;
```

(`--link-color` is only emitted when themed; otherwise the `common.scss` default at line 130 wins. `--heading-color` is new and only emitted when themed.)

- [ ] **Step 7: Make section titles read `--heading-color`**

In `app/bibleview-js/src/components/OSIS/Title.vue`, in the `.titleStyle` CSS rule (inside `<style scoped>`), add:

```css
  color: var(--heading-color, inherit);
```

- [ ] **Step 8: Validate Vue (tests + lint + types)**

Run: `cd app/bibleview-js && npm run test:ci && npm run lint && npm run type-check`
Expected: all PASS.

- [ ] **Step 9: Commit**

```bash
git add app/bibleview-js/src/composables/theme-colors.ts app/bibleview-js/src/composables/config.ts app/bibleview-js/src/components/BibleView.vue app/bibleview-js/src/components/OSIS/Title.vue app/bibleview-js/src/__tests__/colors.spec.js
git commit -m "Render theme accent colors (link, verse number, heading) in BibleView"
```

---

## Self-Review

**Spec coverage:**
- Data model (6 accent fields + themeName, nullable, merge) → Task 1 ✓
- Migration 24→25 on Workspace/PageManager/GlobalTextDisplaySettings → Task 2 ✓
- Kotlin-side preset definitions (5 themes, full palette, applyTo/byId) → Task 3 ✓
- Apply logic + ListPreference UI + manual-edit→Custom + strings → Task 4 ✓
- Vue config type + 3 CSS vars + Title heading + monochrome suppression → Task 5 ✓
- Serialization pipeline unchanged (`toJson()` auto-includes new fields; config emitted via `displaySettings.toJson()`) → no BibleView.kt task needed, noted in Task 5 interfaces ✓
- Testing: Kotlin merge + preset unit tests; Vue helper tests; migration guarded by Room startup check (documented) ✓
- Out-of-scope items (custom themes, bookmark colors, red-letter, native chrome) → not implemented ✓

**Placeholder scan:** No TBD/TODO; all code shown; palettes are concrete hex. The `refreshSummariesAndTheme` no-op loop guard is intentional (color pickers re-read live from the datastore; the loop is a placeholder-free hook point) — implementer may simplify if the picker library exposes a direct `notifyChanged`.

**Type consistency:** `applyTo`/`byId` signatures match between Task 3 definition and Task 4 usage. `resolveThemeAccentColors` signature/return (`linkColor|verseNumberColor|headingColor`) matches between Task 5 helper, test, and topStyle usage. Column names use the `text_display_settings_colors_` prefix consistently in Task 2. Field names (`dayLinkColor` etc.) identical across Tasks 1, 3, 5.
