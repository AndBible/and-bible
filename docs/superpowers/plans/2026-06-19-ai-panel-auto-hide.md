# AI Panel Auto-Hide on Task Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an off-by-default AI setting that auto-hides the AI agent log panel when a task finishes, except on error (panel stays visible); show a toast when hidden after success.

**Architecture:** A pure decision function `shouldAutoHideAgentLog(settingEnabled, reason)` plus a new `AgentStopReason` enum threaded through `AgentSession.stop()` and `AgentSessionStatusChangedEvent`. The `AgentLogWidget` consumes the reason on the stop event and hides itself when appropriate. The setting is a new boolean column on the syncable `GlobalAiSettings` entity (DB migration 22→23) exposed via `AiSettings` and a `SwitchPreferenceCompat`.

**Tech Stack:** Kotlin, Android, Room (syncable `AiSettingsDatabase`), AndroidX Preference, JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-19-ai-panel-auto-hide-design.md`

**Note on placement (refinement of spec §3):** The spec described `shouldAutoHide` as a companion-object function on `AgentLogWidget`. This plan instead places the enum and the pure function as top-level declarations in the `net.bible.service.llm.agent` package (next to `AgentSessionManager`), mirroring the existing `checkPermission` / `PermissionCheckerTest` pattern. This keeps the function unit-testable on the plain JVM without loading the `LinearLayout`-derived View class. Behaviour is identical.

**Gradle note:** All `./gradlew` commands require `dangerouslyDisableSandbox: true`. If a Gradle command fails with a journal/cache lock error, do NOT kill processes or run `--stop` — another sandbox may be building. Wait and retry, or ask the user.

---

### Task 1: Stop-reason enum and pure auto-hide decision function (TDD)

**Files:**
- Modify: `app/src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt` (add enum + function before `AgentSessionStatusChangedEvent`, currently at line 82)
- Test: `app/src/test/java/net/bible/service/llm/agent/AgentLogAutoHideTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/llm/agent/AgentLogAutoHideTest.kt`:

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

package net.bible.service.llm.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLogAutoHideTest {

    @Test
    fun settingOff_neverHides() {
        for (reason in listOf(
            AgentStopReason.COMPLETED, AgentStopReason.ERROR, AgentStopReason.CANCELLED, null
        )) {
            assertFalse(shouldAutoHideAgentLog(settingEnabled = false, reason = reason))
        }
    }

    @Test
    fun settingOn_hidesOnCompleted() {
        assertTrue(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.COMPLETED))
    }

    @Test
    fun settingOn_hidesOnCancelled() {
        assertTrue(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.CANCELLED))
    }

    @Test
    fun settingOn_doesNotHideOnError() {
        assertFalse(shouldAutoHideAgentLog(settingEnabled = true, reason = AgentStopReason.ERROR))
    }

    @Test
    fun settingOn_doesNotHideOnNullReason() {
        assertFalse(shouldAutoHideAgentLog(settingEnabled = true, reason = null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.AgentLogAutoHideTest"
```
Expected: FAIL — compilation error, `AgentStopReason` and `shouldAutoHideAgentLog` are unresolved references.

- [ ] **Step 3: Add the enum and function**

In `app/src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt`, insert immediately before the `class AgentSessionStatusChangedEvent(` declaration (currently line 82):

```kotlin
/** Terminal outcome of an agent session, carried on [AgentSessionStatusChangedEvent]. */
enum class AgentStopReason { COMPLETED, ERROR, CANCELLED }

/**
 * Whether the agent log panel should auto-hide for a terminal session state.
 * Pure function — unit tested in AgentLogAutoHideTest. Hides for any non-error
 * terminal reason when the setting is enabled; keeps the panel visible on error
 * (so the user can read it) and ignores the start event (reason == null).
 */
fun shouldAutoHideAgentLog(settingEnabled: Boolean, reason: AgentStopReason?): Boolean =
    settingEnabled && reason != null && reason != AgentStopReason.ERROR
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.AgentLogAutoHideTest"
```
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt \
        app/src/test/java/net/bible/service/llm/agent/AgentLogAutoHideTest.kt
