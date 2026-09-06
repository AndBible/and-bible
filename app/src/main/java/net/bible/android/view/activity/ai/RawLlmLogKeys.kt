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
package net.bible.android.view.activity.ai

/**
 * Raw LLM log intent keys, lifted out of the classic `RawLlmLogActivity`'s companion so that they
 * would outlive it (Batch Z-late phase 1). That class, and the classic `RawLogHistoryActivity` and
 * `AgentLogWidget` that also read these keys, are all gone now — slice S10-B deleted the first two
 * and the epilogue's Task 5 the widget.
 *
 * **No live reader as of Task 8.** `RawLlmLogComposeActivity` and `RawLogHistoryComposeActivity`
 * still name these two constants in their own dead code (both are unreachable via `ScreenLauncher`
 * now that `Screen.RawLlmLog`/`Screen.RawLogHistory` are in `MIGRATED` — Task 10 deletes them), and
 * `ComposeReadingViewHost` stopped reading [EXTRA_WORKSPACE_ID] the same task (it now builds
 * `NavRoutes.rawLlmLog(workspaceId = ...)` directly). Kept anyway, deliberately, rather than deleted
 * along with [net.bible.android.view.activity.IntentKeysTest.rawLlmLogKeysAreUnchanged]: the object
 * is free to keep (a plain `String` pair, no maintenance burden) and documents the wire-format
 * history of the two extra names the nav-graph route arguments ([net.bible.sharedcore.nav.NavRoutes.ARG_LOG_RECORD_ID]/
 * [net.bible.sharedcore.nav.NavRoutes.ARG_WORKSPACE_ID]) replaced — deleting it would erase that
 * trail for no benefit. Revisit at Task 10 alongside the two dead Activities that still reference it.
 */
object RawLlmLogKeys {
    const val EXTRA_WORKSPACE_ID = "workspace_id"
    const val EXTRA_LOG_RECORD_ID = "log_record_id"
}
