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
package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavRoutesSlice2Test {

    // ——— bookmarks(labelNo) ————————————————————————————————————————————————————————————

    @Test
    fun bookmarksOmitsAbsentLabelNo() {
        // BookmarksComposeActivity.kt:73-77 branches on containsKey, not on null: "no labelNo
        // argument at all" must stay distinguishable from "labelNo present but empty", or the
        // destination arm would try to parse an empty string as an Int. An omitted argument is
        // absent from the route entirely, not present-and-empty.
        assertEquals("bookmarks/list", NavRoutes.bookmarks())
        assertEquals("bookmarks/list", NavRoutes.bookmarks(labelNo = null))
    }

    @Test
    fun bookmarksCarriesLabelNoWhenGiven() {
        assertEquals("bookmarks/list?labelNo=3", NavRoutes.bookmarks(labelNo = 3))
    }

    @Test
    fun bookmarksDoesNotClampANegativeLabelNo() {
        // The clamp (negative -> 0) is host BEHAVIOUR, done in BookmarksComposeActivity's
        // `initialFilterIndex`, not route DATA — a later task's destination arm applies it. The
        // route itself must carry whatever value it was given, unmodified.
        assertEquals("bookmarks/list?labelNo=-1", NavRoutes.bookmarks(labelNo = -1))
    }

    @Test
    fun bookmarksPatternRegistersLabelNo() {
        assertTrue(NavRoutes.BOOKMARKS_PATTERN.contains("${NavRoutes.ARG_LABEL_NO}={${NavRoutes.ARG_LABEL_NO}}"))
    }

    // ——— manageLabels(data) —————————————————————————————————————————————————————————————

    @Test
    fun manageLabelsCarriesTheDataArgument() {
        val route = NavRoutes.manageLabels(data = "{\"mode\":\"ASSIGN\"}")
        assertTrue(route.startsWith("bookmarks/manageLabels?"))
        assertTrue(route.contains("${NavRoutes.ARG_MANAGE_LABELS_DATA}="))
    }

    @Test
    fun manageLabelsEncodesASpaceAndNonAsciiCharacter() {
        // The JSON payload is free text (label names round-trip through it), so a space and a
        // non-ASCII character must survive encodeArg/decodeArg, same discipline as the reading-plan
        // and search routes.
        val json = "{\"name\":\"Työ label\"}"
        val route = NavRoutes.manageLabels(data = json)
        assertTrue(!route.contains(" "), "route must not contain a raw space: $route")
        val encoded = route.substringAfter("${NavRoutes.ARG_MANAGE_LABELS_DATA}=").substringBefore("&")
        assertEquals(json, NavRoutes.decodeArg(encoded))
    }

    @Test
    fun manageLabelsPatternRegistersItsArgument() {
        assertTrue(
            NavRoutes.MANAGE_LABELS_PATTERN.contains(
                "${NavRoutes.ARG_MANAGE_LABELS_DATA}={${NavRoutes.ARG_MANAGE_LABELS_DATA}}",
            ),
        )
    }

    // ——— labelEdit(data) —————————————————————————————————————————————————————————————————

    @Test
    fun labelEditCarriesTheDataArgument() {
        val route = NavRoutes.labelEdit(data = "{\"isAssigning\":true}")
        assertTrue(route.startsWith("bookmarks/labelEdit?"))
        assertTrue(route.contains("${NavRoutes.ARG_LABEL_DATA}="))
    }

    @Test
    fun labelEditEncodesASpaceAndNonAsciiCharacter() {
        val json = "{\"suggestedName\":\"Rukoushuone ä\"}"
        val route = NavRoutes.labelEdit(data = json)
        assertTrue(!route.contains(" "), "route must not contain a raw space: $route")
        val encoded = route.substringAfter("${NavRoutes.ARG_LABEL_DATA}=").substringBefore("&")
        assertEquals(json, NavRoutes.decodeArg(encoded))
    }

    @Test
    fun labelEditPatternRegistersItsArgument() {
        assertTrue(
            NavRoutes.LABEL_EDIT_PATTERN.contains("${NavRoutes.ARG_LABEL_DATA}={${NavRoutes.ARG_LABEL_DATA}}"),
        )
    }

    // ——— result payloads ————————————————————————————————————————————————————————————————

    @Test
    fun bookmarkResultCarriesABibleBookmarkShape() {
        // BookmarksComposeActivity.kt:172-174: a BibleBookmarkWithNotes sets only "verse".
        val result = BookmarkResult(
            verse = "Gen.1.1",
            description = "Bookmarks",
            labelNo = 0,
            listPosition = 2,
        )
        assertEquals("Gen.1.1", result.verse)
        assertEquals(null, result.key)
        assertEquals(null, result.book)
        assertEquals(null, result.ordinal)
    }

    @Test
    fun bookmarkResultCarriesAGenericBookmarkShape() {
        // BookmarksComposeActivity.kt:175-178: a GenericBookmarkWithNotes sets "key"+"book"+"ordinal"
        // instead of "verse".
        val result = BookmarkResult(
            key = "MyNote",
            book = "StudyPad",
            ordinal = 5,
            description = "Bookmarks",
            labelNo = 1,
            listPosition = 0,
        )
        assertEquals(null, result.verse)
        assertEquals("MyNote", result.key)
        assertEquals("StudyPad", result.book)
        assertEquals(5, result.ordinal)
    }

    @Test
    fun manageLabelsResultCarriesOnlyTheJsonString() {
        // Ruling: `reset` is a FIELD OF ManageLabelsData (ManageLabelsContract.kt:60), set by
        // ManageLabelsMapper.applyReset(data) before both exits (:667 and :675-676) build the exact
        // same `Intent().putExtra("data", data.toJSON())` — there is no second "reset" extra to
        // carry, and a duplicate field here could disagree with the one inside the JSON.
        val json = "{\"mode\":\"HIDELABELS\",\"reset\":true}"
        val result = ManageLabelsResult(data = json)
        assertEquals(json, result.data)
    }

    @Test
    fun labelEditResultCancelledIsAVariantNotAFlag() {
        // Plan D1: LabelEditComposeActivity's RESULT_CANCELED path (:onFinish's LabelEditResult.Cancel
        // arm) sets no "data" extra at all, so Cancelled must be a variant with no payload to carry,
        // not a boolean alongside a nullable data field.
        val cancelled: LabelEditResult = LabelEditResult.Cancelled
        assertTrue(cancelled is LabelEditResult.Cancelled)
    }

    @Test
    fun labelEditResultSavedCarriesTheJsonString() {
        val json = "{\"delete\":false}"
        val saved: LabelEditResult = LabelEditResult.Saved(data = json)
        assertEquals(json, (saved as LabelEditResult.Saved).data)
    }
}
