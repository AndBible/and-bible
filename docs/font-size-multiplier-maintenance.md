# Font Size Multiplier Setting - Maintenance Notes

## Why this was changed

The `Fontin koon kerroin` setting had multiple UX and stability issues:

- Value was shown twice (`title row` right side + `Nykyinen arvo` in summary).
- Value did not update early enough while dragging the slider.
- Earlier quick-polish used a theme attribute not available in this preference context, which caused an inflate crash in settings.

## What was changed

### 1) Custom stepper preference for this setting

- `settings.xml` now uses a custom preference class for `font_size_multiplier`:
  - `net.bible.android.view.widget.StepSeekBarPreference`
- New custom layout adds left/right step buttons around the slider.
- Buttons use `seekBarIncrement` so one tap = one configured step.

Files:

- `app/src/main/res/xml/settings.xml`
- `app/src/main/java/net/bible/android/view/widget/StepSeekBarPreference.kt`
- `app/src/main/res/layout/preference_seekbar_stepper.xml`
- `app/src/main/res/drawable/pref_stepper_button_background.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fi/strings.xml`

### 2) Single-source value display

- Removed the extra value badge on the title row.
- Kept one value display in summary:
  - `Nykyinen arvo: <n.n>x`

This avoids duplicate/conflicting value indicators.

### 3) Live update while sliding

- Enabled continuous updates:
  - `fontSizeMultiplier.setUpdatesContinuously(true)`
- Summary text is updated in `setOnPreferenceChangeListener`, so the visible value refreshes during drag, not only after release.

File:

- `app/src/main/java/net/bible/android/view/activity/settings/SettingsActivity.kt`

### 4) Crash fix (preference row inflation)

- A previous quick-polish version used a badge view with a Material color attribute not available in this preference theme context.
- The duplicated badge/value UI was removed from the title row, and the setting now keeps a single summary-based value display.
- This removed the `InflateException` seen when opening/scanning settings.

File:

- `app/src/main/res/layout/preference_seekbar_stepper.xml`

## Current behavior

- Step size: `10` units = `0.1x` multiplier step.
- Range from XML: `10..500` (`0.1x..5.0x`).
- Display format: one decimal (`%1.1fx`).

## Validation summary

- Build check:
  - `./gradlew :app:assembleStandardGithubDebug -x jsBuild --no-daemon` (PASS)
- Device check:
  - Samsung A22 (`R9WT702055P`)
  - Open `Sovelluksen asetukset` and verify:
    - no crash in settings list
    - no duplicate value display
    - summary value changes while slider is being moved
    - step buttons adjust value one increment per tap

## Notes for future maintainers

- If the setting row is redesigned again, keep exactly one visible value source to avoid confusion.
- Avoid Material-only color attrs in this preference overlay unless confirmed available in the active theme.
- If changing step size, update both UX text expectations and any related manual test notes.
