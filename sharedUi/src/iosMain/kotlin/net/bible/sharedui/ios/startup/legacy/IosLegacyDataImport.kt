package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.legacymigration.control.LegacyMigrationControl
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.settings.LegacyDataImport
import net.bible.sharedui.ios.startup.LEGACY_SETTLED_KEY
import net.bible.sharedui.strings.keyed.StringTable

/**
 * The iOS settings row that lets the user import the previous app's data later (after declining or skipping it at the
 * gate). Confirming records an import request and clears the settled flag, so the next launch enters the legacy gate again.
 */
internal class IosLegacyDataImport(
    private val control: LegacyMigrationControl,
    private val dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val settings: DeviceLocalPrefs,
) : LegacyDataImport {
    override fun available(): Boolean = control.importAvailable()

    override suspend fun confirmAndRequest() {
        val strings = table()
        val answer = dialogs.await(
            AppDialogRequest.Confirm(
                title = null,
                message = strings.text("legacy_import_confirm"),
                confirmText = strings.text("yes"),
                dismissText = strings.text("no"),
            ),
        )
        if (answer != AppDialogResult.Ok) return
        // Request first, flag second: a kill in between leaves the request recorded and the flag set, which is harmless.
        withContext(Dispatchers.Default) {
            control.requestImport()
            settings.remove(LEGACY_SETTLED_KEY)
        }
        dialogs.await(AppDialogRequest.Message(title = null, message = strings.text("legacy_import_reopen"), confirmText = strings.text("okay")))
    }
}
