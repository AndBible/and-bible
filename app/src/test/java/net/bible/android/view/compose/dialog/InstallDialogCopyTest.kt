/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.compose.dialog

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.InstallZipContent
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Copy of the install decision dialogs (B3c): every one has a lead naming the action (never "Are you sure?"),
 * a verb confirm button and, for Overwrite, the destructive role.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class InstallDialogCopyTest {
    @get:Rule val compose = createComposeRule()

    private val strings: Strings by lazy {
        AndroidStrings(ApplicationProvider.getApplicationContext<Context>())
    }

    private fun show(state: InstallUiState) {
        compose.setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) { InstallZipContent(state, {}, {}) } }
        }
    }

    private fun roleOf(role: DialogButtonRole) =
        SemanticsMatcher("role is $role") { it.config.getOrNull(DialogButtonRoleKey) == role }

    private fun assertLead(expected: String) {
        compose.onNodeWithTag("dialog-lead", useUnmergedTree = true).onChild().onChild().assertTextEquals(expected)
    }

    @Test fun confirmInstallLeadNamesTheDocumentAndConfirmIsInstall() {
        show(InstallUiState.ConfirmInstall("KJV.zip"))
        assertLead("Install “KJV.zip”?")
        compose.onNodeWithTag("dialog-confirm").assert(roleOf(DialogButtonRole.Confirm))
        compose.onNodeWithText("Install").assertExists()
        compose.onAllNodesWithText("Are you sure?").assertCountEquals(0)
    }

    @Test fun overwriteIsDestructiveWithTheFilesAsDetail() {
        show(InstallUiState.Overwrite(listOf("a.txt", "b.txt")))
        assertLead(strings.overwriteFilesTitle)
        compose.onNodeWithTag("dialog-detail").assertTextEquals("These files already exist:\na.txt\nb.txt")
        compose.onNodeWithTag("dialog-confirm").assert(roleOf(DialogButtonRole.Destructive))
    }

    @Test fun studyPadImportLeadIsAQuestionAndStatsAreTheDetail() {
        show(InstallUiState.StudyPadImport("3 Study Pads"))
        assertLead("Import these Study Pads?")
        compose.onNodeWithTag("dialog-detail").assertTextEquals("3 Study Pads")
        compose.onNodeWithText("Import").assertExists()
    }

    @Test fun backupRestoreNamesTheActionAndConfirmIsRestore() {
        show(InstallUiState.ModuleBackupRestore)
        assertLead("Restore the documents in this backup?")
        compose.onNodeWithTag("dialog-detail").assertTextEquals("This file is an AndBible documents backup.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Restore")
    }

    @Test fun epubUpgradeIsAShortLeadWithTheWarningAsDetail() {
        show(InstallUiState.EpubUpgrade)
        assertLead("Update this document?")
        assertEquals("Update", strings.updateAction)
        assertNotEquals(strings.epubUpgradeTitle, "Update this document?")
        compose.onNodeWithTag("dialog-detail").assert(
            SemanticsMatcher("detail starts with the bookmark warning") {
                it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text }
                    ?.startsWith(strings.epubUpgradeTitle) == true
            },
        )
    }

    @Test fun formatInfoLeadNamesTheAction() {
        show(InstallUiState.FormatInfo("ZIP, SWORD"))
        assertLead("Install documents from a file?")
        compose.onNodeWithTag("dialog-detail").assertTextEquals("ZIP, SWORD")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Choose file")
    }

    @Test fun downloadQuestionNamesTheActionAndQuotesTheDocument() {
        assertEquals("Download “King James Version”?", strings.downloadDocumentQuestion("King James Version"))
        assertEquals("Download", strings.downloadAction)
    }
}
