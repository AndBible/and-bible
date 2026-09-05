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

package net.bible.android.view.compose

import org.junit.Test

/**
 * Batch Z-late phase 1, slice S10 (first half): the six classic AI settings and tool-permission
 * screens were deleted with their fragment-base trio and ten resources, and `ScreenLauncher`'s six
 * arms collapsed to the Compose implementations.
 *
 * S10 is one slice split across two tasks for reviewable size; `ClassicAiPromptsRemovalGuardTest`
 * guards the other half (`AiSettingsActivity` — which despite its name is the manage-prompts
 * screen — `PromptEditActivity`, `RawLogHistoryActivity`, `RawLlmLogActivity`).
 *
 * Four assertions, not five: every doomed class extended `ActivityBase` directly, so nothing in a
 * §2.4-protected directory goes referenceless. `AiSettingsFragmentBase` DOES lose its last subclass
 * and is deliberately DELETED rather than kept: §2.4 protects `view/activity/base/` and
 * `view/util/widget/` by name and names four specific bases, and this is none of them — it is a
 * screen-local base in its own screens' package, which is the S5 `CalendarHeatmapView` ruling.
 *
 * Resources deliberately NOT deleted, and the reason each survives, because this hint is the only
 * place a future reader will find it:
 *   - `settings_activity.xml` + its `@+id/settings_container` — was kept for S12's
 *     `SettingsActivity.kt:105,115` at the time this guard was written, since S12 was not yet in
 *     this batch. S12 has since deleted `SettingsActivity.kt` itself and, with it,
 *     `settings_activity.xml` (Z-late slice S12) -- this layout is GONE now, and
 *     `ClassicSettingsRemovalGuardTest` is what pins that.
 *   - `manage_prompts_category_header.xml`, `prompt_selector_item.xml` — inflated by the SURVIVING
 *     `LlmDialogHelper.kt:112,126`, which is reading-view chrome for `MainBibleActivity`, not an
 *     AI-settings collaborator.
 *   - `item_tool_category_header.xml`, `item_tool_permission.xml`, `manage_prompts_options_menu.xml`
 *     — the other half's, deleted by S10-B.
 */
class ClassicAiSettingsRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.ai.AiConnectionSettingsActivity",
        "net.bible.android.view.activity.ai.AiProvidersActivity",
        "net.bible.android.view.activity.ai.AiModelsActivity",
        "net.bible.android.view.activity.ai.GlobalToolPermissionsActivity",
        "net.bible.android.view.activity.ai.ToolInfoActivity",
        "net.bible.android.view.activity.ai.AiDocumentFilterActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/ai/AiConnectionSettingsActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/AiProvidersActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/AiModelsActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/GlobalToolPermissionsActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/ToolInfoActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/AiDocumentFilterActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/AiSettingsFragmentBase.kt",
        "src/main/java/net/bible/android/view/activity/ai/EasySetupDialogs.kt",
        "src/main/java/net/bible/android/view/activity/ai/ModelDialogs.kt",
        "src/main/res/menu/ai_connection_options_menu.xml",
        "src/main/res/menu/ai_providers_options_menu.xml",
        "src/main/res/menu/ai_models_options_menu.xml",
        "src/main/res/menu/prompt_tool_permissions_menu.xml",
        "src/main/res/xml/ai_connection_settings.xml",
        "src/main/res/xml/ai_providers_settings.xml",
        "src/main/res/xml/ai_models_settings.xml",
        "src/main/res/layout/activity_prompt_tool_permissions.xml",
        "src/main/res/layout/activity_tool_info.xml",
        "src/main/res/layout/item_tool_info.xml",
    )

    @Test fun theClassicAiSettingsFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic AI settings files or resources should have been deleted in S10-A. Six of " +
                "the ten resources are ViewBinding-inflated, so a snake_case grep finds none of " +
                "their consumers and reads as 'already dead' — ownership was established from the " +
                "generated Binding class name instead. See this class's KDoc for what was KEPT.",
        )
    }

    @Test fun noSourceFileNamesAClassicAiSettingsScreen() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic AI settings screen deleted in S10-A. Note that this " +
                "scan walks app/src only, so the four FULLY-QUALIFIED dangling Dokka links in " +
                ":sharedUi (ToolPermissionList.kt:71, GlobalToolPermissionsScreen.kt:47, " +
                "ToolInfoScreen.kt:45, AiDocumentFilterScreen.kt:58) are invisible to it and are " +
                "tail-sweep work, not a failure of this guard.",
        )
    }

    @Test fun noManifestEntryNamesAClassicAiSettingsScreen() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S10-A deletes. This is the assertion that catches the " +
                "parentActivityName cluster: TEN lines pointed at AiConnectionSettingsActivity, the " +
                "largest single-class cluster the phase has met, five of them on SURVIVING Compose " +
                "blocks that Task 1 repointed before any deletion.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForTheAiSettingsScreens() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf(
                "Screen.AiConnectionSettings",
                "Screen.AiProviders",
                "Screen.AiModels",
                "Screen.GlobalToolPermissions",
                "Screen.ToolInfo",
                "Screen.AiDocumentFilter",
            ),
            "one of these six AI arms still branches on the flag (or is missing entirely) — S10-A " +
                "collapses all six to their Compose classes unconditionally",
        )
    }
}
