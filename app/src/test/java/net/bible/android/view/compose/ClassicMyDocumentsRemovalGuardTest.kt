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
 * Batch Z-late phase 1, slice S7: the two classic My documents screens were deleted with their
 * four layouts and four menus, and `ScreenLauncher`'s two arms collapsed to the Compose
 * implementations.
 *
 * Four assertions, not five: nothing becomes referenceless. Both screens extended `ActivityBase`
 * directly, their adapters and ViewHolders were declared inside the two files that go, and the
 * `view/activity/mydocuments/` package contained nothing else — so there was no surviving
 * same-package neighbour that could have been reading a top-level name importlessly, which is the
 * trap S8 hit in this same batch (spec §9.5).
 *
 * Mind the package asymmetry when reading a failure from this guard: the deleted classes are
 * `net.bible.android.view.activity.mydocuments.*` while the surviving Compose twins are
 * `net.bible.android.view.mydocuments.*`, without `activity.`.
 */
class ClassicMyDocumentsRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.mydocuments.MyDocumentsActivity",
        "net.bible.android.view.activity.mydocuments.MyDocumentPagesActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/mydocuments/MyDocumentsActivity.kt",
        "src/main/java/net/bible/android/view/activity/mydocuments/MyDocumentPagesActivity.kt",
        "src/main/res/layout/my_documents_selector.xml",
        "src/main/res/layout/my_document_list_item.xml",
        "src/main/res/layout/my_document_pages_selector.xml",
        "src/main/res/layout/my_document_page_list_item.xml",
        "src/main/res/menu/my_documents_options_menu.xml",
        "src/main/res/menu/my_document_popup_menu.xml",
        "src/main/res/menu/my_document_pages_options_menu.xml",
        "src/main/res/menu/my_document_page_popup_menu.xml",
    )

    @Test fun theClassicMyDocumentsFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic My documents files, layouts or menus should have been deleted in S7. " +
                "Four layouts and four menus is the batch's heaviest resource set, and each was " +
                "proven sole-referenced before deletion per spec 8.1.",
        )
    }

    @Test fun noSourceFileNamesAClassicMyDocumentsClass() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic My documents class deleted in S7. Note the package: " +
                "the survivors are view.mydocuments.*, the deleted ones view.activity.mydocuments.*",
        )
    }

    @Test fun noManifestEntryNamesAClassicMyDocumentsClass() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S7 deletes. Watch parentActivityName specifically: the " +
                "surviving Compose MyDocumentPagesComposeActivity pointed its Up target at classic " +
                "MyDocumentsActivity before this batch, and nothing compiles against that attribute " +
                "— no test read it and no golden rendered it.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForMyDocuments() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.MyDocuments", "Screen.MyDocumentPages"),
            "these My documents arms still branch on the flag (or are missing entirely) — S7 " +
                "collapses both to their Compose classes unconditionally",
        )
    }
}
