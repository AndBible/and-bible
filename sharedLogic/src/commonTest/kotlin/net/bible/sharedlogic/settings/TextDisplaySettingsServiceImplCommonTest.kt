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
package net.bible.sharedlogic.settings

import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.settings.DetachedWorkspaceEdit
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the common [TextDisplaySettingsServiceImpl] through a fake [TextDisplayPrefItems] (detached workspace edit). */
class TextDisplaySettingsServiceImplCommonTest {
    private class FakeHandle(initial: Any?) : PrefItemHandle {
        var nonSpecificCalls = 0
        val writes = mutableListOf<Any?>()
        override var value: Any? = initial
            set(v) { field = v; writes += v }
        override val enabled = true
        override val visible = true
        override val iconKey: String? = null
        override fun setNonSpecific() { nonSpecificCalls++ }
    }

    private class FakePrefItems : TextDisplayPrefItems {
        val requested = mutableListOf<TextDisplaySettings.Types>()
        val handles = mutableMapOf<TextDisplaySettings.Types, FakeHandle>()

        override fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle {
            requested += type
            return handles.getOrPut(type) {
                FakeHandle(
                    when (type) {
                        TextDisplaySettings.Types.FONTSIZE -> 18
                        TextDisplaySettings.Types.FONTFAMILY -> "serif"
                        TextDisplaySettings.Types.MARGINSIZE -> WorkspaceEntities.MarginSize(5, 5, 100)
                        TextDisplaySettings.Types.BOOKMARKS_HIDELABELS -> emptyList<IdType>()
                        TextDisplaySettings.Types.STRONGS, TextDisplaySettings.Types.PAGE_SCROLL_AMOUNT,
                        TextDisplaySettings.Types.SCROLL_HELPER_LINE_STYLE, TextDisplaySettings.Types.TOPMARGIN,
                        TextDisplaySettings.Types.LINE_SPACING -> 20
                        else -> true
                    }
                )
            }
        }

        override fun string(key: TextDisplayString, vararg args: Any) = "$key:${args.joinToString(",")}"
        override fun array(key: TextDisplayArray) = listOf("a", "b", "c")
        override val fonts = listOf(SettingsItem.Choice("serif", "Serif"))
        override fun displaySettingChanged(type: TextDisplaySettings.Types) {}
        override fun contentSource(): ContentSource = error("unused")
        override fun backgroundImageNames(): Map<String, String> = emptyMap()
    }

    private val fake = FakePrefItems()
    private val bundle = SettingsBundle(
        level = SettingsLevel.WORKSPACE, workspaceId = IdType(), workspaceName = "Other",
        workspaceSettings = TextDisplaySettings(), globalSettings = TextDisplaySettings(),
    )
    private val service get() = TextDisplaySettingsServiceImpl(DetachedWorkspaceEdit(bundle))
    private val scope get() = SettingsScope.Workspace(bundle.workspaceId.toString())

    @BeforeTest fun setUp() {
        stopKoin()
        startKoin { modules(module { single<TextDisplayPrefItems> { fake } }) }
    }
    @AfterTest fun tearDown() { stopKoin() }

    @Test fun fontSizeRowTextComesFromThePort() {
        val row = service.loadText(scope).rows.getValue(TextSettingType.FONTSIZE).value as TextSettingRowValue.Numeric
        assertEquals(fake.string(TextDisplayString.FONT_SIZE_PT, 18), row.displayText)
        assertEquals(18, row.value)
        assertTrue(TextDisplaySettings.Types.FONTSIZE in fake.requested)
    }

    @Test fun writingAValueGoesThroughTheHandleSetter() {
        service.setValue(scope, TextSettingType.FONTSIZE, TextSettingValue.IntValue(22))
        assertEquals(listOf<Any?>(22), fake.handles.getValue(TextDisplaySettings.Types.FONTSIZE).writes)
    }

    @Test fun revertingARowCallsSetNonSpecific() {
        service.revert(scope, TextSettingType.FONTSIZE)
        assertEquals(1, fake.handles.getValue(TextDisplaySettings.Types.FONTSIZE).nonSpecificCalls)
        assertTrue(fake.handles.getValue(TextDisplaySettings.Types.FONTSIZE).writes.isEmpty())
    }
}
