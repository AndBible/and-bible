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
package net.bible.sharedui

import androidx.annotation.DrawableRes
import net.bible.android.activity.R

/**
 * Maps a [net.bible.sharedcore.settings.SettingsItem] `iconKey` to its classic drawable resource, if
 * any. Backs the `LocalSettingsIcon` seam ([ProvideAppLocals]) so moved Compose settings screens can
 * show the classic per-row leading icon. Unknown/unmapped keys return `null` — the row then renders
 * with no leading icon (same as an item with `iconKey == null`), never a crash or a placeholder box.
 *
 * F29 (Batch B): AI connection-settings row keys, mirroring classic `res/xml/ai_connection_settings.xml`
 * `android:icon` values verbatim. The seam's Painter is always rendered via Compose `Icon` (not `Image`)
 * by [net.bible.sharedui.settings.AbSettingsScreen], which tints every pixel to the current content
 * colour — so a colour-coded classic icon (e.g. the red warning triangle) still renders correctly
 * grayscale in monochrome/e-ink mode; only the shape carries meaning here, not the drawable's baked-in
 * colour. `ask_model_before_run`/`auto_hide_agent_log_on_completion` are mapped for completeness (classic
 * parity) even though [net.bible.sharedcore.settings.SettingsItem.SwitchRow] doesn't render a leading
 * icon yet ([net.bible.sharedui.components.AbSwitchRow] has no icon slot) — see F29 follow-up note in
 * `AbSettingsScreen.kt`.
 */
@DrawableRes
fun settingsDrawableRes(key: String): Int? = when (key) {
    "ai_disclaimer_warning" -> R.drawable.ic_warning_red_24dp
    "ai_getting_started" -> R.drawable.ic_baseline_cloud_24
    "ai_providers_shortcut" -> R.drawable.ic_baseline_cloud_24
    "ai_models_shortcut" -> R.drawable.icon_robot
    "ai_language" -> R.drawable.ic_baseline_description_gray_24
    "agent_permission_mode" -> R.drawable.ic_baseline_security_24
    "manage_tool_permissions" -> R.drawable.ic_baseline_security_24
    "manage_ai_documents" -> R.drawable.ic_baseline_description_gray_24
    "commentary_max_response_chars" -> R.drawable.ic_baseline_description_gray_24
    "agent_max_iterations" -> R.drawable.ic_baseline_description_gray_24
    "ask_model_before_run" -> R.drawable.ic_baseline_description_gray_24
    "auto_hide_agent_log_on_completion" -> R.drawable.ic_baseline_description_gray_24
    "custom_agent_system_prompt" -> R.drawable.ic_baseline_description_gray_24
    "custom_text_transform_system_prompt" -> R.drawable.ic_baseline_description_gray_24
    "llm_usage_summary" -> R.drawable.ic_baseline_description_gray_24
    "llm_reset_usage" -> R.drawable.ic_baseline_refresh_gray_24
    "raw_log_history" -> R.drawable.ic_baseline_description_gray_24
    "raw_log_retention" -> R.drawable.ic_delete_24dp
    else -> null
}
