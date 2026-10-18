package net.bible.android.view.activity.nav.ports

import androidx.compose.ui.graphics.ImageBitmap
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.settings.BackgroundThumbnailResolver
import net.bible.android.view.activity.settings.TextDisplaySettingsRouteEntry
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform

/**
 * [TextDisplayPlatform] over [TextDisplaySettingsRouteEntry] (shared vs detached service), the label manager's
 * `ManageLabelsContract` and a [BackgroundThumbnailResolver] (one per host, so its cache is per host).
 *
 * [sharedService] is a provider: the Koin singleton is built on first entry, never when the host is constructed.
 */
class AndroidTextDisplayPlatform(
    private val sharedService: () -> TextDisplaySettingsServiceImpl,
) : TextDisplayPlatform {
    private val thumbnails = BackgroundThumbnailResolver()

    override fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry {
        val entry = TextDisplaySettingsRouteEntry(args, sharedService())
        val service = entry.service
        return TextDisplayEntry(
            service = service,
            scope = entry.scope,
            // Classic `HideLabelsPreference.openDialog`'s `ManageLabelsData`, verbatim; since F97 it no longer seeds
            // the active workspace's auto-assign list.
            hideLabelsPayload = { scope: SettingsScope ->
                ManageLabelsMapper.hideLabelsData(
                    service.currentHideLabelsIds(scope),
                    isWindow = scope is SettingsScope.Window,
                ).toJSON()
            },
            resultOnLeave = entry::resultOnLeave,
        )
    }

    /**
     * Classic's `RESULT_OK` branch of the BOOKMARKS_HIDELABELS bridge: a `reset` answer reverts the row, anything
     * else hands the chosen ids to the controller. "A result arrived" IS "the user answered".
     */
    override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) {
        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(resultJson)
        if (resultData.reset) {
            controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
        } else {
            controller.onHideLabelsChange(resultData.selectedLabels.map { it.toString() })
        }
    }

    override fun thumbnail(token: String): ImageBitmap? = thumbnails.resolve(token)
}
