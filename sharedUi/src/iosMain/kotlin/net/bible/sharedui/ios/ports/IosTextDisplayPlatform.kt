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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import androidx.compose.ui.graphics.ImageBitmap
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.ios.IosPending

/**
 * Pending on iOS (stream H). [entryFor] is reported and answers an inert entry (global scope, an empty service that
 * edits nothing), so entering the route never throws.
 */
@kotlin.native.HiddenFromObjC
class IosTextDisplayPlatform : TextDisplayPlatform {
    override fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry {
        IosPending.log("TextDisplayPlatform.entryFor")
        return TextDisplayEntry(
            service = IosInertTextDisplaySettingsService(),
            scope = SettingsScope.Global,
            hideLabelsPayload = { "{}" },
            resultOnLeave = { null },
        )
    }

    override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) =
        IosPending.log("TextDisplayPlatform.applyHideLabelsResult")

    override fun thumbnail(token: String): ImageBitmap? {
        IosPending.log("TextDisplayPlatform.thumbnail")
        return null
    }
}
