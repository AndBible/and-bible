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
import org.junit.Assert.assertNull
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
        selectionStyle: BookmarkDisplayStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle: BookmarkDisplayStyle? = BookmarkDisplayStyle.UNDERLINE,
    ): LabelEditContract.LabelData {
        val label = BookmarkEntities.Label(
            id = IdType(),
            name = name,
            color = -0xff0100,
            displayStyle = selectionStyle,
            displayStyleWholeVerse = wholeVerseStyle,
            favourite = true,
            customIcon = "star",
        )
        val override = WorkspaceEntities.WorkspaceLabelOverride(
            workspaceId = IdType(),
            labelId = label.id,
            overrideMode = overrideMode,
        )
        return LabelEditContract.LabelData(
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
    fun `each axis round-trips through state and back into the label`() {
        for (selection in BookmarkDisplayStyle.entries) {
            for (wholeVerse in BookmarkDisplayStyle.entries) {
                val data = buildData(selectionStyle = selection, wholeVerseStyle = wholeVerse)
                val state = LabelEditMapper.toState(data)
                assertEquals(selection, state.selectionStyle)
                assertEquals(wholeVerse, state.wholeVerseStyle)
                val written = LabelEditMapper.applyToData(data, state)
                assertEquals(selection, written.label.displayStyle)
                assertEquals(wholeVerse, written.label.displayStyleWholeVerse)
            }
        }
    }

    @Test
    fun `a fresh label defaults to highlight on selection and inherit on whole verses`() {
        // Round 9b deliberately chose an EXPLICIT UNDERLINE here, not a leftover: a full-verse
        // background highlight is too heavy (MIGRATION_52_53_underline_default, 7e020fdd7), and
        // defaulting to inherit would silently turn every new label's whole-verse bookmarks into
        // highlights.
        //
        // Round 10a (2026-08-20) superseded that: a new label now inherits instead, so it carries
        // ONE style unless the user deliberately asks for two -- accepting, on purpose, that a new
        // label's whole-verse bookmarks now highlight (the very outcome round 9b avoided). Both
        // decisions are real; this comment keeps the lineage so a future reader does not "fix" this
        // back to UNDERLINE without knowing it was already tried and deliberately reversed.
        //
        // The SQL default is asserted by BookmarkDatabaseMigration12To13Test and is deliberately
        // UNCHANGED (still 1/UNDERLINE) -- changing it would bump the schema version and the iOS
        // contract. This test pins only the Kotlin default.
        val label = BookmarkEntities.Label()
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, label.displayStyle)
        assertNull(label.displayStyleWholeVerse)
    }

    @Test
    fun `an inherited whole-verse style reads back as null, not resolved`() {
        val data = buildData(selectionStyle = BookmarkDisplayStyle.MARKER, wholeVerseStyle = null)
        assertNull(LabelEditMapper.toState(data).wholeVerseStyle)
    }

    @Test
    fun `choosing inherit writes NULL back to the label`() {
        val data = buildData(selectionStyle = BookmarkDisplayStyle.MARKER, wholeVerseStyle = BookmarkDisplayStyle.HIDDEN)
        val state = LabelEditMapper.toState(data).copy(wholeVerseStyle = null)
        assertNull(LabelEditMapper.applyToData(data, state).label.displayStyleWholeVerse)
    }

    /** The generated default reaches the editor as a real name, not the grey hint: `toState` uses
     *  `suggestedName` whenever the label's own name is empty. */
    @Test
    fun `suggestedName seeds a new label's name`() {
        val data = buildData(name = "", suggestedName = "Label 3")
        assertEquals("Label 3", LabelEditMapper.toState(data).name)
    }

    /** An existing label's own name always wins — `suggestedName` is a NEW-label seed only. */
    @Test
    fun `suggestedName does not override an existing name`() {
        val data = buildData(name = "Study", suggestedName = "Label 3")
        assertEquals("Study", LabelEditMapper.toState(data).name)
    }
}
