package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.legacymigration.IosLegacyPlatform
import net.bible.service.legacymigration.extras.LegacyExtrasApplier
import net.bible.service.legacymigration.extras.LegacyNotice
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.keyed.StringTable

/**
 * Runs the after-Ready part of the legacy migration (H9, H11, H16): selects the Nextcloud sync adapter once and shows
 * the one-time notices. It never throws into the Ready composition.
 */
internal object IosLegacyNotices {
    /** Production entry: the applier reads the same `LegacyMigration` directory as [IosLegacyMigrationRunner]. */
    suspend fun showPending(dialogs: AppDialogController, settings: AppSettings, table: StringTable) {
        val applier = try {
            withContext(Dispatchers.Default) {
                val platform = IosLegacyPlatform(IosKeychainSecretStore())
                LegacyExtrasApplier(platform.fileSystem, legacyMigrationControl(platform).dir, IosDeviceSettings())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nsLog("legacy notices: cannot open the migration directory: ${e.stackTraceToString()}")
            return
        }
        showPending(dialogs, settings, table, applier)
    }

    /**
     * Applies the extras off Main, then shows one [AppDialogRequest.Message] per pending notice in enum order. A notice
     * is marked shown only after its dialog was answered with OK, so a kill (or a dropped dialog) while it is open
     * shows it again. A failure to apply or to read the queue is logged and nothing is marked shown.
     */
    internal suspend fun showPending(dialogs: AppDialogController, settings: AppSettings, table: StringTable, applier: LegacyExtrasApplier) {
        val pending = try {
            withContext(Dispatchers.Default) {
                applier.applyAfterReady(settings)
                applier.pendingNotices()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nsLog("legacy notices: applying the migrated extras failed: ${e.stackTraceToString()}")
            return
        }
        for (notice in pending) {
            val text = when (notice) {
                LegacyNotice.ICLOUD_SYNC_MOVED -> table.text("legacy_notice_icloud")
                LegacyNotice.NEXTCLOUD_SYNC_OFF -> table.text("legacy_notice_nextcloud")
                LegacyNotice.NEXTCLOUD_UNSENT -> table.text("legacy_notice_nextcloud_unsent")
                LegacyNotice.BOOKMARKS_SKIPPED -> table.fmt("legacy_notice_bookmarks_skipped", applier.skippedBookmarks)
            }
            val result = dialogs.await(AppDialogRequest.Message(title = null, message = text, confirmText = table.text("okay")))
            if (result != AppDialogResult.Ok) return // dropped (cancelAll): not answered, stays pending
            try {
                withContext(Dispatchers.Default) { applier.markShown(notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nsLog("legacy notices: recording a shown notice failed: ${e.stackTraceToString()}")
                return
            }
        }
    }
}
