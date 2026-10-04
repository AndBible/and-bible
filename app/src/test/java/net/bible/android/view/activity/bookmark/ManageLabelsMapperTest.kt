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
import net.bible.sharedcore.bookmark.ManageLabelsMode
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
class ManageLabelsMapperTest {

    @Test
    fun `toMode maps all four classic modes`() {
        assertEquals(ManageLabelsMode.STUDYPAD, ManageLabelsMapper.toMode(ManageLabelsContract.Mode.STUDYPAD))
        assertEquals(ManageLabelsMode.WORKSPACE, ManageLabelsMapper.toMode(ManageLabelsContract.Mode.WORKSPACE))
        assertEquals(ManageLabelsMode.ASSIGN, ManageLabelsMapper.toMode(ManageLabelsContract.Mode.ASSIGN))
        assertEquals(ManageLabelsMode.HIDELABELS, ManageLabelsMapper.toMode(ManageLabelsContract.Mode.HIDELABELS))
    }

    @Test
    fun `seeds derive String sets from IdType sets`() {
        val a = IdType()
        val b = IdType()
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = mutableSetOf(a, b),
            autoAssignLabels = mutableSetOf(a),
            autoAssignPrimaryLabel = a,
            bookmarkPrimaryLabel = b,
        )

        assertEquals(setOf(a.toString(), b.toString()), ManageLabelsMapper.seedSelected(data))
        assertEquals(setOf(a.toString()), ManageLabelsMapper.seedAutoAssign(data))
        assertEquals(a.toString(), ManageLabelsMapper.seedAutoAssignPrimary(data))
        assertEquals(b.toString(), ManageLabelsMapper.seedBookmarkPrimary(data))
    }

    @Test
    fun `seeds are empty-null when data has no labels`() {
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.WORKSPACE)

        assertTrue(ManageLabelsMapper.seedSelected(data).isEmpty())
        assertTrue(ManageLabelsMapper.seedAutoAssign(data).isEmpty())
        assertNull(ManageLabelsMapper.seedAutoAssignPrimary(data))
        assertNull(ManageLabelsMapper.seedBookmarkPrimary(data))
    }

    @Test
    fun `applyResult round-trips selected, autoAssign and primaries, preserving mode and isWindow`() {
        val a = IdType()
        val b = IdType()
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.ASSIGN, isWindow = true)

        val result = ManageLabelsMapper.applyResult(
            data = data,
            selected = setOf(a.toString()),
            autoAssign = setOf(b.toString()),
            changed = setOf(a.toString()),
            deleted = setOf(b.toString()),
            deletedWithOrphaned = setOf(b.toString()),
            autoAssignPrimary = b.toString(),
            bookmarkPrimary = a.toString(),
        )

        assertEquals(setOf(a), result.selectedLabels)
        assertEquals(setOf(b), result.autoAssignLabels)
        assertEquals(setOf(a), result.changedLabels)
        assertEquals(setOf(b), result.deletedLabels)
        assertEquals(setOf(b), result.deletedLabelsWithOrphanedBookmarks)
        assertEquals(b, result.autoAssignPrimaryLabel)
        assertEquals(a, result.bookmarkPrimaryLabel)
        // mode/isWindow untouched by applyResult
        assertEquals(ManageLabelsContract.Mode.ASSIGN, result.mode)
        assertTrue(result.isWindow)
        assertFalse(result.reset)
    }

    @Test
    fun `applyResult with null primaries clears them`() {
        val a = IdType()
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.WORKSPACE,
            autoAssignPrimaryLabel = a,
            bookmarkPrimaryLabel = a,
        )

        val result = ManageLabelsMapper.applyResult(
            data = data,
            selected = emptySet(),
            autoAssign = emptySet(),
            changed = emptySet(),
            deleted = emptySet(),
            deletedWithOrphaned = emptySet(),
            autoAssignPrimary = null,
            bookmarkPrimary = null,
        )

        assertNull(result.autoAssignPrimaryLabel)
        assertNull(result.bookmarkPrimaryLabel)
    }

    @Test
    fun `applyReset sets the reset flag and leaves other fields untouched`() {
        val a = IdType()
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.HIDELABELS, selectedLabels = mutableSetOf(a))

        val result = ManageLabelsMapper.applyReset(data)

        assertTrue(result.reset)
        assertEquals(setOf(a), result.selectedLabels)
        assertEquals(ManageLabelsContract.Mode.HIDELABELS, result.mode)
    }

    // ---- F97 (fix batch 3 §2.3.1): HIDELABELS carries no workspace auto-assign state -------------

    @Test
    fun `hideLabelsData carries the hidden set and no auto-assign state`() {
        val hidden = listOf(IdType(), IdType())
        val data = ManageLabelsMapper.hideLabelsData(hidden, isWindow = true)
        assertEquals(ManageLabelsContract.Mode.HIDELABELS, data.mode)
        assertEquals(hidden.toSet(), data.selectedLabels)
        assertTrue(data.isWindow)
        assertTrue("F97: another workspace's auto-assign list must not ride along", data.autoAssignLabels.isEmpty())
        assertNull(data.autoAssignPrimaryLabel)
    }

    @Test
    fun `the label editor opened from HIDELABELS has no workspace context`() {
        assertFalse(ManageLabelsMapper.labelEditHasWorkspaceContext(ManageLabelsContract.Mode.HIDELABELS))
        assertTrue(ManageLabelsMapper.labelEditHasWorkspaceContext(ManageLabelsContract.Mode.WORKSPACE))
        assertTrue(ManageLabelsMapper.labelEditHasWorkspaceContext(ManageLabelsContract.Mode.ASSIGN))
        assertTrue(ManageLabelsMapper.labelEditHasWorkspaceContext(ManageLabelsContract.Mode.STUDYPAD))
    }
}
