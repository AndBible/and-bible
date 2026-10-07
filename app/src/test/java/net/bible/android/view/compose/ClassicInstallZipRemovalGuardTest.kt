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
 * Slice S16 split `InstallZip.kt` and then deleted the classic Activity: the class, its layout,
 * its routing arm and its `src/main` manifest block. See [ClassicRemovalScan] for why a deletion
 * needs a test at all (dead code compiles cleanly, so the compiler proves nothing about it).
 *
 * The split kept TWO of the file's twelve top-level declarations. `InstallZipEvent` moved to its
 * own file (since replaced by `InstallZipProgress.messages`, ABEventBus phase 3). `const val TAG` moved into
 * `InstallZipComposeActivity` as a private constant, because the Compose host reads it with no
 * import at all (same package), so no import sweep could have seen that dependency. The other ten
 * — `ZipHandler`, `EpubFile`, `InvalidModule`, `ModulesExists`, `CantOverwrite`, `InstallZipError`
 * and its four subtypes — died with the class: every reference to them from outside the file was a
 * KDoc `[…]` link or a `//` comment, i.e. a Dokka warning, not a compile error, so extracting them
 * would only have preserved dead code.
 */
class ClassicInstallZipRemovalGuardTest {

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/installzip/InstallZip.kt",
        "src/main/res/layout/activity_install_zip.xml",
    )

    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.installzip.InstallZip",
    )

    /**
     * The file this slice deliberately CREATES rather than deletes. `InstallZipProgress` is reported by
     * `DocumentInstallService` and `EpubOptimization` and consumed by `StartupActivity` and
     * `StartupComposeActivity`, none of which is classic-InstallZip code — so it had to outlive the
     * Activity it happened to be declared beside. Asserted by PATH, not merely by compilation:
     * moving it to a different package would still compile (with four import lines rewritten) yet
     * would silently break the "S16 and S18 impose no ordering on each other" property this split
     * was chosen to preserve.
     */
    private val survivingCollaborators = listOf(
        "src/main/java/net/bible/service/installzip/InstallZipProgress.kt",
    )

    @Test fun theClassicInstallZipFilesAreGone() =
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "a classic install-zip source or layout is back; slice S16 deleted it, so " +
                "InstallZipComposeActivity is the only implementation",
        )

    @Test fun theSurvivingInstallZipCollaboratorsStillExist() =
        ClassicRemovalScan.assertPathsPresent(
            survivingCollaborators,
            "the install-progress owner is missing: InstallZipProgress must " +
                "stay at net.bible.service.installzip.InstallZipProgress, the exact " +
                "fully-qualified name its consumers import",
        )

    @Test fun noSourceFileNamesAClassicInstallZipClass() =
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "a shipping source file or resource XML still names the classic InstallZip class; the " +
                "fix is to remove the reference (for a layout, the offending TAG), not to restore " +
                "the class. The trailing boundary in ClassicRemovalScan.refsFor deliberately " +
                "spares the surviving InstallZipComposeActivity and InstallZipProgress",
        )

    @Test fun noManifestEntryNamesAClassicInstallZipClass() =
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the classic InstallZip class -- check android:name AND " +
                "android:parentActivityName; the latter compiles, tests and renders fine while " +
                "pointing Up at a class that does not exist",
        )

    @Test fun theInstallZipArmIsUnconditional() =
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.InstallZip"),
            "the InstallZip arm still branches; slice S16 deleted the classic " +
                "class it would branch to",
        )

    /**
     * Slice S16 moved the app's only EXTERNAL module-file entry points (a file manager's "open
     * with", a Share sheet, any ACTION_VIEW on a .zip/.epub/.ttf) from classic InstallZip onto the
     * Compose host, because deleting them with the classic Activity would have removed the feature.
     * Nothing else in the gate can see that: an <intent-filter> compiles nowhere, is exercised by no
     * unit test, renders in no golden, and assembles happily whether it is present or absent.
     */
    @Test fun theComposeInstallZipHostCarriesTheExternalEntryPoints() {
        val standard = java.io.File("src/standard/AndroidManifest.xml").readText()
        val hostBlock = standard
            .substringAfter("net.bible.android.view.activity.installzip.InstallZipComposeActivity", "")
            .substringBefore("</activity>", "")
        assertTrue(
            "src/standard/AndroidManifest.xml no longer declares InstallZipComposeActivity; the " +
                "external module-file entry points live on it since slice S16",
            hostBlock.isNotEmpty(),
        )
        listOf(
            "android.intent.action.VIEW",
            "android.intent.action.SEND",
            "android.intent.action.SEND_MULTIPLE",
            "application/zip",
            "application/epub+zip",
            "font/ttf",
        ).forEach {
            assertTrue(
                "InstallZipComposeActivity's block in src/standard/AndroidManifest.xml no longer " +
                    "declares $it -- opening a module file from a file manager or the Share sheet " +
                    "would silently stop working, and no other gate in this phase can see that",
                hostBlock.contains(it),
            )
        }
        val discreteBlock = java.io.File("src/discrete/AndroidManifest.xml").readText()
            .substringAfter("net.bible.android.view.activity.installzip.InstallZipComposeActivity", "")
            .substringBefore("/>", "")
        assertTrue(
            "src/discrete/AndroidManifest.xml no longer overrides InstallZipComposeActivity's " +
                "label; the discrete build would advertise the non-disguised name in the system " +
                "share sheet",
            discreteBlock.contains("install_zip_module_discrete"),
        )
    }
}