git commit -m "Add AgentStopReason enum and shouldAutoHideAgentLog decision function"
```

---

### Task 2: Thread stop reason through session stop and status event

**Files:**
- Modify: `app/src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt:82-85` (event class), `:137-146` (`stop()`), `:659` (Error), `:673` (success)

- [ ] **Step 1: Add `stopReason` to the status event**

Replace the event class (currently lines 82-85):

```kotlin
class AgentSessionStatusChangedEvent(
    val workspaceId: IdType,
    val isRunning: Boolean
)
```

with:

```kotlin
class AgentSessionStatusChangedEvent(
    val workspaceId: IdType,
    val isRunning: Boolean,
    /** Terminal outcome when [isRunning] is false; null on the start event. */
    val stopReason: AgentStopReason? = null
)
```

- [ ] **Step 2: Add a `reason` parameter to `stop()` and post it**

Replace `AgentSession.stop()` (currently lines 137-146):

```kotlin
    fun stop(message: String? = null) {
        if (message != null) {
            val hasRawLog = rawLlmLog?.isEmpty() == false
            addLogEntry(AgentLogEntry.info(message, showRawLogLink = hasRawLog))
        }
        this.isRunning = false
        this.job?.cancel()
        this.job = null
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, false))
    }
```

with:

```kotlin
    fun stop(message: String? = null, reason: AgentStopReason = AgentStopReason.CANCELLED) {
        if (message != null) {
            val hasRawLog = rawLlmLog?.isEmpty() == false
            addLogEntry(AgentLogEntry.info(message, showRawLogLink = hasRawLog))
        }
        this.isRunning = false
        this.job?.cancel()
        this.job = null
        ABEventBus.post(AgentSessionStatusChangedEvent(workspaceId, false, reason))
    }
```

(`start()` at line 134 already posts `AgentSessionStatusChangedEvent(workspaceId, true)`; `stopReason` defaults to null there — no change needed. The cancellation/clear call sites at lines 228, 236, 313 and the `AgentEvent.Cancelled` site at line 665 rely on the `CANCELLED` default — no change needed.)

- [ ] **Step 3: Pass `ERROR` on the error path**

In `handleAgentEvent`, the `is AgentEvent.Error ->` branch (currently line 659) reads `session.stop()`. Change that single line to:

```kotlin
                session.stop(reason = AgentStopReason.ERROR)
```

- [ ] **Step 4: Pass `COMPLETED` on the success path**

In `completeSession` (currently line 673) the line reads:

```kotlin
        session.stop(app.getString(R.string.agent_log_completed))
```

Change it to:

```kotlin
        session.stop(app.getString(R.string.agent_log_completed), AgentStopReason.COMPLETED)
```

- [ ] **Step 5: Verify it compiles**

Run:
```bash
./gradlew :app:compileStandardGoogleplayDebugKotlin
```
Expected: BUILD SUCCESSFUL. (`AgentForegroundService.onEvent(AgentSessionStatusChangedEvent)` reads only `isRunning`; the added defaulted parameter does not break it.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/service/llm/agent/AgentSessionManager.kt
git commit -m "Carry stop reason on AgentSessionStatusChangedEvent"
```

---

### Task 3: Add the `autoHideAgentLogOnCompletion` setting (DB + accessor)

**Files:**
- Modify: `app/src/main/java/net/bible/service/llm/AgentPromptEntities.kt:454` (add column to `GlobalAiSettings`)
- Modify: `app/src/main/java/net/bible/android/database/Databases.kt:176` (bump version)
- Modify: `app/src/main/java/net/bible/android/database/migrations/AiSettingsMigrations.kt` (add migration + register)
- Modify: `app/src/main/java/net/bible/service/common/AiSettings.kt:114` (add accessor)

- [ ] **Step 1: Add the entity column**

In `AgentPromptEntities.kt`, in `data class GlobalAiSettings(...)`, add a field after `rawLogRetentionDays` (line 454, before the closing `)` on line 455). The line currently reads:

```kotlin
    @ColumnInfo(defaultValue = "30") val rawLogRetentionDays: Int? = 30,
```

Add immediately after it:

```kotlin
    /** When true, auto-hide the agent log panel when a task finishes (unless it errored). */
    @ColumnInfo(defaultValue = "0") val autoHideAgentLogOnCompletion: Boolean = false,
```

- [ ] **Step 2: Add the migration and register it**

In `app/src/main/java/net/bible/android/database/migrations/AiSettingsMigrations.kt`, add a new migration after `addBuiltinPromptOverride` (the last `private val` before the `aiSettingsMigrations` array):

```kotlin
private val addAutoHideAgentLog = makeMigration(22..23) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `autoHideAgentLogOnCompletion` INTEGER NOT NULL DEFAULT 0")
}
```

