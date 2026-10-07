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
 * Batch Z-late phase 1, slice S8: the classic Cloud documents screen and its adapter were deleted
 * and `ScreenLauncher`'s arm collapsed to the Compose implementation. This guard makes the deletion
 * durable — a regression that re-added a file, an import, a manifest entry or a flag branch would
 * otherwise only be noticed if something else broke.
 *
 * Four assertions rather than S3's five: no file becomes referenceless in this slice, so there is
 * nothing for [ClassicRemovalScan.assertPathsPresent] to defend. `CloudDocumentsComposeActivity`
 * survived in the same package with a live consumer at the time (defended by the compiler) until
 * nav-graph slice 4 Task 9 deleted it too, once its screen had a nav-graph destination of its own;
 * `CloudSyncProgressBridge` survived it too, until ABEventBus phase 3 replaced it with
 * `DocumentSync.runningChanged`.
 *
 * What this guard canNOT see, recorded here because it is this slice's real hazard: the deleted
 * activity declared eight top-level functions that three same-package test classes called with no
 * import at all. A same-package importless read is invisible to a class-name sweep and to an
 * import sweep alike (spec §9.5's third leg), so those 42 assertions were ported into
 * `:sharedCore`'s `CloudDocFunctionsTest`/`CloudDocModelsTest` before this deletion, and it is
 * `:sharedCore:jvmTest` — not this file — that keeps them honest.
 */
class ClassicCloudDocumentsRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.cloud.CloudDocumentsActivity",
        "net.bible.android.view.activity.cloud.CloudDocumentsAdapter",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt",
        "src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt",
        "src/main/res/layout/activity_cloud_documents.xml",
        "src/main/res/layout/item_cloud_document.xml",
        "src/main/res/menu/cloud_documents_selection.xml",
        "src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt",
        "src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsMenuTest.kt",
        "src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsBulkTest.kt",
    )

    @Test fun theClassicCloudDocumentsFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic Cloud documents files, resources or tests should have been deleted in " +
                "S8. The three test files are in the list on purpose: they exercised top-level " +
                "functions declared inside CloudDocumentsActivity.kt by bare same-package name, " +
                "and their assertions were ported to :sharedCore's cloud tests first.",
        )
    }

    @Test fun noSourceFileNamesAClassicCloudDocumentsClass() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic Cloud documents class deleted in S8",
        )
    }

    @Test fun noManifestEntryNamesAClassicCloudDocumentsClass() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S8 deletes — either a leftover <activity> block or a " +
                "parentActivityName. Neither is a compile error and neither breaks another test; " +
                "the app would simply reference a class that is gone.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForCloudDocuments() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.CloudDocuments"),
            "the Cloud documents arm still branches on the flag (or is missing entirely) — S8 " +
                "collapses it to the Compose class unconditionally",
        )
    }
}
