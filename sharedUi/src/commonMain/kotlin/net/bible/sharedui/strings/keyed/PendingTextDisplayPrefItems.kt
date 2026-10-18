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
 * C7 pending [TextDisplayPrefItems] for iOS: the text display settings screen works against values held in memory
 * instead of the (Android `OptionsMenuItems`) preference items. Phase 2: route through the shared root.
 *
 * A handle starts at [TextDisplaySettings.default]'s concrete value for its type and keeps what is written to it; the
 * values are per setting type, not per bundle (one scope-less store), and are not persisted. [setNonSpecific] returns
 * the handle to the default. Strings and arrays come from [strings] by the same keys as `AndroidTextDisplayPrefItems`
 * ([textDisplayStringKey], [textDisplayArrayName]); a handle has no title (the row titles are `KeyedSettingLabels`'
 * business). The picker's content source and the provided background images are passed in, as the iOS ones live below
 * this module.
 *
 * Every write and each call that has no real implementation yet logs with tag `C7-pending`.
 */
class PendingTextDisplayPrefItems(
    private val strings: () -> StringTable,
    private val contentSource: () -> ContentSource,
    private val backgroundImages: () -> Map<String, String> = { emptyMap() },
) : TextDisplayPrefItems {
    private val store = mutableMapOf<TextDisplaySettings.Types, Any>()

    /** Types passed to [displaySettingChanged], oldest first. */
    val changedTypes: List<TextDisplaySettings.Types> get() = changed
    private val changed = mutableListOf<TextDisplaySettings.Types>()

    private inner class Handle(private val type: TextDisplaySettings.Types) : PrefItemHandle {
        override var value: Any
            get() = store[type] ?: defaultFor(type)
            set(v) {
                Log.w(TAG, "PendingTextDisplayPrefItems: $type kept in memory only (phase 2: route through the shared root)")
                store[type] = v
            }
        override val enabled: Boolean get() = true
        override val visible: Boolean get() = true
        override val iconKey: String? get() = null
        override fun setNonSpecific() { store.remove(type) }
    }

    override fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle = Handle(type)

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
        private const val TAG = "C7-pending"

        /** The non-null default of [type]: every [TextDisplaySettings.default] member is set. */
        fun defaultFor(type: TextDisplaySettings.Types): Any =
            checkNotNull(TextDisplaySettings.default.getValue(type)) { "No default for $type" }
    }
}
