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

import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.IosPending

/** Pending (stream H). */
@kotlin.native.HiddenFromObjC
class IosPromptFiles : PromptFiles {
    override suspend fun read(ref: DocumentRef): String {
        IosPending.log("PromptFiles.read")
        return ""
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        IosPending.log("PromptFiles.write")
    }

    override suspend fun installAsAddon(ref: DocumentRef) {
        IosPending.log("PromptFiles.installAsAddon")
    }

    override fun suggestedExportName(): String {
        IosPending.log("PromptFiles.suggestedExportName")
        return ""
    }
}
