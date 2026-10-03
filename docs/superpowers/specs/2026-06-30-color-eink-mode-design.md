# Color e-ink mode — design

Date: 2026-06-30

## Background

AndBible already has two settings aimed at e-ink devices:

- **Black & white mode** (`monochrome_mode`): forces the whole UI to grayscale — backgrounds,
  text, toolbars, window bars, bookmark highlights, the active-window indicator, etc.
- **E-ink display mode** (`eink_mode`): shows e-ink-specific UI elements (scroll helper lines,
  page buttons, scroll-helper-line style). Despite the name it is *not* about animations.
- **Disable animations** (`disable_animations`): a separate, orthogonal toggle.

Color e-ink devices exist now and can show color, but they still need high contrast. In practice
black & white mode currently works best even on color e-ink devices. The goal of this work is a
third **"Color e-ink"** color treatment that is *almost identical to black & white mode* but
selectively shows color where it improves clarity / contrast on a color e-ink screen:

- bookmark highlight/underline colors and bookmark marker icons rendered in the label's real color
- the active-window indicator shown in a clear color (the existing blue) instead of black/white
- normally colorless helper elements — scroll helper lines and the top-margin line — drawn in a
  unified accent **blue** instead of gray, for contrast.

On normal (non-e-ink) devices and in plain black & white mode, none of these elements change —
they keep rendering exactly as they do today.

## Approach (chosen)

Replace the boolean `monochrome_mode` switch with a **three-way choice** (a `ListPreference`):
`normal` / `bw` / `color_eink`.

Internally, color e-ink **inherits the monochrome base**:

```
displayColorMode ∈ { NORMAL, BW, COLOR_EINK }
monochromeMode   = displayColorMode in [BW, COLOR_EINK]   // existing grayscale code unchanged
colorEinkMode    = displayColorMode == COLOR_EINK         // only the accent points check this
```

This is deliberately the lowest-risk model: all ~20 existing `monochromeMode` branch points keep
working unchanged (color e-ink gets the full grayscale base for free). Only the four accent
categories gain a `colorEinkMode` override that re-introduces color. The accent transformation at
each grayscale branch is uniformly "monochrome **AND NOT** colorEink".

