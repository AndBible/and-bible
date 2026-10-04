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
 * Batch Z-late phase 1, slice S10 (second half): the classic manage-prompts screen, the prompt
 * editor and the two raw-log screens were deleted with their nine resources, together with
 * `ToolPermissionListBuilder` — whose last consumer was the prompt editor — and its two item
 * layouts. `ScreenLauncher`'s four arms collapsed to the Compose implementations, closing S10.
 *
 * Read the class names carefully. `AiSettingsActivity` is NOT the AI settings screen: despite its
 * name it is the classic MANAGE-PROMPTS screen (spec Appendix B A6), `Screen.AiPrompts`' classic
 * target, and its Compose twin is `AiPromptsComposeActivity`. There is no `AiSettingsComposeActivity`.
 * The `AiSettings` prefix names four files here, of which only one is doomed —
 * `AiSettingsServiceImpl` is a surviving Koin-bound Compose seam and `AiSettingsFragmentBase` was
 * S10-A's. A bare-name grep is worthless in this package; these FQNs are not.
 *
 * One resource is deliberately KEPT, and this hint is the only place that is written down:
 *   - `res/xml/prompt_advanced_settings.xml` — a live fixture for `SettingsIconParityTest.kt:102`,
 *     which pins the Compose `settingsDrawableRes` table by PARSING the classic XML at test time.
 *     Deleting it is a compile error in that test, and would lose the drift-from-classic property
 *     that is the test's whole point. The same test pins three S12 res/xml files, so this choice is
 *     S12's precedent.
 */
class ClassicAiPromptsRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.ai.AiSettingsActivity",
        "net.bible.android.view.activity.ai.PromptEditActivity",
        "net.bible.android.view.activity.ai.RawLogHistoryActivity",
        "net.bible.android.view.activity.ai.RawLlmLogActivity",
        "net.bible.android.view.activity.ai.RawLlmLogAdapter",
        "net.bible.android.view.activity.ai.ToolPermissionListBuilder",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/ai/AiSettingsActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/PromptEditActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/RawLogHistoryActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/RawLlmLogActivity.kt",
        "src/main/java/net/bible/android/view/activity/ai/RawLlmLogAdapter.kt",
        "src/main/java/net/bible/android/view/activity/ai/ToolPermissionListBuilder.kt",
        "src/main/res/layout/manage_prompts.xml",
        "src/main/res/layout/manage_prompts_list_item.xml",
        "src/main/res/menu/manage_prompts_options_menu.xml",
        "src/main/res/layout/prompt_edit.xml",
        "src/main/res/menu/prompt_edit_options_menu.xml",
        "src/main/res/layout/activity_raw_log_history.xml",
        "src/main/res/layout/raw_log_history_item.xml",
        "src/main/res/layout/activity_raw_llm_log.xml",
        "src/main/res/layout/raw_llm_log_item.xml",
        "src/main/res/layout/item_tool_category_header.xml",
        "src/main/res/layout/item_tool_permission.xml",
        // Platform-dialog removal Task 10: `LlmDialogHelper.kt` (the reading-view chrome that
        // inflated these two) is deleted outright -- its `?:` fallback was unreachable once slice 8
        // made NavHost the only reading host -- so these move here from "deliberately KEPT" to
        // "gone". Nothing else inflates them (grep confirmed).
        "src/main/res/layout/manage_prompts_category_header.xml",
        "src/main/res/layout/prompt_selector_item.xml",
    )

    @Test fun theClassicPromptAndRawLogFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic prompt/raw-log files or resources should have been deleted in S10-B. " +
                "ToolPermissionListBuilder and its two item layouts are here rather than in S10-A " +
                "because PromptEditActivity was its second consumer; see this class's KDoc for the " +
                "two resources that were deliberately KEPT.",
        )
    }

    @Test fun noSourceFileNamesAClassicPromptOrRawLogScreen() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic screen deleted in S10-B. The FQNs' trailing boundary " +
                "spares AiSettingsServiceImpl and AiSettingsFragmentBase, so a hit on " +
                "…ai.AiSettingsActivity is a real reference to the manage-prompts screen.",
        )
    }

    @Test fun noManifestEntryNamesAClassicPromptOrRawLogScreen() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S10-B deletes. Task 1 repointed the three surviving " +
                "Compose blocks that pointed Up at these classes (:231, :245, :312) before any " +
                "deletion; a failure here on one of those means Task 1 did not land.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForThePromptAndRawLogScreens() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.AiPrompts", "Screen.PromptEdit", "Screen.RawLogHistory", "Screen.RawLlmLog"),
            "one of these four arms still branches on the flag (or is missing entirely) — S10-B " +
                "collapses all four to their Compose classes unconditionally, closing S10",
        )
    }

    @Test fun theKeptAiCollaboratorsStillExist() {
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/res/xml/prompt_advanced_settings.xml",
                "src/main/java/net/bible/android/view/activity/ai/AgentLogAdapter.kt",
                "src/main/res/layout/agent_log_item.xml",
            ),
            "one of S10's deliberate survivors was deleted. prompt_advanced_settings.xml is " +
                "SettingsIconParityTest's live fixture; AgentLogAdapter + agent_log_item belong to " +
                "the §2.4-protected AgentLogWidget, not to any AI screen. " +
                "manage_prompts_category_header.xml/prompt_selector_item.xml used to be here too " +
                "(inflated by LlmDialogHelper) -- platform-dialog removal Task 10 moved them to " +
                "doomedPaths once LlmDialogHelper was deleted.",
        )
    }
}
