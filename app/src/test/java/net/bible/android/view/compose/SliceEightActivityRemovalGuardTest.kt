/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
 * Slice 8 §2: the Activities whose screens became destinations of the one host are gone, from the tree and
 * from every manifest. Their `ScreenLauncher.targetFor` arms stay (the Classic*RemovalGuard family text-scans
 * for `"Screen.X ->"`) with `targetForMigratedScreen` bodies.
 */
class SliceEightActivityRemovalGuardTest {

    private val deleted = listOf(
        "net.bible.android.view.activity.backup.BackupComposeActivity",
        "net.bible.android.view.activity.StartupComposeActivity",
        "net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity",
        "net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity",
        "net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity",
        "net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity",
        "net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity",
        "net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity",
        "net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity",
    )

    @Test fun theNineActivitiesAreGone() = ClassicRemovalScan.assertPathsGone(
        deleted.map { "src/main/java/" + it.replace('.', '/') + ".kt" },
        "a slice 8 Activity is back; its screen is a destination of NavHostComposeActivity",
    )

    @Test fun noManifestDeclaresThem() = ClassicRemovalScan.assertNoManifestNames(
        deleted, "a manifest still declares an Activity slice 8 deleted",
    )

    @Test fun noSourceNamesThem() = ClassicRemovalScan.assertNoSourceNames(
        deleted, "a shipping source still names an Activity slice 8 deleted",
    )

    @Test fun theirLauncherArmsAreStillThereAndUnconditional() = ClassicRemovalScan.assertLauncherArmsUnconditional(
        listOf(
            "Screen.Backup", "Screen.Startup", "Screen.InstallZip", "Screen.ChooseDocument", "Screen.GridChoosePassageBook",
            "Screen.ChooseDictionaryWord", "Screen.ChooseGeneralBookKey", "Screen.ChooseMapKey", "Screen.WorkspaceSelector",
            "Screen.TextDisplaySettings",
        ),
        "every Screen keeps its own targetFor arm (targetForMigratedScreen body) -- never merged, never removed",
    )
}
