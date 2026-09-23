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

package net.bible.android.view.activity.nav

import android.app.Activity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.android.TEST_SDK
import net.bible.android.database.IdType
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.android.view.activity.page.KeyChooserResults
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.nav.WorkspaceResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix round 1, Finding 3: split out of `net.bible.android.view.nav.NavResultChannelGuardTest` so
 * that class's structural ratchet stays dependency-free -- these two tests are the only reason it
 * needed Robolectric, since they exercise `android.content.Intent`, and that class's own text-walk
 * tests touch no Android type.
 *
 * `@RunWith(RobolectricTestRunner::class)`: `android.content.Intent`'s `putExtra`/`getStringExtra`
 * are no-ops under the plain `isReturnDefaultValues` unit-test jar and need Robolectric's shadow to
 * actually round-trip a value (matching `ReadingProgressServiceImplTest`'s reason for the same
 * runner). `@Config(application = android.app.Application::class)` mirrors `DocumentRowMarkersTest`:
 * a plain `Application`, not `DebugApp`/`BibleApplication`, since nothing here needs the real app's
 * heavy `onCreate`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class NavResultIntentsTest {

    /**
     * Proves the WIRING (the branch itself is unit-tested on `NavResultChannel`): that
     * [NavResultIntents.forReadingProgress] still produces what
     * `NavHostComposeActivity.finishWithChapterResult` produced by hand before this batch -- the
     * same [ActivityResultKind.EXTRA] tag and the same `"verse"` extra, and -- fix round 1, Finding
     * 4 -- NOT the `"action"` extra, which `MainBibleActivity.kt:2933` reads BEFORE `"verse"` to
     * pick the memorize branch, so its absence here is as load-bearing as the values that ARE
     * present.
     */
    @Test
    fun forReadingProgressChapterCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(ReadingProgressResult.Chapter("GEN", 1))

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("Gen.1.1", intent.getStringExtra("verse"))
        assertNull(intent.getStringExtra("action"))
    }

    /** The memorize half of the same edge -- classic `navigateToMemorize` (`:200-208`), verbatim. */
    @Test
    fun forReadingProgressMemorizeCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(
            ReadingProgressResult.Memorize(startOrdinal = 3, endOrdinal = 7),
        )

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("memorize", intent.getStringExtra("action"))
        assertEquals(3, intent.getIntExtra("startOrdinal", -1))
        assertEquals(7, intent.getIntExtra("endOrdinal", -1))
    }

    /**
     * Fix round 1, Finding 2. The `"data"` extra is the WHOLE payload
     * `ManageLabelsComposeActivity`'s `registerForActivityResult` launcher reads back from the label
     * editor (classic `LabelEditComposeActivity.finishWithData`, `:249-254`) -- and `RESULT_OK` is
     * half of it, which is why the result code is asserted here beside the extra rather than left to
     * the caller. Until this test existed the contract lived only in an `exitWithResult` lambda that
     * nothing could exercise.
     */
    @Test
    fun forLabelEditSavedCarriesTheDataExtraWithResultOk() {
        val payload = """{"label":{"name":"Prayer"}}"""

        val result = NavResultIntents.forLabelEdit(LabelEditResult.Saved(payload))

        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(payload, result.data?.getStringExtra("data"))
    }

    /**
     * The other half, classic's `Cancel` arm (`LabelEditComposeActivity.kt:242-245`): a bare
     * `setResult(RESULT_CANCELED)` with NO `Intent` at all. The null is as load-bearing as the
     * payload above -- `ManageLabelsComposeActivity`'s launcher branches on the result code and then
     * reads `"data"`, so `RESULT_OK` with an empty extra would be read as a save of nothing.
     */
    @Test
    fun forLabelEditCancelledCarriesNoDataAndResultCanceled() {
        val result = NavResultIntents.forLabelEdit(LabelEditResult.Cancelled)

        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
        assertNull(result.data)
    }

    /**
     * slice 2, Task 5. The label MANAGER's exit, and the one the six classic callers outside the
     * graph actually read: `MenuCommandHandler.kt:207`, `OptionsMenuItems.kt:572` and `:602`,
     * `BibleView.kt:618`, `CurrentGeneralBookPage.kt:80` and
     * `TextDisplaySettingsComposeActivity.kt:379` all do the SAME two things with the result --
     * check `resultCode == RESULT_OK`, then
     * `ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)`. So the assertion here is
     * deliberately written as that decode rather than as a string comparison: a packing that put the
     * JSON under a different key, or double-encoded it, would still pass a `getStringExtra("data")
     * == json` test written the lazy way round only if the key were right, but this shape also pins
     * that what comes back out is a readable payload with its fields intact.
     *
     * Unlike [forLabelEdit] this returns a bare `Intent`, not an `ActivityResult`: classic
     * `ManageLabelsComposeActivity` has exactly one result code on BOTH of its exits
     * (`saveAndExit`, `:635`, and the HIDELABELS reset path, `:667`) -- `RESULT_OK` -- so there is
     * no code/Intent pairing to keep together, which is the whole reason `forLabelEdit` returns one.
     */
    @Test
    fun forManageLabelsCarriesTheDataExtraTheClassicCallersDecode() {
        val labelId = IdType()
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.HIDELABELS,
            selectedLabels = mutableSetOf(labelId),
            isWindow = true,
        )

        val intent = NavResultIntents.forManageLabels(ManageLabelsResult(data.toJSON()))

        val decoded = ManageLabelsContract.ManageLabelsData
            .fromJSON(intent.getStringExtra("data")!!)
        assertEquals(ManageLabelsContract.Mode.HIDELABELS, decoded.mode)
        assertEquals(mutableSetOf(labelId), decoded.selectedLabels)
        assertTrue(decoded.isWindow)
        // `reset` is a FIELD on the payload, not a second result shape -- and its default matters as
        // much as its set value, because `HideLabelsPreference.openDialog` (OptionsMenuItems.kt:583)
        // branches on it before touching `selectedLabels`.
        assertFalse(decoded.reset)
    }

    /**
     * slice 2, Task 6. The bookmark LIST's exit, in its BIBLE-bookmark shape: classic
     * `BookmarksComposeActivity.onSelectBookmark` (`:172-186`), itself a mirror of classic
     * `Bookmarks.bookmarkSelected` (`Bookmarks.kt:295-323`). `MainBibleActivity.kt:2930-2957` is the
     * reader, and it reads in this order: the [ActivityResultKind.EXTRA] tag to pick its branch,
     * then `"verse"`, then -- for the generic shape -- `"key"`/`"book"`/`"ordinal"`. Every one of
     * them is asserted here, including the ones that must be ABSENT: a `"key"` alongside a `"verse"`
     * would not change what that dispatcher does today (it tests `verse` first), but it would make
     * the two shapes indistinguishable to anything that looked the other way round.
     *
     * `"description"`, `LABEL_NO_EXTRA` and `"listPosition"` are not read by that dispatcher at all.
     * They are read by the HISTORY seam: `HistoryManager.createHistoryItem` (`:153-155`) takes the
     * `IntentHistoryItem`'s title from `intent.getStringExtra("description")`, and this same Intent
     * OBJECT is the one stored there -- see the `bookmarkResults` channel in
     * `NavHostComposeActivity`. So `"description"` must be a String extra, not a CharSequence one:
     * `getStringExtra` returns null for the latter and the history row would read "-".
     */
    @Test
    fun forBookmarksBibleShapeCarriesTheSameExtrasClassicBuilt() {
        val intent = NavResultIntents.forBookmarks(
            BookmarkResult(
                verse = "Gen.1.1",
                description = "Bookmarks and my notes",
                labelNo = 3,
                listPosition = 7,
            ),
        )

        assertEquals(ActivityResultKind.Bookmarks.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("Gen.1.1", intent.getStringExtra("verse"))
        assertNull(intent.getStringExtra("key"))
        assertNull(intent.getStringExtra("book"))
        assertEquals("Bookmarks and my notes", intent.getStringExtra("description"))
        assertEquals(3, intent.getIntExtra(BookmarkControl.LABEL_NO_EXTRA, -1))
        assertEquals(7, intent.getIntExtra("listPosition", -1))
    }

    /**
     * The GENERIC half of the same exit (classic `:178-182`), which is the branch that carries three
     * extras rather than one. `MainBibleActivity.kt:2947-2955` needs `"key"` AND `"book"` together
     * -- it guards on `keyStr != null && bookStr != null` -- and then reads `"ordinal"` as an Int, so
     * dropping any one of the three silently turns a generic-book bookmark into a no-op. `"verse"`
     * must be absent: that dispatcher tests it FIRST, so a stray empty `"verse"` would send a
     * general-book selection down the bible branch.
     */
    @Test
    fun forBookmarksGenericShapeCarriesKeyBookAndOrdinal() {
        val intent = NavResultIntents.forBookmarks(
            BookmarkResult(
                key = "some key",
                book = "Pilgrim",
                ordinal = 42,
                description = "Bookmarks and my notes",
                labelNo = 0,
                listPosition = 0,
            ),
        )

        assertEquals(ActivityResultKind.Bookmarks.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertNull(intent.getStringExtra("verse"))
        assertEquals("some key", intent.getStringExtra("key"))
        assertEquals("Pilgrim", intent.getStringExtra("book"))
        assertEquals(42, intent.getIntExtra("ordinal", -1))
        assertEquals(0, intent.getIntExtra(BookmarkControl.LABEL_NO_EXTRA, -1))
    }

    /**
     * The HIDELABELS reset exit (`ManageLabelsComposeActivity.kt:663-668`). It is NOT a different
     * Intent shape -- `ManageLabelsMapper.applyReset` flips `reset` on the SAME payload and the same
     * `"data"` extra is built -- which is exactly why [ManageLabelsResult] carries just the JSON and
     * this packing needs no branch. `OptionsMenuItems.kt:583` (`HideLabelsPreference`),
     * `:609` (`AutoAssignPreference`) and `TextDisplaySettingsComposeActivity.kt:392` all read the
     * flag back off the decoded payload, so a packing that dropped it would silently turn "revert to
     * the inherited value" into "hide nothing".
     */
    @Test
    fun forManageLabelsCarriesTheResetFlagOnTheSameDataExtra() {
        val data = ManageLabelsMapper.applyReset(
            ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.HIDELABELS),
        )

        val intent = NavResultIntents.forManageLabels(ManageLabelsResult(data.toJSON()))

        val decoded = ManageLabelsContract.ManageLabelsData
            .fromJSON(intent.getStringExtra("data")!!)
        assertTrue(decoded.reset)
    }

    /**
     * nav-graph slice 4, Task 5. `MyDocumentPages`'s open-a-page exit -- classic
     * `MyDocumentPagesComposeActivity.returnWithPage`'s `resultIntent.putExtra(...)` + `finishOk()`
     * (`:224-226`) -- and the pair `MainBibleActivity.kt:2902-2913` reads back.
     */
    @Test
    fun myDocumentPagesSelectedCarriesTheKindAndBothKeys() {
        val result = NavResultIntents.forMyDocumentPages(
            MyDocumentPagesResult.Selected(documentInitials = "MyDoc_1", pageKey = "page-3"),
        )
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val extras = requireNotNull(result.data?.extras)
        assertEquals(ActivityResultKind.MyDocumentPages.name, extras.getString(ActivityResultKind.EXTRA))
        assertEquals("MyDoc_1", extras.getString("documentInitials"))
        assertEquals("page-3", extras.getString("pageKey"))
    }

    /**
     * The Dismiss button's exit (`MyDocumentPagesComposeActivity.kt:129`, `finishCanceled()` at
     * `:344`): `RESULT_CANCELED`, but unlike [forLabelEdit]'s `Cancelled` it still carries the
     * tagged Intent classic attached on this path.
     */
    @Test
    fun myDocumentPagesCancelledIsAPlainCancelWithTheKindStillTagged() {
        val result = NavResultIntents.forMyDocumentPages(MyDocumentPagesResult.Cancelled)
        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
        // Classic still attached resultIntent on the cancelled path (:344); MainBibleActivity's
        // dispatcher early-returns on RESULT_CANCELED (:2870-2876), so the tag is inert — but it
        // is preserved rather than quietly dropped.
        assertEquals(
            ActivityResultKind.MyDocumentPages.name,
            result.data?.extras?.getString(ActivityResultKind.EXTRA),
        )
    }

    /**
     * nav-graph slice 4, Task 6. The My-Documents LIST's relay-a-selection exit -- classic
     * `MyDocumentsComposeActivity`'s `pagesLauncher` callback (`:222-233`) building the same two
     * extras + `finishOk()`. Explicitly asserts `"changed"` is ABSENT (plan D8): classic's
     * `resultIntent.putExtra("changed", true)` (`:172`) is dropped because nothing reads it for
     * this kind -- see [MyDocumentsResult]'s own kdoc -- and this test is the only place that
     * decision is visible.
     */
    @Test
    fun myDocumentsSelectedCarriesTheKindAndBothKeysAndNoChangedExtra() {
        val result = NavResultIntents.forMyDocuments(
            MyDocumentsResult.Selected(documentInitials = "MyDoc_1", pageKey = "page-3"),
        )
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val extras = requireNotNull(result.data?.extras)
        assertEquals(ActivityResultKind.MyDocuments.name, extras.getString(ActivityResultKind.EXTRA))
        assertEquals("MyDoc_1", extras.getString("documentInitials"))
        assertEquals("page-3", extras.getString("pageKey"))
        assertFalse(extras.containsKey("changed"))
    }

    /**
     * The Save button's exit, classic `{ controller.save(); finishOk() }` (`:126`) with NEITHER key
     * set -- those are the relayed-selection path's alone.
     */
    @Test
    fun myDocumentsSavedCarriesTheKindWithNoKeysAndNoChangedExtra() {
        val result = NavResultIntents.forMyDocuments(MyDocumentsResult.Saved)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        val extras = requireNotNull(result.data?.extras)
        assertEquals(ActivityResultKind.MyDocuments.name, extras.getString(ActivityResultKind.EXTRA))
        assertNull(extras.getString("documentInitials"))
        assertNull(extras.getString("pageKey"))
        assertFalse(extras.containsKey("changed"))
    }

    /** The Dismiss button's exit, classic `{ finishCanceled() }` (`:127`): `RESULT_CANCELED`. */
    @Test
    fun myDocumentsCancelledIsAPlainCancel() {
        val result = NavResultIntents.forMyDocuments(MyDocumentsResult.Cancelled)
        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
        assertEquals(
            ActivityResultKind.MyDocuments.name,
            result.data?.extras?.getString(ActivityResultKind.EXTRA),
        )
    }

    // ——— slice 8 B1: the four channels the reading view now collects ———————————————————————————
    // Each packs the Intent the CLASSIC chooser Activity set, so the one reader of that shape --
    // KeyChooserResults / ReadingCommands.applyChosen* -- serves the in-graph answer unchanged.

    @Test
    fun aKeyChooserAnswerPacksClassicsGenBookKeyShape() {
        val intent = NavResultIntents.forKeyChooser(KeyChooserResult(key = "Gen.1.1", book = "KJV"))
        assertEquals(ActivityResultKind.GenBookKey.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("Gen.1.1", intent.getStringExtra("key"))
        assertEquals("KJV", intent.getStringExtra("book"))
        assertNull(intent.getStringExtra("bookAndKey"))
        assertEquals(
            KeyChooserResults.ChosenKey.GenBookKey(bookAndKeyJson = null, bookInitials = "KJV", osisRef = "Gen.1.1"),
            KeyChooserResults.chosenKeyFrom(intent.getStringExtra(ActivityResultKind.EXTRA)) { intent.getStringExtra(it) },
        )
    }

    @Test
    fun anEpubTocAnswerCarriesOnlyTheSerialisedBookAndKey() {
        // ChooseGeneralBookKeyComposeActivity.buildResult: a BookAndKey key carries its OWN document,
        // so classic wrote bookAndKey and NEITHER key nor book.
        val intent = NavResultIntents.forKeyChooser(KeyChooserResult(bookAndKeyJson = """{"k":"v"}"""))
        assertEquals("""{"k":"v"}""", intent.getStringExtra("bookAndKey"))
        assertNull(intent.getStringExtra("key"))
        assertNull(intent.getStringExtra("book"))
        assertEquals(ActivityResultKind.GenBookKey.name, intent.getStringExtra(ActivityResultKind.EXTRA))
    }

    @Test
    fun aPassageAnswerPacksTheVerseExtra() {
        val intent = NavResultIntents.forPassage(PassageResult("Ps.23.1"))
        assertEquals("Ps.23.1", intent.getStringExtra("verse"))
        assertEquals(ActivityResultKind.PassageGrid.name, intent.getStringExtra(ActivityResultKind.EXTRA))
    }

    @Test
    fun aDocumentAnswerPacksTheBookExtra() {
        val intent = NavResultIntents.forDocument(DocumentResult("ESV2011"))
        assertEquals(ActivityResultKind.ChooseDocument.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals(
            KeyChooserResults.ChosenKey.Document("ESV2011"),
            KeyChooserResults.chosenKeyFrom(intent.getStringExtra(ActivityResultKind.EXTRA)) { intent.getStringExtra(it) },
        )
    }

    @Test
    fun aWorkspaceAnswerPacksClassicsTwoExtras() {
        val picked = NavResultIntents.forWorkspace(WorkspaceResult(workspaceId = "ws-9", changed = true))
        assertEquals("ws-9", picked.getStringExtra("workspaceId"))
        assertTrue(picked.getBooleanExtra("changed", false))

        val saved = NavResultIntents.forWorkspace(WorkspaceResult(workspaceId = null, changed = false))
        assertFalse(saved.hasExtra("workspaceId"), "a plain Save selects no workspace, as classic's onResult")
        assertFalse(saved.getBooleanExtra("changed", true))
    }
}
