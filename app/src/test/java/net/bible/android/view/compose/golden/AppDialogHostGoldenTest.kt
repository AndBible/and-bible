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

package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.toAnswers
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.ShownDialog
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.app.ports.externalLinkQuestion
import net.bible.sharedui.strings.AndroidStrings
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `AppDialogHost` per dialog-shaped request (spec §10). Sheet-shaped requests (SingleChoice,
 * MultiChoice, action Options) are deliberately NOT captured: two popup hosts on one page hang the
 * suite, and the sheets themselves already have goldens of their own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AppDialogHostGoldenTest {

    private fun host(request: AppDialogRequest): @androidx.compose.runtime.Composable () -> Unit = {
        AppDialogHost(
            shown = ShownDialog(1, request), permission = null,
            onRespond = { _, _ -> }, onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
        )
    }

    /** Progress renders through the NEW `progress` parameter now, not through `shown` (C1). */
    private fun progressHost(request: AppDialogRequest.Progress): @androidx.compose.runtime.Composable () -> Unit = {
        AppDialogHost(
            shown = null, permission = null,
            onRespond = { _, _ -> }, onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
            progress = ShownDialog(1, request),
        )
    }

    @Test fun messageWithLinkAndReport() = captureMatrix("AppDialogHost", "message") {
        host(
            AppDialogRequest.Message(
                title = null,
                message = "Download failed.<br><br><b>Details</b>: see <a href=\"https://andbible.org\">andbible.org</a>",
                confirmText = "OK", neutralText = "Report error",
            ),
        ).invoke()
    }

    @Test fun confirm() = captureMatrix("AppDialogHost", "confirm") {
        host(AppDialogRequest.Confirm(title = "Are you sure?", message = "Delete 3 bookmarks?", confirmText = "OK", dismissText = "Cancel")).invoke()
    }

    @Test fun textInputWithNeutral() = captureMatrix("AppDialogHost", "textinput") {
        host(
            AppDialogRequest.TextInput(
                title = "Give passphrase for KJV", message = null, initial = "", confirmText = "OK",
                dismissText = "Cancel", neutralText = "Show unlock info", masked = false,
            ),
        ).invoke()
    }

    @Test fun optionsAnswers() = captureMatrix("AppDialogHost", "options") {
        host(
            AppDialogRequest.Options(
                title = "Restore or import?", message = "Choose what to do with this backup.",
                options = listOf(SettingsItem.Choice("restore", "Restore"), SettingsItem.Choice("import", "Import")).toAnswers(),
                dismissText = "Cancel", asActionSheet = false,
            ),
        ).invoke()
    }

    @Test fun updateOfferWithCheckbox() = captureMatrix("AppDialogHost", "update_offer") {
        host(
            AppDialogRequest.Options(
                title = "New version available",
                message = "AndBible production-99999 is available. You have 6.0.1266.<br><br>" +
                    "You can turn update checks back on in Settings → Check for updates.",
                options = listOf(SettingsItem.Choice("download", "Download"), SettingsItem.Choice("later", "Later")).toAnswers(),
                dismissText = null, asActionSheet = false, cancellable = false,
                checkboxLabel = "Don't notify me about updates",
            ),
        ).invoke()
    }

    /** #4155: the external-link question with "Don't ask again for <host>", built by the shared builder. */
    @Test fun externalLinkWithTrust() = captureMatrix("AppDialogHost", "external_link_trust") {
        val strings = AndroidStrings(ApplicationProvider.getApplicationContext())
        host(externalLinkQuestion(strings, "https://www.youtube.com/watch?v=abc", "www.youtube.com")).invoke()
    }

    @Test fun progress() = captureMatrix("AppDialogHost", "progress") {
        progressHost(AppDialogRequest.Progress(title = null, message = "Please wait…")).invoke()
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun confirmRtl() = captureRtl("AppDialogHost", "confirm") {
        host(AppDialogRequest.Confirm(title = "Are you sure?", message = "Delete?", confirmText = "OK", dismissText = "Cancel")).invoke()
    }
}
