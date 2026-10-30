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

package net.bible.sharedui.host.ports

import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedui.app.ai.AI_CUSTOM_LANGUAGE_TAG
import net.bible.sharedui.app.ai.AiHostPlatform
import net.bible.sharedui.strings.keyed.StringTable

/** Locale choices are rebuilt against the current string table, including an already-open chooser. */
@kotlin.native.HiddenFromObjC
class HostAiHostPlatform(
    private val strings: CoreStrings,
    private val locales: LocaleSupport,
    private val table: () -> StringTable,
    private val formatTimestamp: (Long) -> String,
    private val reportError: suspend (String, Throwable) -> Unit,
) : AiHostPlatform {
    override fun languageChoices(): List<SettingsItem.Choice> = buildList {
        val t = table()
        add(SettingsItem.Choice("", t.fmt("ai_language_app_default", locales.defaultDisplayLanguage())))
        val descriptions = strings.prefsInterfaceLocaleDescriptions
        strings.prefsInterfaceLocaleValues.forEachIndexed { i, code ->
            if (code.isNotEmpty()) add(SettingsItem.Choice(code, descriptions[i]))
        }
        add(SettingsItem.Choice(AI_CUSTOM_LANGUAGE_TAG, t.text("ai_language_custom")))
    }

    override fun formatLogTimestamp(epochMs: Long): String = formatTimestamp(epochMs)

    override suspend fun showErrorDialog(message: String, cause: Throwable) = reportError(message, cause)
}
