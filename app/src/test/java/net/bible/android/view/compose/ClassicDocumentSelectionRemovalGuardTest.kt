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

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice S6 deleted the classic document-selection cluster: the document chooser, the download
 * screen and its first-run subclass, and the custom-repository list and editor. This guard makes
 * that permanent — see [ClassicRemovalScan] for why a deletion needs a test at all (dead code
 * compiles cleanly, so the compiler proves nothing about it).
 */
class ClassicDocumentSelectionRemovalGuardTest {

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/ChooseDocument.kt",
        "src/main/java/net/bible/android/view/activity/navigation/DocumentItemAdapter.kt",
        "src/main/java/net/bible/android/view/activity/download/DownloadActivity.kt",
        "src/main/java/net/bible/android/view/activity/download/FirstDownload.kt",
        "src/main/java/net/bible/android/view/activity/download/DocumentDownloadItemAdapter.kt",
        "src/main/java/net/bible/android/view/activity/download/DocumentListItem.kt",
        "src/main/java/net/bible/android/view/activity/download/CustomRepositories.kt",
        "src/main/java/net/bible/android/view/activity/download/CustomRepositoryEditor.kt",
        "src/main/res/layout/document_list_item.xml",
        "src/main/res/layout/custom_repositories.xml",
        "src/main/res/layout/custom_repository_item.xml",
        "src/main/res/layout/custom_repository_editor.xml",
        "src/main/res/menu/choose_document_menu.xml",
        "src/main/res/menu/download_documents.xml",
        "src/main/res/menu/custom_repositories_options_menu.xml",
        "src/main/res/menu/custom_repository_editor_options_menu.xml",
    )

    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.ChooseDocument",
        "net.bible.android.view.activity.navigation.DocumentItemAdapter",
        "net.bible.android.view.activity.download.DownloadActivity",
        "net.bible.android.view.activity.download.FirstDownload",
        "net.bible.android.view.activity.download.DocumentDownloadItemAdapter",
        "net.bible.android.view.activity.download.DocumentListItem",
        "net.bible.android.view.activity.download.CustomRepositories",
        "net.bible.android.view.activity.download.CustomRepositoryEditor",
    )

    /**
     * The resources this slice deliberately KEEPS. Both are shared with a surviving Compose
     * host (the books_not_downloaded layouts, which Appendix A attributes to the classic screen
     * alone — deleting them is a runtime Resources$NotFoundException, not a compile error). Their
     * inflater is now `NavHostComposeActivity.warnUserBooksNotDownloaded`, which nav-graph slice 4
     * Task 7a ported out of `DownloadComposeActivity` along with the rest of the Download host's
     * baggage; the classic Activity inflated them too until nav-graph slice 4 Task 9 deleted it, so
     * the two layouts outlive both of those facts changing. `DocumentBadges.kt` holds top-level
     * declarations that outlived the deleted classes.
     */
    private val survivingCollaborators = listOf(
        "src/main/res/layout/books_not_downloaded_dialog.xml",
        "src/main/res/layout/books_not_downloaded_list_item.xml",
        "src/main/java/net/bible/android/view/activity/download/DocumentBadges.kt",
    )

    /**
     * nav-graph slice 8 F7: `DocumentSelectionBase` lost its only two subclasses (the abstract,
     * subclass-less `ChooseKeyBase` and `DocumentSelectionBase` itself, per the slice 8 plan's
     * "Corrections to the spec", correction 10) and is deleted. `document_selection.xml` (its binding) and
     * `document_context_menu.xml` go with it: the menu was kept past S6 only because
     * `DocumentSelectionBase.onActionItemClicked` still named `R.id.about` and `R.id.delete_index`
     * from it (see the superseded note this replaces) — with that reader gone, it has no referrer
     * left anywhere under any source set's `res` tree.
     */
    private val goneWithItsLastSubclass = listOf(
        "src/main/java/net/bible/android/view/activity/base/DocumentSelectionBase.kt",
        "src/main/res/layout/document_selection.xml",
        "src/main/res/menu/document_context_menu.xml",
    )

    @Test fun theClassicDocumentSelectionFilesAreGone() =
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "a classic document-selection source or resource is back; slice S6 deleted the cluster, " +
                "so the Compose screens are the only implementation",
        )

    @Test fun theSurvivingDocumentSelectionCollaboratorsStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            survivingCollaborators,
            "a collaborator slice S6 deliberately kept has been deleted: DocumentBadges.kt holds " +
                "the top-level declarations that outlived the deleted classes, and both " +
                "books_not_downloaded layouts are inflated by the SURVIVING NavHostComposeActivity " +
                "(DownloadComposeActivity inflated them too until nav-graph slice 4 Task 9 deleted it)",
        )

    @Test fun theSpec24ResidueWentWithItsLastSubclass() =
        ClassicRemovalScan.assertPathsGone(
            goneWithItsLastSubclass,
            "slice 8 F7: zero subclasses; spec §2 deletes the family's dead members",
        )

    @Test fun noSourceFileNamesAClassicDocumentSelectionClass() =
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "a shipping source file or resource XML still names a classic document-selection class; " +
                "the fix is to remove the reference (for a layout, the offending TAG), not to " +
                "restore the class",
        )

    @Test fun noManifestEntryNamesAClassicDocumentSelectionClass() =
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a classic document-selection class -- check android:name AND " +
                "android:parentActivityName; the latter compiles, tests and renders fine while " +
                "pointing Up at a class that does not exist",
        )

    @Test fun theDocumentSelectionArmsAreUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf(
                "Screen.ChooseDocument",
                "Screen.Download",
                "Screen.CustomRepositories",
                "Screen.CustomRepositoryEditor",
            ),
            "a document-selection arm still branches; slice S6 deleted the classic " +
                "class it would branch to",
        )

    /**
     * nav-graph slice 4 Task 7b's backstop for spec §9 risk 3.
     * `migratedScreenArgumentIsNeverDroppedByAPutExtra` (`NavHostRoutingGuardTest.kt`) cannot see
     * this shape: `StartupComposeActivity.firstDownloadIntent()` used to be an expression-bodied
     * function whose OTHER two extras were attached 14 and 49 lines away by its own callers, and
     * `Screen.Download` was never in `ScreenLauncher.MIGRATED` in the first place (five arguments),
     * so that guard's putExtra scan does not even look at these call sites. A plain containment
     * scan over every shipping source catches what the shape-aware scan cannot: none of the five
     * classic Download intent extras may survive anywhere, because Task 7b moved every one of them
     * onto `NavRoutes.download(...)`'s route arguments instead.
     */
    @Test
    fun noCallSiteStillPutsADownloadExtraOnAnIntent() {
        val offenders = ClassicRemovalScan.appSources().filter { file ->
            val text = file.readText()
            listOf(
                """putExtra("download-recommended"""",
                """putExtra("search"""",
                """putExtra("addons"""",
                "putExtra(DownloadKeys.DOCUMENT_IDS_EXTRA",
                "EXTRA_FIRST_DOWNLOAD",
            ).any { text.contains(it) }
        }
        assertTrue("download extras survive at: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