Color e-ink is always an explicit user choice — there is no auto-detection. The default stays
`bw` on Onyx devices and `normal` elsewhere (preserving today's behavior).

## Data model & settings (Android)

### `CommonUtils.kt`

Current (`CommonUtils.kt:453`):

```kotlin
val monochromeMode: Boolean get() = getBoolean("monochrome_mode", isOnyxDevice)
```

New:

```kotlin
enum class DisplayColorMode(val value: String) {
    NORMAL("normal"), BW("bw"), COLOR_EINK("color_eink");
    companion object {
        fun fromValue(v: String?): DisplayColorMode =
            entries.firstOrNull { it.value == v } ?: NORMAL
    }
}

val displayColorMode: DisplayColorMode get() =
    DisplayColorMode.fromValue(getString("display_color_mode", defaultDisplayColorMode))

val monochromeMode: Boolean get() = displayColorMode != DisplayColorMode.NORMAL
val colorEinkMode:  Boolean get() = displayColorMode == DisplayColorMode.COLOR_EINK
```

`defaultDisplayColorMode` = `if (isOnyxDevice) "bw" else "normal"`.

### Migration

`monochrome_mode` is a stored boolean. On first run after upgrade, perform a **one-time migration**
that writes `display_color_mode` from the old value (and removes the old key), so the settings UI
reflects the user's previous choice:

- `monochrome_mode == true`  → `display_color_mode = "bw"`
- `monochrome_mode == false` → `display_color_mode = "normal"`
- old key absent → leave unset (getter falls back to device default)

Use the existing settings-migration hook if one exists; otherwise run it at app startup before the
settings screen can be opened. (Implementation plan to confirm the exact hook.)

### `settings.xml` (`settings.xml:195-200`)

Replace the `SwitchPreferenceCompat` for `monochrome_mode` with a `ListPreference`:

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

Entry values: `normal`, `bw`, `color_eink`. The `eink_mode` and `disable_animations` switches in the
same category are unchanged.

In `SettingsActivity.SettingsFragment.onCreatePreferences`, set the effective device default on the
`ListPreference` (mirroring how `night_mode_pref3` calls `setDefaultValue`) so the UI shows `bw` on
Onyx when nothing is persisted. The preference uses the existing `PreferenceStore` data store (same
as other `ListPreference`s), so string persistence and the return-to-MainBibleActivity refresh path
work unchanged.

### `SettingsActivity.kt` reset list (`SettingsActivity.kt:164`)

Replace `"monochrome_mode"` with `"display_color_mode"` in the reset-preferences key list.

### Strings (`strings.xml`)

- Reuse / repurpose: the current `prefs_e_ink_mode_title` ("Black & white mode") and
  `prefs_eink_mode_summary` are tied to the old switch. Add new strings:
  - `prefs_display_color_mode_title` — e.g. "Color mode"
  - `prefs_display_color_mode_summary` — explains the three options
  - string-array `prefs_display_color_mode_names`: `Normal`, `Black & white`, `Color e-ink`
  - string-array `prefs_display_color_mode_values`: `normal`, `bw`, `color_eink`
- Color e-ink option description should make clear: "Black & white base, but bookmark colors, the
  active-window indicator and helper lines stay/become colored — for color e-ink screens."
- English only (translations handled separately).

## Android UI (native chrome)

Native chrome (toolbar, window bars, restore buttons in `MainBibleActivity` / `SplitBibleArea`)
**stays monochrome** in color e-ink mode, exactly like black & white mode. All those branch points
read `monochromeMode`, which is `true` for color e-ink — no change required. The accent elements the
user asked for all live in the WebView content (Vue.js), including the active-window indicator.

## Kotlin → Vue.js config flow

### `BibleView.kt` (`getUpdateConfigCommand`, ~1507-1532)

`monochromeMode` continues to be passed (stays `true` for color e-ink, so JS-computed
text/background colors go black/white). Add the new flag alongside it:

```kotlin
val colorEinkMode = CommonUtils.settings.colorEinkMode
// ... in the emitted appSettings object:
monochromeMode: $monochromeMode,
colorEinkMode: $colorEinkMode,
```

(The black/white background computation at `BibleView.kt:1371-1373` is unchanged — color e-ink keeps
a true black/white background for reading contrast.)

### `config.ts`

- Add `colorEinkMode: boolean` to the `AppSettings` type (after `monochromeMode`, line ~134).
- Initialize it in the reactive `appSettings` object and handle it in the `set_config` listener
  (same as `monochromeMode`).

## Vue.js accent overrides

### Body class binding (`BibleView.vue:21`)

Add a `colorEink` class (keep `monochrome`, which stays present for color e-ink so the grayscale base
CSS still applies):

```html
:class="{night: appSettings.nightMode, noAnimation: appSettings.disableAnimations,
         monochrome: appSettings.monochromeMode, colorEink: appSettings.colorEinkMode}"
```

CSS override rules use `.colorEink &` (or `.monochrome.colorEink &` for specificity over the existing
`.monochrome &` rules — to be finalized during implementation; rules placed after the monochrome
rules with equal-or-higher specificity).

### 1. Bookmark colors (`bookmarks.ts`) — JS-driven

Each grayscale branch becomes "monochrome AND NOT colorEink":

- **Underline color** (`bookmarks.ts:164-166`):
  ```ts
  const color = (appSettings.monochromeMode && !appSettings.colorEinkMode)
      ? monochromeUnderlineColor
      : new Color(s.color).hsl();
  ```
- **Highlight color** (`bookmarks.ts:637-640`):
  ```ts
  if (appSettings.monochromeMode && !appSettings.colorEinkMode) {
      return monochromeHighlightColor;
  }
  // fall through to the real colored highlight
  ```
- **Marker icons** (`bookmarks.ts:799` speak icon, `:830` bookmark/note marker):
  ```ts
  const color = adjustedColor(
      (appSettings.monochromeMode && !appSettings.colorEinkMode) ? "black" : <realColor>
  ).string();
  ```

### 2. Active-window indicator (CSS, `BibleView.vue:510-585`)

Corners (`.active-window-corner`, :523-528) and full border (`.active-window-indicator`, :579-584)
currently force black/white under `.monochrome`. Add color-eink overrides restoring the existing
blue (day) / light blue (night):

```scss
.colorEink & {            // overrides the .monochrome black/white
    border-color: rgba(0, 0, 255, 0.6);
}
.colorEink.night & {
    border-color: rgba(196, 196, 255, 0.8);
}
```

(Same blue/light-blue values already used for the non-monochrome case at `:521` and `:518`/`:574`.)

### 3. Scroll helper lines (CSS, `BibleView.vue:749-771`)

Currently `border-top` uses `var(--text-color)` (gray-ish via opacity 0.3). Add a colorEink override
that uses the unified accent blue at full strength for contrast:

```scss
.colorEink & {
    border-color: rgba(0, 0, 255, 0.6);   // day
    opacity: 1;
}
.colorEink.night & {
    border-color: rgba(196, 196, 255, 0.8); // night
}
```

Applied to all three style variants (thin-dotted / thin-solid / thick-solid). Normal and plain b&w
modes keep `var(--text-color)` + opacity 0.3.

### 4. Top-margin line (CSS, `BibleView.vue:587-608`)

Currently a translucent gray bar, becoming a dashed gray border under `.noAnimation`. Add colorEink
overrides drawing it in the accent blue. Because color e-ink users typically also enable
`disable_animations`, the colorEink rule must cover both the bar and the `.noAnimation` dashed-border
case:

```scss
.colorEink & {
    background-color: rgba(0, 0, 255, 0.25);
}
.colorEink.noAnimation & {
    background-color: unset;
    border-bottom: 1px dashed rgba(0, 0, 255, 0.7);
}
.colorEink.night & { ... lighter blue ... }
```

Exact alphas to be tuned during implementation/visual check.

### Shared accent color

Define the day/night accent blue once (SCSS variables) and reuse across the active-window indicator,
scroll helper lines, and top-margin line, to keep a single unified accent color.

## Testing

- **Vue.js** (`bookmarks.spec.js` and a `BibleView`-level spec where feasible): verify
  highlight/underline/marker colors across the three modes — `normal` (real color), `bw` (gray/black),
  `color_eink` (real color). The key regression target is that `bw` is unchanged and `color_eink`
  returns real colors while `monochromeMode` is still true.
- **Android**: unit test for the derived `monochromeMode` / `colorEinkMode` from `displayColorMode`,
  and for the `monochrome_mode → display_color_mode` migration mapping, if testable without a full
  Android context.

## Theme matrix verification

Verify all combinations render correctly and that `normal` and `bw` are visually unchanged from
today:

- day / night × animations on / off
- each of the three color modes
- focus on the four accent categories in `color_eink` (bookmarks, active-window indicator, scroll
  helper lines, top-margin line).

## Out of scope (YAGNI)

- Auto-detecting color e-ink hardware (no reliable signal; user opts in).
- Colorizing other normally-grayscale elements (links, verse selection highlight, study-pad/notes
  colors, native chrome). Only the four agreed accent categories are in scope.
