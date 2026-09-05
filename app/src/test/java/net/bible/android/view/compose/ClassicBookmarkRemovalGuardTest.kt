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
 * Batch Z-late phase 1, slice S9: the classic bookmarks-and-labels cluster — three screens and
 * their two list adapters — was deleted with six layouts and five menus, and `ScreenLauncher`'s
 * three arms collapsed onto the Compose implementations. 16 files, 3026 lines; the batch's
 * largest deletion, and the only one in it that needed no file split (spec 9.5's three legs all
 * came back empty — P2 had already moved every shared contract type out).
 *
 * Five assertions, not four: unlike S7, this slice DOES leave something referenceless — see
 * [survivingCollaborators]. Note the naming asymmetry when reading a failure: the deleted classes
 * and their surviving Compose twins share the package `net.bible.android.view.activity.bookmark`,
 * so the FQN sweep leans entirely on [ClassicRemovalScan.refsFor]'s trailing boundary to keep
 * `…bookmark.Bookmarks` from matching `…bookmark.BookmarksComposeActivity` /
 * `…bookmark.BookmarksServiceImpl`, and `…bookmark.ManageLabels` from matching
 * `…bookmark.ManageLabelsComposeActivity` / `…Contract` / `…Mapper` / `…ServiceImpl`.
 */
class ClassicBookmarkRemovalGuardTest {

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/bookmark/Bookmarks.kt",
        "src/main/java/net/bible/android/view/activity/bookmark/ManageLabels.kt",
        "src/main/java/net/bible/android/view/activity/bookmark/LabelEditActivity.kt",
        "src/main/java/net/bible/android/view/activity/bookmark/BookmarkItemAdapter.kt",
        "src/main/java/net/bible/android/view/activity/bookmark/ManageLabelItemAdapter.kt",
        "src/main/res/layout/bookmarks.xml",
        "src/main/res/layout/bookmark_list_item.xml",
        "src/main/res/layout/manage_labels.xml",
        "src/main/res/layout/manage_labels_list_item.xml",
        "src/main/res/layout/manage_labels_search_result_item.xml",
        "src/main/res/layout/bookmark_label_edit.xml",
        "src/main/res/menu/bookmark_context_menu.xml",
        "src/main/res/menu/bookmark_actionbar_menu.xml",
        "src/main/res/menu/search_mode_menu.xml",
        "src/main/res/menu/manage_labels_options_menu.xml",
        "src/main/res/menu/edit_label_options_menu.xml",
    )

    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.bookmark.Bookmarks",
        "net.bible.android.view.activity.bookmark.ManageLabels",
        "net.bible.android.view.activity.bookmark.LabelEditActivity",
        "net.bible.android.view.activity.bookmark.BookmarkItemAdapter",
        "net.bible.android.view.activity.bookmark.ManageLabelItemAdapter",
    )

    /**
     * What S9 deliberately keeps although nothing references it any more. Both are spec 2.4
     * residue, and both live in `view/util/widget/` — the first time this phase has produced
     * residue from that directory rather than from `view/activity/base/`, which is why they are
     * easy to miss.
     *
     * `BookmarkStyleAdapterHelper.kt`'s only consumer was the deleted `ManageLabelItemAdapter`
     * (`:25,43`); after S9 its sole occurrence in the whole tree is its own declaration.
     * `BookmarkListItem.kt` was inflated by fully-qualified name from two layouts, and S9 deletes
     * one of them (`bookmark_list_item.xml`); the survivor, `studypad_list_item.xml`, is itself on
     * the epilogue's dead-layout list, so this widget is now reachable from exactly one place and
     * that place is scheduled to go. Neither may be swept up as "obviously unused": spec 2.4
     * protects `view/util/widget/` by name.
     *
     * `res/layout/list_content_simple.xml` is NOT in this list on purpose. It is also kept, but
     * it is not referenceless — `ListActivityBase.kt:178` still inflates it and three classic
     * screens in later slices still extend that base.
     */
    private val survivingCollaborators = listOf(
        "src/main/java/net/bible/android/view/util/widget/BookmarkStyleAdapterHelper.kt",
        "src/main/java/net/bible/android/view/util/widget/BookmarkListItem.kt",
    )

    @Test fun theClassicBookmarkFilesAndResourcesAreGone() =
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "a classic bookmarks/labels source, layout or menu is back; slice S9 deleted all 16, " +
                "each proven sole-referenced by name AND by every @+id/ it defines (spec 8.1), so " +
                "the Compose BookmarksComposeActivity / ManageLabelsComposeActivity / " +
                "LabelEditComposeActivity are the only implementations",
        )

    @Test fun theSurvivingBookmarkWidgetsStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            survivingCollaborators,
            "a widget slice S9 deliberately kept has been deleted. Both went referenceless with " +
                "S9 and both are spec 2.4-protected view/util/widget/ files: deleting them passes " +
                "compile, passes the reference proof and moves no golden, which is exactly why " +
                "this assertion exists",
        )

    @Test fun noSourceFileNamesAClassicBookmarkClass() =
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "a shipping source file or resource XML still names a classic bookmarks/labels class " +
                "deleted in S9; the fix is to remove the reference (for a layout, the offending " +
                "TAG), not to restore the class",
        )

    @Test fun noManifestEntryNamesAClassicBookmarkClass() =
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a classic bookmarks/labels class -- check android:name AND " +
                "android:parentActivityName. S9 had TWO live parentActivityName instances on " +
                "SURVIVING Compose blocks (LabelEditComposeActivity Up-> classic ManageLabels, " +
                "ManageLabelsComposeActivity Up-> classic Bookmarks); that attribute compiles, " +
                "tests and renders fine while pointing Up at a class that does not exist",
        )

    @Test fun theBookmarkArmsAreUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.LabelEdit", "Screen.ManageLabels", "Screen.Bookmarks"),
            "a bookmarks/labels arm still consults use_compose_ui (or is missing entirely); S9 " +
                "deleted the classic classes all three would branch to",
        )
}