Then append `addAutoHideAgentLog` to the end of the `aiSettingsMigrations` array (it currently ends with `..., addBuiltinPromptOverride)`):

```kotlin
val aiSettingsMigrations: Array<Migration> = arrayOf(addEditBeforeRun, addNoDocumentCreation, addGlobalAiSettingsAndUsage, setCommentaryTokenDefault, addHiddenBuiltInPrompts, addMaxIterations, addCommentaryDeselected, addConfiguredModels, raiseCommentaryTokenDefault, addAiLanguage, addAutoIncludeFields, addAskModelBeforeRun, addBibleOnly, addIsTextTransformation, addAiDisclaimerAccepted, addPromptCategories, addCategoryHidden, addCustomSystemPrompts, addFavoritePrompts, addRawLogTable, addBuiltinPromptOverride, addAutoHideAgentLog)
```

- [ ] **Step 3: Bump the database version**

In `app/src/main/java/net/bible/android/database/Databases.kt`, line 176, change:

```kotlin
const val AI_SETTINGS_DATABASE_VERSION = 22
```

to:

```kotlin
const val AI_SETTINGS_DATABASE_VERSION = 23
```

- [ ] **Step 4: Add the `AiSettings` accessor**

In `app/src/main/java/net/bible/service/common/AiSettings.kt`, after the `rawLogRetentionDays` property (ends at line 114), add:

```kotlin
    /** When true, auto-hide the agent log panel when a task finishes (unless it errored). */
    var autoHideAgentLogOnCompletion: Boolean
        get() = getOrDefault().autoHideAgentLogOnCompletion
        set(value) = update { copy(autoHideAgentLogOnCompletion = value) }
```

- [ ] **Step 5: Verify it compiles**

