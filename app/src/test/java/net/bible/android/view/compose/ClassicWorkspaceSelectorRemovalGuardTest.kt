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
 * Batch Z-late phase 1, slice S11: the classic workspace selector was deleted with its two layouts
 * and two menus, and `ScreenLauncher`'s arm collapsed to `WorkspaceSelectorComposeActivity`.
 *
 * Five assertions, because one file goes referenceless and must be defended:
 * `service/common/RecyclerViewSearchHelper.kt` lost its last production consumer here (the classic
 * My-documents screens that also used it went in S7). It is NOT in a §2.4-protected directory, so
 * nothing else stops a later slice from "tidying" it away — and it would go with every gate green,
 * because a referenceless file breaks no compile, moves no golden and fails no reference proof.
 * Four comments in `:sharedCore` and `:sharedUi` cite it by path AND line as the classic-parity
 * source of truth, so deleting it silently invalidates four cross-module parity claims.
 *
 * What this guard used to deliberately leave untouched: `RoutingSeamGuardTest`'s §11.4 exemption,
 * which locked the Compose selector's unconditional raw launch of classic TextDisplaySettingsActivity.
 * S11 did not open that exemption; S12 did -- "Z-late S12 prelude: route the workspace selector at
 * the Compose settings screen" deleted both the allowlist and its anti-vacuity test, since the
 * selector now launches `TextDisplaySettingsComposeActivity.intentForDetachedWorkspace` instead.
 * §11.4 is RESOLVED, not merely decided.
 */
class ClassicWorkspaceSelectorRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/workspaces/WorkspaceSelectorActivity.kt",
        "src/main/res/layout/workspace_selector.xml",
        "src/main/res/layout/workspace_list_item.xml",
        "src/main/res/menu/workspace_options_menu.xml",
        "src/main/res/menu/workspace_popup_menu.xml",
    )

    @Test fun theClassicWorkspaceSelectorFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic workspace-selector files, layouts or menus should have been deleted in " +
                "S11. workspace_selector.xml has no R.layout referrer at all — it was reached only " +
                "through the generated WorkspaceSelectorBinding.",
        )
    }

    @Test fun noSourceFileNamesTheClassicWorkspaceSelector() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name the classic WorkspaceSelectorActivity deleted in S11",
        )
    }

    @Test fun noManifestEntryNamesTheClassicWorkspaceSelector() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the class S11 deletes",
        )
    }

    @Test fun screenLauncherDoesNotBranchForTheWorkspaceSelector() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.WorkspaceSelector"),
            "the WorkspaceSelector arm still branches on the flag (or is missing entirely) — S11 " +
                "collapses it to WorkspaceSelectorComposeActivity unconditionally",
        )
    }

    @Test fun theReferencelessRecyclerViewSearchHelperStillExists() {
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/service/common/RecyclerViewSearchHelper.kt"),
            "RecyclerViewSearchHelper lost its last production consumer in S11 and is now " +
                "referenceless, so it can be deleted with every gate green. Four comments in " +
                ":sharedCore and :sharedUi cite it by path and line as the classic-parity source of " +
                "truth (WorkspaceSelectorController.kt:70, MyDocumentPagesController.kt:90, " +
                "MyDocumentsController.kt:91, WorkspaceSelectorScreen.kt:128). If it should go, " +
                "rewrite those four citations in the same change — do not delete it to make a " +
                "reference sweep quieter.",
        )
    }
}
