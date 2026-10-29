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

package net.bible.sharedui.host.app

import androidx.compose.runtime.Composable
import net.bible.sharedui.app.ports.ExternalLinks

/**
 * What [HostAppDialogOverlay] needs from the host: the external-link opener for links in queued dialogs, and whether
 * the host's view can show app dialogs right now. iOS: `IosAppDialogHost` (UIKit presentation state of the Compose
 * view controller). A host whose window is never covered by native UI (desktop) answers a constant `true`.
 */
@kotlin.native.HiddenFromObjC
interface AppDialogHost {
    /** A new [ExternalLinks] for one overlay; the overlay remembers it for its lifetime. */
    fun newExternalLinks(): ExternalLinks

    /**
     * The "may render now" predicate for the current host view, remembered per view: the overlay polls it every
     * 100 ms (native presentation state is not snapshot state) and restarts the poll when a new predicate is
     * returned. Main thread only.
     */
    @Composable
    fun rememberCanRenderDialogs(): () -> Boolean
}
