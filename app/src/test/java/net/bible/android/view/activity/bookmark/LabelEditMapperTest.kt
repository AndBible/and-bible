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

package net.bible.android.view.activity.bookmark

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.OverrideMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LabelEditMapperTest {

    private fun buildData(
        name: String = "My Label",
        suggestedName: String? = null,
        overrideMode: Int? = 2,
        hasWorkspaceContext: Boolean = true,
    ): LabelEditActivity.LabelData {
        val label = BookmarkEntities.Label(
            id = IdType(),
            name = name,
            color = -0xff0100,
            markerStyle = true,
            markerStyleWholeVerse = false,
            underlineStyle = false,
            underlineStyleWholeVerse = false,
            hideStyle = false,
            hideStyleWholeVerse = false,
            favourite = true,
            customIcon = "star",
        )
        val override = WorkspaceEntities.WorkspaceLabelOverride(
            workspaceId = IdType(),
            labelId = label.id,
            overrideMode = overrideMode,
        )
        return LabelEditActivity.LabelData(
            isAssigning = true,
            label = label,
            isAutoAssign = false,
            isAutoAssignPrimary = false,
            isThisBookmarkSelected = false,
            isThisBookmarkPrimary = false,
            suggestedName = suggestedName,
            workspaceOverride = override,
            hasWorkspaceContext = hasWorkspaceContext,
        )
    }

    @Test
    fun `toState maps overrideMode 2 to MARKER`() {
        val data = buildData(overrideMode = 2)
        val state = LabelEditMapper.toState(data)
        assertEquals(OverrideMode.MARKER, state.overrideMode)
    }

    @Test
    fun `toState maps null overrideMode to NONE`() {
        val data = buildData(overrideMode = null)
        val state = LabelEditMapper.toState(data)
        assertEquals(OverrideMode.NONE, state.overrideMode)
    }

    @Test
    fun `toState uses suggestedName when stored name is empty`() {
        val data = buildData(name = "", suggestedName = "Suggested")
        val state = LabelEditMapper.toState(data)
        assertEquals("Suggested", state.name)
    }

    @Test
    fun `toState reads isSpecialLabel and isSpeakLabel from the label`() {
        val data = buildData()
        val state = LabelEditMapper.toState(data)
        assertEquals(data.label.isSpecialLabel, state.isSpecialLabel)
        assertEquals(data.label.isSpeakLabel, state.isSpeakLabel)
    }

    @Test
    fun `applyToData round-trips name and overrideMode`() {
        val data = buildData()
        val state = LabelEditMapper.toState(data)
        val updated = state.copy(name = "X", overrideMode = OverrideMode.HIDDEN)

        val result = LabelEditMapper.applyToData(data, updated)

        assertEquals("X", result.label.name)
        assertEquals(3, result.workspaceOverride?.overrideMode)
    }

    @Test
    fun `applyToData clears overrideMode when set back to NONE`() {
        val data = buildData(overrideMode = 2)
        val state = LabelEditMapper.toState(data)
        val updated = state.copy(overrideMode = OverrideMode.NONE)

        val result = LabelEditMapper.applyToData(data, updated)

        assertNull(result.workspaceOverride?.overrideMode)
    }

    @Test
    fun `each style expands to exactly one column per axis`() {
        for (style in BookmarkDisplayStyle.entries) {
            val data = buildData()   // the file's fixture builder at `:37`
            val state = LabelEditMapper.toState(data).copy(selectionStyle = style, wholeVerseStyle = style)
            val out = LabelEditMapper.applyToData(data, state).label
            val set = listOf(out.hideStyle, out.markerStyle, out.underlineStyle).count { it }
            val setWv = listOf(out.hideStyleWholeVerse, out.markerStyleWholeVerse, out.underlineStyleWholeVerse).count { it }
            val expected = if (style == BookmarkDisplayStyle.HIGHLIGHT) 0 else 1
            assertEquals("selection axis for $style", expected, set)
            assertEquals("whole-verse axis for $style", expected, setWv)
        }
    }

    @Test
    fun `a legacy row with a dominated flag reads as hidden and normalises on write`() {
        val data = buildData().also {
            it.label.hideStyle = true
            it.label.underlineStyle = true      // dominated: classic greyed it out but kept it
            it.label.markerStyle = true         // dominated too
        }
        val state = LabelEditMapper.toState(data)
        assertEquals(BookmarkDisplayStyle.HIDDEN, state.selectionStyle)

        val out = LabelEditMapper.applyToData(data, state).label
        assertTrue(out.hideStyle)
        assertFalse(out.underlineStyle)
        assertFalse(out.markerStyle)
    }
}
