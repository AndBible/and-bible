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

package net.bible.sharedui.ios.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.bible.service.startup.ExampleLabelNames
import net.bible.service.startup.ReadingAppInitPlatform
import net.bible.sharedui.strings.keyed.StringTable

/** iOS side of [net.bible.service.startup.ReadingAppInit]: no platform services to start; names from the string table. */
@kotlin.native.HiddenFromObjC
class IosReadingAppInitPlatform(private val table: StringTable) : ReadingAppInitPlatform {
    override suspend fun platformStart() = Unit

    override val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default

    override val exampleLabelNames: ExampleLabelNames
        get() = ExampleLabelNames(
            red = table.text("label_red"),
            green = table.text("label_green"),
            blue = table.text("label_blue"),
            underline = table.text("label_underline"),
            salvation = table.text("label_salvation"),
            migratedMyNotes = table.text("migrated_my_notes"),
        )

    override fun workspaceName(n: Int): String = table.fmt("workspace_number", n)

    override fun sweepOrphanSearchIndexes() = Unit
}
