# AI Panel Auto-Hide on Task Completion — Design

**Date:** 2026-06-19

## Summary

Add a new AI setting that automatically hides the AI agent log panel
(`AgentLogWidget`) when an agent task finishes — **except when it finishes with
an error**, in which case the panel stays visible so the user can read the
error. The setting is **off by default**.

When the panel is auto-hidden because the task completed successfully, a toast
("AI task completed") is shown, because the panel disappears immediately and the
user would otherwise have no signal that the task finished.

## Motivation

Today the `AgentLogWidget` auto-shows when an agent starts running, but never
auto-hides. The user must dismiss it manually after every task. Users who run
the AI assistant frequently want the panel to get out of the way on success
while still surfacing failures.

## Behaviour

| Task outcome        | Setting OFF (default) | Setting ON                          |
|---------------------|-----------------------|-------------------------------------|
| Completed (success) | panel stays (current) | panel hides + toast "AI task completed" |
| Cancelled / cleared | panel stays (current) | panel hides, no toast               |
| Error               | panel stays (current) | panel **stays visible** (no hide)   |

Auto-hide happens immediately (no delay). If the panel is already hidden when
the task finishes, nothing happens.

## Architecture

### 1. Distinguishing the stop outcome (core change)

The problem: `AgentSession.stop()` currently posts
`AgentSessionStatusChangedEvent(workspaceId, isRunning = false)` for **all**
terminal states — success, error, cancellation. The widget cannot tell them
apart from the event alone.

Changes in `AgentSessionManager.kt`:

- New enum:
  ```kotlin
  enum class AgentStopReason { COMPLETED, ERROR, CANCELLED }
  ```
- `AgentSession.stop(message: String? = null, reason: AgentStopReason = AgentStopReason.CANCELLED)`
  — `CANCELLED` is the safe default for the cancellation paths that pass no
  explicit reason.
- `AgentSessionStatusChangedEvent` gains a nullable field:
  ```kotlin
  class AgentSessionStatusChangedEvent(
      val workspaceId: IdType,
      val isRunning: Boolean,
      val stopReason: AgentStopReason? = null  // null on start (isRunning=true)
  )
  ```
  `stop()` posts the event with the actual reason; `start()` posts with `null`.

Call-site mapping for `session.stop(...)`:

| Line (current) | Context                          | reason      |
|----------------|----------------------------------|-------------|
| 228            | `stopAgent` — cancelled by user  | `CANCELLED` |
| 236            | session cleared                  | `CANCELLED` |
| 313            | `CancellationException` fallback | `CANCELLED` |
| 659            | `AgentEvent.Error`               | `ERROR`     |
| 665            | `AgentEvent.Cancelled`           | `CANCELLED` |
| 673            | `completeSession` — success      | `COMPLETED` |

`AgentForegroundService.onEvent(AgentSessionStatusChangedEvent)` (line ~406)
needs no change — the added field has a default and that handler ignores it.

### 2. New setting (DB-backed, syncable)

Follows the exact pattern of the existing `askModelBeforeRun` setting.

- `GlobalAiSettings` entity (`AgentPromptEntities.kt`): add
  ```kotlin
  @ColumnInfo(defaultValue = "0") val autoHideAgentLogOnCompletion: Boolean = false
  ```
- Migration in `AiSettingsMigrations.kt`:
  ```kotlin
  private val addAutoHideAgentLog = makeMigration(22..23) { db ->
      db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `autoHideAgentLogOnCompletion` INTEGER NOT NULL DEFAULT 0")
  }
  ```
  Append `addAutoHideAgentLog` to the `aiSettingsMigrations` array.
- Bump `AI_SETTINGS_DATABASE_VERSION` 22 → 23 in `Databases.kt`.
- Accessor in `AiSettings.kt`:
  ```kotlin
  var autoHideAgentLogOnCompletion: Boolean
      get() = getOrDefault().autoHideAgentLogOnCompletion
      set(value) = update { copy(autoHideAgentLogOnCompletion = value) }
  ```

### 3. Auto-hide logic in `AgentLogWidget`

Decision logic extracted to a pure, testable function (companion object):

```kotlin
/** True if the panel should auto-hide for this terminal state. */
fun shouldAutoHide(settingEnabled: Boolean, reason: AgentStopReason?): Boolean =
    settingEnabled && reason != null && reason != AgentStopReason.ERROR
```

In `onEventMainThread(event: AgentSessionStatusChangedEvent)`, in the
`!event.isRunning` branch (after `stopStatusAnimation()` / button update):

```kotlin
if (visibility == View.VISIBLE &&
    shouldAutoHide(CommonUtils.aiSettings.autoHideAgentLogOnCompletion, event.stopReason)) {
    hide()
    if (event.stopReason == AgentStopReason.COMPLETED) {
        ABEventBus.post(ToastEvent(R.string.ai_task_completed))
    }
}
```

The existing auto-show on start (`if (event.isRunning && visibility != VISIBLE) show()`)
is unchanged.

### 4. Settings UI

- `ai_connection_settings.xml`: add a `SwitchPreferenceCompat` with key
  `auto_hide_agent_log_on_completion`, `defaultValue="false"`, title and summary
  strings, near the existing `ask_model_before_run` switch.
- `strings.xml`: add `auto_hide_agent_log_title` and `auto_hide_agent_log_summary`
  (English only, per project translation policy).
- `AiConnectionSettingsActivity.kt`: declare the pref field, `findPreference` it
  in setup, and add `setupAutoHideAgentLog()` mirroring `setupAskModelBeforeRun()`:
  ```kotlin
  autoHideAgentLogPref.isChecked = settings.autoHideAgentLogOnCompletion
  autoHideAgentLogPref.setOnPreferenceChangeListener { _, newValue ->
      settings.autoHideAgentLogOnCompletion = newValue as Boolean
      true
  }
  ```

## Error handling / edge cases

- **Already hidden:** if the panel is `GONE` when the task finishes, `hide()` is
  not called and no toast fires (guarded by `visibility == View.VISIBLE`).
- **Setting off:** `shouldAutoHide` returns false → current behaviour preserved
  exactly (panel stays).
- **Start event:** `stopReason == null` → `shouldAutoHide` returns false; the
  branch only runs when `!isRunning` anyway.
- **Cancellation without explicit reason** (clear / cancellation exception): the
  `stop()` default `CANCELLED` ensures these hide (and show no toast), matching
  the agreed behaviour "hide on everything except error".

## Testing

Unit test for `AgentLogWidget.shouldAutoHide` (pure function, no Android
dependencies):

- setting OFF → false for COMPLETED, ERROR, CANCELLED, null
- setting ON → true for COMPLETED, true for CANCELLED, **false for ERROR**, false for null

This is the meaningful logic seam; the rest is straightforward UI wiring and a
mechanical DB migration.

## Out of scope

- No delay / fade-out animation (immediate hide was chosen).
- No separate toggle for the toast (toast is implied by successful auto-hide).
- No per-prompt override; this is a single global setting.