Run:
```bash
./gradlew :app:compileStandardGoogleplayDebugKotlin
```
Expected: BUILD SUCCESSFUL. (Room schema export may warn; the `defaultValue` on the column matches the migration's `DEFAULT 0`, so Room's schema validation passes.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/service/llm/AgentPromptEntities.kt \
        app/src/main/java/net/bible/android/database/Databases.kt \
        app/src/main/java/net/bible/android/database/migrations/AiSettingsMigrations.kt \
        app/src/main/java/net/bible/service/common/AiSettings.kt
git commit -m "Add autoHideAgentLogOnCompletion AI setting (DB migration 22->23)"
```

---

### Task 4: Wire auto-hide into AgentLogWidget

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/util/widget/AgentLogWidget.kt` (imports near line 33-48; `onEventMainThread` at lines 336-357)

- [ ] **Step 1: Add imports**

In `app/src/main/java/net/bible/android/view/util/widget/AgentLogWidget.kt`, add these imports (alongside the existing `net.bible.*` imports; `ABEventBus`, `CommonUtils`, `AgentSessionManager`, and `R` are already imported):

```kotlin
import net.bible.android.control.event.ToastEvent
import net.bible.service.llm.agent.AgentStopReason
import net.bible.service.llm.agent.shouldAutoHideAgentLog
```

- [ ] **Step 2: Add the auto-hide block in the status handler**

In `onEventMainThread(event: AgentSessionStatusChangedEvent)`, the current body (lines 337-356) ends with the auto-show block:

```kotlin
            // Auto-show when agent starts
            if (event.isRunning && visibility != View.VISIBLE) {
                show()
            }
        }
    }
```

Insert the auto-hide block immediately after the auto-show `if` (still inside `if (event.workspaceId == workspaceId)`):

```kotlin
            // Auto-show when agent starts
            if (event.isRunning && visibility != View.VISIBLE) {
                show()
            }

            // Auto-hide on a terminal state (unless it errored), if enabled by the user.
            if (!event.isRunning &&
                visibility == View.VISIBLE &&
                shouldAutoHideAgentLog(CommonUtils.aiSettings.autoHideAgentLogOnCompletion, event.stopReason)
            ) {
                hide()
                if (event.stopReason == AgentStopReason.COMPLETED) {
                    ABEventBus.post(ToastEvent(R.string.ai_task_completed))
                }
            }
        }
    }
```

- [ ] **Step 3: Verify it compiles**

Run:
```bash
./gradlew :app:compileStandardGoogleplayDebugKotlin
```
Expected: FAIL — `R.string.ai_task_completed` is unresolved (the string is added in Task 5). This is expected; the resource is added next. (If you prefer a clean compile at this step, do Task 5 Step 1 first.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/view/util/widget/AgentLogWidget.kt
git commit -m "Auto-hide agent log panel on non-error completion"
```

---

### Task 5: Settings UI and strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (after line 1412)
- Modify: `app/src/main/res/xml/ai_connection_settings.xml` (after the `ask_model_before_run` switch, line 91-96)
- Modify: `app/src/main/java/net/bible/android/view/activity/ai/AiConnectionSettingsActivity.kt` (pref field line 102; findPreference line 127; setup call line 154; new setup function near `setupAskModelBeforeRun` line 431-437)

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, after line 1412 (`ask_model_before_run_summary`), add:

```xml
    <string name="auto_hide_agent_log_title">Hide AI panel when done</string>
    <string name="auto_hide_agent_log_summary">Automatically hide the AI panel when a task finishes successfully or is cancelled. On error the panel stays visible.</string>
    <string name="ai_task_completed">AI task completed</string>
```

- [ ] **Step 2: Add the switch preference**

In `app/src/main/res/xml/ai_connection_settings.xml`, after the `ask_model_before_run` `SwitchPreferenceCompat` (closing `/>` at line 96), before `</PreferenceCategory>` (line 97), add:

```xml
        <SwitchPreferenceCompat android:key="auto_hide_agent_log_on_completion"
            android:title="@string/auto_hide_agent_log_title"
            android:summary="@string/auto_hide_agent_log_summary"
            android:defaultValue="false"
            android:icon="@drawable/ic_baseline_description_gray_24"
            />
```

- [ ] **Step 3: Declare the preference field**

In `AiConnectionSettingsActivity.kt`, after line 102 (`private lateinit var askModelBeforeRunPref: SwitchPreferenceCompat`), add:

```kotlin
    private lateinit var autoHideAgentLogPref: SwitchPreferenceCompat
```

- [ ] **Step 4: Find the preference**

After line 127 (`askModelBeforeRunPref = preferenceScreen.findPreference("ask_model_before_run")!!`), add:

```kotlin
        autoHideAgentLogPref = preferenceScreen.findPreference("auto_hide_agent_log_on_completion")!!
```

- [ ] **Step 5: Call the setup function**

After line 154 (`setupAskModelBeforeRun()`), add:

```kotlin
        setupAutoHideAgentLog()
```

- [ ] **Step 6: Add the setup function**

After `setupAskModelBeforeRun()` (ends at line 437), add:

```kotlin
    private fun setupAutoHideAgentLog() {
        autoHideAgentLogPref.isChecked = settings.autoHideAgentLogOnCompletion
        autoHideAgentLogPref.setOnPreferenceChangeListener { _, newValue ->
            settings.autoHideAgentLogOnCompletion = newValue as Boolean
            true
        }
    }
```

- [ ] **Step 7: Verify it compiles**

Run:
```bash
./gradlew :app:compileStandardGoogleplayDebugKotlin
```
Expected: BUILD SUCCESSFUL (this resolves `R.string.ai_task_completed` from Task 4 too).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/xml/ai_connection_settings.xml \
        app/src/main/java/net/bible/android/view/activity/ai/AiConnectionSettingsActivity.kt
git commit -m "Add Hide AI panel when done setting to AI settings screen"
```

---

### Task 6: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the unit test suite for the touched module**

Run:
```bash
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.AgentLogAutoHideTest"
```
Expected: PASS (5 tests).

- [ ] **Step 2: Full debug compile**

Run:
```bash
./gradlew :app:compileStandardGoogleplayDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Confirm clean git state**

Run:
```bash
git status
```
Expected: working tree clean (all changes committed across Tasks 1-5).

---

## Self-Review Notes

- **Spec coverage:** Behaviour table → Tasks 1 (decision fn) + 2 (reason plumbing) + 4 (widget). Setting (DB-backed, default false) → Task 3. UI → Task 5. Toast on success → Task 4 Step 2. Unit test → Task 1. All spec sections covered.
- **Type consistency:** `AgentStopReason { COMPLETED, ERROR, CANCELLED }`, `shouldAutoHideAgentLog(settingEnabled, reason)`, `autoHideAgentLogOnCompletion`, and preference key `auto_hide_agent_log_on_completion` are used identically across all tasks.
- **Cross-task dependency:** Task 4 Step 3 intentionally fails to compile because `R.string.ai_task_completed` is defined in Task 5 Step 1. Final green state is reached at Task 5 Step 7 / Task 6. When executing strictly task-by-task with a compile gate, run Task 5 Step 1 (add strings) before Task 4's compile check, or accept the documented expected failure.
