# Adding a New TextDisplaySetting

This guide covers the full checklist for adding a new boolean TextDisplaySetting (TDS) to AndBible. TDS settings control per-window/workspace/global text display options (show bookmarks, show verse numbers, etc.).

## Checklist

### 1. Database Entity (`WorkspaceEntities.kt`)

In `TextDisplaySettings` data class:

- Add the field: `@ColumnInfo(defaultValue = "NULL") var showMyNewSetting: Boolean? = null`
- Add enum value to `Types`: `MY_NEW_SETTING`
- Add to `getValue()`: `Types.MY_NEW_SETTING -> showMyNewSetting`
- Add to `setValue()`: `Types.MY_NEW_SETTING -> showMyNewSetting = value as Boolean?`
- Add to `default` companion val with default value: `showMyNewSetting = true`

### 2. Database Migration (`WorkspacesMigrations.kt`)

- Add migration (N..N+1) with ALTER TABLE for all three tables:
  ```kotlin
  private val addMyNewSetting = makeMigration(N..N+1) { _db ->
      _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showMyNewSetting` INTEGER DEFAULT NULL")
      _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showMyNewSetting` INTEGER DEFAULT NULL")
      _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showMyNewSetting` INTEGER DEFAULT NULL")
  }
  ```
- Add to `workspacesMigrations` array
- Increment `WORKSPACE_DATABASE_VERSION`

### 3. Settings UI XML (`app/src/main/res/xml/text_display_settings.xml`)

Add a `SwitchPreferenceCompat` in the appropriate category:
```xml
<SwitchPreferenceCompat
    android:key="MY_NEW_SETTING"
    android:title="@string/prefs_my_new_setting_title"
    android:summary="@string/prefs_my_new_setting_summary"
    />
```

### 4. String Resources (`app/src/main/res/values/strings.xml`)

Add title and summary strings (English only — translations handled by Transifex):
```xml
<string name="prefs_my_new_setting_title">Show my new setting</string>
<string name="prefs_my_new_setting_summary">Description of what this setting does</string>
```

### 5. Preference Class (`OptionsMenuItems.kt`)

- Add title mapping in `Preference.title` `when` block: `Types.MY_NEW_SETTING -> R.string.prefs_my_new_setting_title`
- Optionally add icon mapping in `Preference.icon` `when` block (else falls back to default star icon)
- If custom visibility/enable logic needed, create a subclass:
  ```kotlin
  class MyNewSettingPreference(settings: SettingsBundle) : Preference(settings, Types.MY_NEW_SETTING) {
      override val visible: Boolean get() = /* custom logic */
  }
  ```
  Otherwise use `ItemPreference(settings, Types.MY_NEW_SETTING)` directly.

### 6. Settings Fragment (`TextDisplaySettings.kt`)

Add to `getPrefItem()` function:
```kotlin
Types.MY_NEW_SETTING -> MyNewSettingPreference(settings)
// or: Types.MY_NEW_SETTING -> ItemPreference(settings, Types.MY_NEW_SETTING)
```

### 7. Vue.js Config Type (`app/bibleview-js/src/composables/config.ts`)

- Add to `Config` type: `showMyNewSetting: boolean`
- Add default in `useConfig()`: `showMyNewSetting: true`
- Add to `getNeedBookmarkRefresh()` keys array if the setting affects bookmark/marker rendering
- Add to `getNeedRefreshLocation()` keys array if the setting affects text layout

### 8. Vue.js Usage

Use `config.showMyNewSetting` in the relevant composable or component to conditionally render content.

## How TDS Inheritance Works

Settings cascade: **Window** → **Workspace** → **Global** → **Default**. Each level can be `null` (inherit from parent) or have an explicit value. `TextDisplaySettings.actual()` resolves the effective value by walking the chain.

## Notes

- The `title` `when` in `OptionsMenuItems.kt` is **exhaustive** (no `else`) — the Kotlin compiler will error if a Types entry is missing. The `icon` `when` has an `else` branch so new entries get a default icon.
- `Types.values()` iteration (used in copy-settings dialogs) works automatically — no changes needed there.
- The column name pattern is `text_display_settings_<fieldName>` due to Room's `@Embedded` prefix.
