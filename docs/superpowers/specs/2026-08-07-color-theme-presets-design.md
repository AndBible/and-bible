# Named Color Theme Presets (Gruvbox, Nord, Solarized, Dracula, Sepia)

**Date:** 2026-08-07
**Status:** Design — pending user review

## Problem

AndBible has no concept of named color palettes. Theming today is three orthogonal
axes:

- **Day/Night binary** (`nightMode`) — [ScreenSettings.kt:84](../../../app/src/main/java/net/bible/service/device/ScreenSettings.kt#L84)
- **DisplayColorMode** tri-state — `NORMAL` / `BW` (e-ink mono) / `COLOR_EINK` — [DisplayColorMode.kt:29](../../../app/src/main/java/net/bible/service/common/DisplayColorMode.kt#L29)
- **Per-workspace/window custom colors** — user picks day/night text + background + noise
  individually via color picker.

Users must hand-pick every color. There are no curated palettes. Goal: ship named
presets (Gruvbox, Nord, Solarized, Dracula, Sepia) covering a **full palette** (text,
background, links/xref, verse numbers, section headings) for both day and night, with
the selected theme remembered.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Preset model | Presets fill the existing color fields (full palette, incl. accents) |
| Palette slots (new) | link/xref color, verse-number color, section-heading color — per day/night |
| Themes shipped | Gruvbox, Nord, Solarized, Dracula, Sepia (each day+night variant) |
| Apply model | **Remember** selected theme; manual color edit → "Custom" |
| Where palettes are defined | **Kotlin-side** — presets populate `WorkspaceEntities.Colors`; flows through existing serialization + Vue pipeline. Single source of truth. |

## Architecture

Presets are pure color data on the Kotlin side. Selecting a preset stamps all `Colors`
fields (day + night, text/background/link/verseNumber/heading) and records the preset id
in a new `themeName` field. The existing `Colors` → `TextDisplaySettings.toJson()` →
WebView config → Vue CSS-var pipeline carries everything unchanged; the Vue side only
needs to consume three new optional CSS vars with fallbacks.

Because monochrome (`BW`) mode already forces black/white in the Vue layer
([BibleView.vue:399-403](../../../app/bibleview-js/src/components/BibleView.vue#L399)),
themes are auto-suppressed on e-ink with no extra work.

### 1. Data model — [WorkspaceEntities.kt:140](../../../app/src/main/java/net/bible/android/database/WorkspaceEntities.kt#L140)

Extend `Colors` with 6 nullable accent fields + `themeName`. `null` on any color field =
current derived/default behavior (no regression for existing users).

```kotlin
@Serializable
data class Colors(
    @ColumnInfo(defaultValue = "NULL") var dayTextColor: Int?,
    @ColumnInfo(defaultValue = "NULL") var dayBackground: Int?,
    @ColumnInfo(defaultValue = "NULL") var dayNoise: Int?,
    @ColumnInfo(defaultValue = "NULL") var nightTextColor: Int?,
    @ColumnInfo(defaultValue = "NULL") var nightBackground: Int?,
    @ColumnInfo(defaultValue = "NULL") var nightNoise: Int?,
    @ColumnInfo(defaultValue = "NULL") var dayBackgroundImage: String?,
    @ColumnInfo(defaultValue = "NULL") var nightBackgroundImage: String?,
    @ColumnInfo(defaultValue = "NULL") var dayBackgroundImageOpacity: Int?,
    @ColumnInfo(defaultValue = "NULL") var nightBackgroundImageOpacity: Int?,
    // NEW:
    @ColumnInfo(defaultValue = "NULL") var dayLinkColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var nightLinkColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var dayVerseNumberColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var nightVerseNumberColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var dayHeadingColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var nightHeadingColor: Int? = null,
    @ColumnInfo(defaultValue = "NULL") var themeName: String? = null,
)
```

`merge()` (WorkspaceEntities.kt:170) gains matching `override.x ?: x` lines for each new
field so per-field inheritance across the default→global→workspace→page hierarchy still
holds. `themeName` merges the same way.

### 2. Migration — [WorkspacesMigrations.kt:296](../../../app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt#L296)

Bump `WORKSPACE_DATABASE_VERSION` 24 → 25. Add migration `addColorThemePresets = makeMigration(24..25)`
mirroring `addBackgroundImage`. Embedded `Colors` uses prefix `colors_` inside
`TextDisplaySettings` (prefix `text_display_settings_`), so full column names are
`text_display_settings_colors_<field>`. Apply to the same three tables the existing
Colors migration touches: **`Workspace`, `PageManager`, `GlobalTextDisplaySettings`**.

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

Register in the `workspacesMigrations` array (WorkspacesMigrations.kt:305).

### 3. Preset definitions — new `ColorThemePreset.kt`

New file under `net.bible.service.common` (or `view.activity.settings`). Sealed
enum/data set. Each preset defines a full day + night palette and produces a partial
`Colors`. Store colors as `0xAARRGGBB` ints (matching existing color-int convention).

```kotlin
enum class ColorThemePreset(
    val id: String,                 // stored in Colors.themeName
    val labelRes: Int,              // strings.xml entry
    // day
    val dayText: Int, val dayBg: Int, val dayLink: Int, val dayVerse: Int, val dayHeading: Int,
    // night
    val nightText: Int, val nightBg: Int, val nightLink: Int, val nightVerse: Int, val nightHeading: Int,
) {
    GRUVBOX(...), NORD(...), SOLARIZED(...), DRACULA(...), SEPIA(...);

    /** Stamp this preset onto a Colors object (mutates day/night text/bg + accents + themeName). */
    fun applyTo(c: WorkspaceEntities.Colors) { ... c.themeName = id }

    companion object {
        fun byId(id: String?): ColorThemePreset? = entries.find { it.id == id }
    }
}
```

Palette sourcing: dark-first themes (Nord, Dracula) use their canonical palette for
**night** fields and a lighter theme-adjacent variant for **day**. Solarized/Gruvbox/Sepia
have natural light+dark pairs. Exact hex values chosen during implementation from each
theme's published palette.

### 4. Apply logic + UI — [ColorSettings.kt](../../../app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt), [color_settings.xml](../../../app/src/main/res/xml/color_settings.xml)

- Add a `ListPreference` `color_theme` at the **top** of `color_settings.xml`. Entries:
  `Custom` + one per preset. Values = preset ids (`""`/`custom` for Custom).
- `ColorSettingsDataStore` gains `putString`/`getString` for `color_theme`:
  - **get** → `colors.themeName ?: ""` (Custom).
  - **put** a preset id → `ColorThemePreset.byId(id)?.applyTo(colors)`, then `setDirty()`.
    The fragment refreshes the individual picker summaries to reflect stamped colors.
  - **put** `""`/Custom → no-op on colors (keeps current), `themeName = null`.
- Any existing per-color `putInt` (text/background/etc.) additionally sets
  `colors.themeName = null` so a manual tweak flips the selector to **Custom**. The
  fragment re-reads the `color_theme` preference value on change.

`ColorSettingsFragment.onCreatePreferences` wires an `OnPreferenceChangeListener` on
`color_theme` to stamp + refresh sibling preference summaries in place.

### 5. Vue render

**Config type** — [config.ts:74](../../../app/bibleview-js/src/composables/config.ts#L74):
add optional fields to `colors`:
```ts
dayLinkColor?: number | null,        nightLinkColor?: number | null,
dayVerseNumberColor?: number | null, nightVerseNumberColor?: number | null,
dayHeadingColor?: number | null,     nightHeadingColor?: number | null,
themeName?: string | null,
```

**topStyle** — [BibleView.vue:398](../../../app/bibleview-js/src/components/BibleView.vue#L398):
emit three vars, each falling back to today's behavior when the field is null and
suppressed in monochrome mode (consistent with existing text/bg handling):

- `--link-color`: overrides the `common.scss:130` default (`#1a73e8` day / `#8ab4f8`
  night). When theme field null → **omit** the var so the SCSS default wins.
- `--verse-number-color`: currently derived at BibleView.vue:408-426. When theme field
  set (and not monochrome) → use it; else keep existing derivation.
- `--heading-color`: **new** var. When null → omit (title inherits text color as today).

**Consumers:**
- Links/xref already read `var(--link-color)` — [Html.vue:40](../../../app/bibleview-js/src/components/OSIS/Html.vue#L40), markdown-render.scss. No change beyond topStyle emitting it.
- Verse numbers read `var(--verse-number-color)` — [VerseNumber.vue:42](../../../app/bibleview-js/src/components/OSIS/VerseNumber.vue#L42), [Chapter.vue:53](../../../app/bibleview-js/src/components/OSIS/Chapter.vue#L53). No change.
- Section titles — [Title.vue:20](../../../app/bibleview-js/src/components/OSIS/Title.vue#L20) (`.titleStyle`). Add `color: var(--heading-color, inherit);` in its `<style>`.

### 6. Strings

- **Android** [strings.xml](../../../app/src/main/res/values/strings.xml): `color_theme` (pref
  title), `color_theme_custom`, and one label per preset (`color_theme_gruvbox`, …).
  English only per project policy.
- No new Vue strings (theme is chosen on the Android settings screen).

## Data flow (unchanged pipeline)

```
ColorThemePreset.applyTo → WorkspaceEntities.Colors (Room, 3 tables)
  → TextDisplaySettings.merge/.actual → toJson()
  → BibleView.getUpdateConfigCommand (config: {...})
  → config.ts colors{}  → BibleView.vue topStyle CSS vars
  → VerseNumber/Reference/Title components
```

## Theme / display-mode interaction

- **Light/Dark**: preset stamps day fields and night fields; existing `nightMode` toggle
  switches variant automatically. Both must be validated.
- **Monochrome (BW / e-ink)**: Vue forces white/black; theme accents auto-suppressed. No
  colored artifacts. Must verify links/verse-numbers/headings go grayscale.
- **No-animations**: unaffected (no animation added).

## Testing

**Kotlin** (`app/src/test/java/...`):
- Each preset's `applyTo` stamps expected day+night text/bg/link/verse/heading and sets
  `themeName` to its id.
- `ColorThemePreset.byId` round-trips ids; unknown/null → null.
- Manual color edit path sets `themeName = null` (Custom).
- `Colors.merge` propagates each new field per-field.
- Migration 24→25 test: open v24 DB, migrate, assert new columns exist on all three
  tables and existing rows read back with null accents (no data loss).

**Vue** (`app/bibleview-js/src/__tests__/*.spec.js`):
- `topStyle`/color-computation emits `--link-color` / `--verse-number-color` /
  `--heading-color` when config fields set.
- Falls back (omits var / keeps derivation) when fields null.
- Monochrome mode suppresses accent colors to black/white.

## Out of scope (YAGNI)

- User-defined/custom saved themes (only built-in presets).
- Theming bookmark/highlight label colors (user-owned, separate system).
- Red-letter (words of Jesus) recoloring.
- Theming the native Android chrome (action bar, menus) — BibleView content only.
- Import/export or sharing of themes.

## Open items for implementation

- Final hex palettes per theme (day + night) — pick from canonical sources.
- Confirm `ListPreference` refresh of sibling picker summaries after stamping (may need
  `fragment.setPreferencesFromResource` re-read or manual summary updates).
