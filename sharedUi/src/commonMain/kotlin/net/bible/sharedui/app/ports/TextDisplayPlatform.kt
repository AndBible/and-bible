@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import androidx.compose.ui.graphics.ImageBitmap
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsService

/**
 * What one entry into the text-display-settings destination needs from the platform: the service it edits (the
 * shared one, or a detached copy for a selector-originated edit), the scope the route asks for, the Hide-labels
 * payload for that service and the result to deliver on leaving.
 */
@kotlin.native.HiddenFromObjC
class TextDisplayEntry(
    val service: TextDisplaySettingsService,
    val scope: SettingsScope,
    /** Classic `HideLabelsPreference.openDialog`'s `ManageLabelsData`, as JSON, for [scope]. */
    val hideLabelsPayload: (scope: SettingsScope) -> String,
    val resultOnLeave: () -> TextSettingsResult?,
)

/** What the workspace cluster (text display settings) needs from the platform beyond the generic ports. */
@kotlin.native.HiddenFromObjC
interface TextDisplayPlatform {
    /** Resolves the route's [args] into the service and scope this visit edits (Android: `TextDisplaySettingsRouteEntry`). */
    fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry

    /** Applies the label manager's answer to the Hide-labels row: a `reset` answer reverts it, anything else sets the ids. */
    fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController)

    /** A down-sampled thumbnail of a provided background image, cached by [token]; null if unreadable. */
    fun thumbnail(token: String): ImageBitmap?
}
