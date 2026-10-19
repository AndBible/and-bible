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

package net.bible.sharedui.strings.keyed

import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.settings.TextDisplayString

/**
 * Bundle-backed [TextDisplayPrefItems] for iOS.
 * Sparse overrides follow Android's Preference: assigning the inherited value clears the override, and reverting
 * clears the current level (or restores defaults at global level). The service owns persistence and propagation.
 * Strings and arrays use the same resource keys as Android; background names come from the installed module catalog.
 */
class BundleTextDisplayPrefItems(
    private val strings: () -> StringTable,
    private val contentSource: () -> ContentSource,
    private val backgroundImages: () -> Map<String, String> = { emptyMap() },
) : TextDisplayPrefItems {
    /** Types passed to [displaySettingChanged], oldest first. */
    val changedTypes: List<TextDisplaySettings.Types> get() = changed
    private val changed = mutableListOf<TextDisplaySettings.Types>()

    private inner class Handle(private val bundle: SettingsBundle, private val type: TextDisplaySettings.Types) : PrefItemHandle {
        private val target: TextDisplaySettings get() = when (bundle.level) {
            net.bible.android.database.SettingsLevel.WINDOW -> requireNotNull(bundle.pageManagerSettings)
            net.bible.android.database.SettingsLevel.WORKSPACE -> bundle.workspaceSettings
            net.bible.android.database.SettingsLevel.GLOBAL -> bundle.globalSettings
        }
        override var value: Any
            get() = bundle.actualSettings.getValue(type) ?: defaultFor(type)
            set(v) {
                displaySettingChanged(type)
                val parent = when (bundle.level) {
                    net.bible.android.database.SettingsLevel.WINDOW -> bundle.workspaceSettings.getValue(type) ?: bundle.globalSettings.getValue(type) ?: defaultFor(type)
                    net.bible.android.database.SettingsLevel.WORKSPACE -> bundle.globalSettings.getValue(type) ?: defaultFor(type)
                    net.bible.android.database.SettingsLevel.GLOBAL -> null
                }
                if (parent == v) target.setNonSpecific(type) else target.setValue(type, v)
            }
        override val enabled: Boolean get() = true
        override val visible: Boolean get() = true
        override val iconKey: String? get() = null
        override fun setNonSpecific() {
            if (bundle.level == net.bible.android.database.SettingsLevel.GLOBAL) target.setValue(type, defaultFor(type))
            else target.setNonSpecific(type)
        }
    }

    override fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle = Handle(bundle, type)

    override fun string(key: TextDisplayString, vararg args: Any): String =
        strings().format(textDisplayStringKey(key), args)

    override fun array(key: TextDisplayArray): List<String> = strings().array(textDisplayArrayName(key))

    /** The CSS generic families every WebView resolves; Android's installed-font scan has no iOS counterpart yet. */
    override val fonts: List<SettingsItem.Choice> = listOf(
        SettingsItem.Choice("sans-serif", "Sans-serif"),
        SettingsItem.Choice("serif", "Serif"),
        SettingsItem.Choice("monospace", "Monospace"),
    )

    override fun displaySettingChanged(type: TextDisplaySettings.Types) { changed += type }

    override fun contentSource(): ContentSource = contentSource.invoke()

    override fun backgroundImageNames(): Map<String, String> = backgroundImages()

    companion object {
        /** The non-null default of [type]: every [TextDisplaySettings.default] member is set. */
        fun defaultFor(type: TextDisplaySettings.Types): Any =
            checkNotNull(TextDisplaySettings.default.getValue(type)) { "No default for $type" }
    }
}
