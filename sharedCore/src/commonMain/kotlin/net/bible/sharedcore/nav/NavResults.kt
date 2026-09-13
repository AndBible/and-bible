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

/**
 * What the reading-progress destination hands back: either a chapter to open or an ordinal range to
 * memorize. Mirrors the two `Intent`s `NavHostComposeActivity.finishWithChapterResult` /
 * `finishWithMemorizeResult` used to build by hand, and `NavResultIntents.forReadingProgress` is now
 * the only place those `Intent`s are built.
 *
 * [Chapter]'s field names are the old host function's own parameter names (`bookId`/`chapter`).
 * [Memorize]'s are `startOrdinal`/`endOrdinal`, NOT the old host function's own `start`/`end`
 * (fix round 1): three of four naming sources agree on "ordinal" — the `"startOrdinal"`/
 * `"endOrdinal"` extras keys themselves, the KJVA-ordinal semantics the values carry, and
 * [net.bible.sharedcore.progress.ReadingProgressController]'s own
 * `onNavigateToMemorize(startOrdinal, endOrdinal)` — and only the (now-deleted) private host
 * function's parameter list said otherwise. This is new `commonMain` API iOS will consume, so the
 * unit belongs in the name.
 */
sealed interface ReadingProgressResult {
    data class Chapter(val bookId: String, val chapter: Int) : ReadingProgressResult

    data class Memorize(val startOrdinal: Int, val endOrdinal: Int) : ReadingProgressResult
}

/**
 * What the Bookmarks list hands back on a row selection. Mirrors the one result [android.content.Intent]
 * `BookmarksComposeActivity.onSelectBookmark` builds (`BookmarksComposeActivity.kt:165-194`), which
 * itself mirrors classic `Bookmarks.bookmarkSelected` (`Bookmarks.kt:295-323`).
 *
 * The row is one of two shapes and the extras reflect that: a `BibleBookmarkWithNotes` sets only
 * [verse]; a `GenericBookmarkWithNotes` sets [key]/[book]/[ordinal] instead and leaves [verse] null.
 * [description], [labelNo] and [listPosition] are always present. This is a flat mirror of the
 * Intent's extras, not a sealed hierarchy, because that Intent itself carries one optional field per
 * shape rather than a tagged union.
 */
data class BookmarkResult(
    val verse: String? = null,
    val key: String? = null,
    val book: String? = null,
    val ordinal: Int? = null,
    val description: String,
    val labelNo: Int,
    val listPosition: Int,
)

/**
 * What `ManageLabelsComposeActivity` hands back on every exit: `saveAndExit`
 * (`ManageLabelsComposeActivity.kt:573-644`) and the HIDELABELS reset path
 * (`ManageLabelsComposeActivity.kt:663-668`) both build the exact same
 * `Intent().putExtra("data", data.toJSON())` — no second extra.
 *
 * [data] is the whole `ManageLabelsData` JSON string (`ManageLabelsContract.kt:47-59`), because that
 * contract type cannot cross into this module (see the file note below). It deliberately carries no
 * separate `reset` field: `reset` is a FIELD of `ManageLabelsData` itself
 * (`ManageLabelsContract.kt:60`), set by `ManageLabelsMapper.applyReset(data)` before `:667` builds
 * this exact result, and it already travels inside [data]. A second field here would duplicate state
 * the JSON already carries and could disagree with it.
 */
data class ManageLabelsResult(val data: String)

/**
 * What `LabelEditComposeActivity` hands back. Mirrors `onFinish` (`LabelEditComposeActivity.kt:240-254`):
 * [Saved] covers both its `Save` and `Delete` arms, which both end at `finishWithData` and so build the
 * identical `Intent().putExtra("data", updated.toJSON())` (`LabelData`, `LabelEditContract.kt:31`) —
 * a completed delete is just a saved [LabelEditContract.LabelData] with its own `delete` flag set.
 * [Cancelled] mirrors the `Cancel` arm, which sets `RESULT_CANCELED` and no `data` extra at all.
 *
 * [Cancelled] is a VARIANT, not a boolean alongside a nullable [Saved.data] (plan D1): there is no
 * payload to carry on cancellation, so a variant with none is the honest shape.
 */
sealed interface LabelEditResult {
    data class Saved(val data: String) : LabelEditResult

    data object Cancelled : LabelEditResult
}

/**
 * What the My-Documents list hands back. Three variants, because classic had three exits:
 * [Selected] is `MyDocumentsComposeActivity`'s relay of the pages child's pick (`:222-233`),
 * [Saved] is `finishOk()` with no selection (`:126`), [Cancelled] is `finishCanceled()` (`:127`).
 *
 * `MainBibleActivity.kt:2914-2929` reads only `documentInitials`/`pageKey`; the classic `"changed"`
 * extra (`MyDocumentsComposeActivity.kt:172`) is NOT carried, because nothing reads it for this
 * kind — the tree's only `getBoolean("changed")` is the WORKSPACE_CHANGED branch (plan D8).
 */
sealed interface MyDocumentsResult {
    data class Selected(val documentInitials: String, val pageKey: String) : MyDocumentsResult

    data object Saved : MyDocumentsResult

    data object Cancelled : MyDocumentsResult
}

/**
 * What the pages editor hands back — the same three shapes, consumed in two places:
 * `MainBibleActivity.kt:2902-2913` when entered from `CurrentGeneralBookPage`, and the
 * My-Documents arm when entered from inside the graph, which re-delivers it as
 * [MyDocumentsResult.Selected].
 */
sealed interface MyDocumentPagesResult {
    data class Selected(val documentInitials: String, val pageKey: String) : MyDocumentPagesResult

    data object Saved : MyDocumentPagesResult

    data object Cancelled : MyDocumentPagesResult
}

/**
 * What the three KEY choosers -- `ChooseGeneralBookKey`, `ChooseMapKey`, `ChooseDictionaryWord` --
 * hand back. ONE type and, on `ChooserNavDeps`, one channel for
 * all three (design §6.2): their three classic result `Intent`s are deliberately identical, and
 * `MainBibleActivity.onActivityResult` dispatches all of them through the single
 * `ActivityResultKind.GenBookKey` arm into `applyChosenGenBookKey`. Three channels would be three
 * ways to say the same thing.
 *
 * Two shapes in one flat type, mirroring the extras rather than inventing a sealed hierarchy (the
 * reason [BookmarkResult] is flat too -- the Intent itself carries one optional field per shape):
 *
 * - [bookAndKeyJson] is `ChooseGeneralBookKeyComposeActivity`'s `"bookAndKey"` extra, set only when
 *   the chosen key is a `BookAndKey` -- `BookAndKey.serialized`, i.e. `BookAndKeySerialized` encoded
 *   as JSON, which is what the consumer's `BookAndKeySerialized.fromJSON` reads back. Hence
 *   `...Json` in the name: it is not an opaque token, and the consumer decodes it.
 * - [key] + [book] are the `"key"` (osisRef) / `"book"` (initials) pair every other exit uses, and
 *   the branch the consumer takes when [bookAndKeyJson] is null.
 *
 * The `ActivityResultKind.GenBookKey` tag the classic Intents also carried is NOT a field here, for
 * the reason [ReadingProgressResult]'s own kdoc gives: a kind tag names the INTENT's shape to
 * `MainBibleActivity`'s dispatcher, and is not part of what the destination decided. These
 * destinations are entered only from inside the graph (design §1.1), so no Intent is ever packed
 * from this type at all.
 */
data class KeyChooserResult(
    val bookAndKeyJson: String? = null,
    val key: String? = null,
    val book: String? = null,
)

/**
 * What `GridChoosePassage` hands back: the chosen passage's `osisID`, classic's `"verse"` extra
 * (`GridChoosePassageComposeActivity.finishWithVerse`), consumed by `applyChosenVerse`.
 *
 * One non-null field and no cancellation variant, deliberately: classic never called `setResult` on
 * any other path out of that Activity -- backing out simply finished, and the consumer saw
 * `RESULT_CANCELED` with no data. The in-graph equivalent of that path is a plain pop with nothing
 * published, not a variant of this type.
 */
data class PassageResult(val verse: String)

/**
 * What `WorkspaceSelector` hands back on a SAVE or a workspace pick: classic
 * `WorkspaceSelectorComposeActivity`'s two `RESULT_OK` extras, `workspaceId` (nullable -- a plain
 * Save that changed nothing selects no workspace) and `changed`.
 *
 * No cancelled variant, for [PassageResult]'s reason: classic's cancel path set `RESULT_CANCELED`
 * with an EMPTY Intent, and the consumer (`MainBibleActivity`'s `WORKSPACE_CHANGED` arm) does
 * nothing at all on it. The in-graph equivalent of that path is a plain pop with nothing published,
 * not a variant of this type -- there is no payload to carry.
 *
 * `changed` is deliberately kept even though [MyDocumentsResult] drops its own `"changed"` extra:
 * this is the ONE result in the tree whose consumer actually reads that extra (plan D8), which is
 * why `WORKSPACE_CHANGED` is also the one request code `MainBibleActivity` dispatches on.
 */
data class WorkspaceResult(val workspaceId: String?, val changed: Boolean)

/**
 * What `TextDisplaySettings` hands back to `WorkspaceSelector` on a DETACHED (selector-originated)
 * edit: the edited `SettingsBundle` as JSON, plus whether the edit was a whole-scope reset.
 *
 * Produced on ONE condition, preserved exactly from classic's `finish()` override: only when the
 * detached edit actually CHANGED (`DetachedWorkspaceEdit.changed`, i.e. dirty or reset -- plan D3).
 * Merely opening the screen and leaving must not mark the workspace changed on the selector's next
 * Save, which is what classic did before that condition existed.
 *
 * [settingsBundleJson] is opaque here and decoded only host-side: `SettingsBundle` embeds Room-backed
 * `WorkspaceEntities` types that cannot cross into this module (see this file's closing note). The
 * workspace id is NOT a second field, for the same reason classic did not echo one -- it is already
 * inside the JSON, and the consumer reads it back out with `SettingsBundle.fromJson(...).workspaceId`
 * so the round trip survives the host process being killed while the editor was foregrounded.
 */
data class TextSettingsResult(val settingsBundleJson: String, val reset: Boolean)

/**
 * What `ChooseDocument` hands back: the chosen document's `initials`, classic's `"book"` extra
 * (`ChooseDocumentComposeActivity.handleDocumentSelection`), consumed by `applyChosenDocument`.
 * Single-field and variant-free for [PassageResult]'s reason.
 */
data class DocumentResult(val book: String)

// No `@Serializable` anywhere in this file, deliberately. A result is packed into an Intent's
// EXTRAS by `NavResultIntents`, never encoded as JSON, and the two `:app` contract types slice 2
// carries (`ManageLabelsData`, `LabelData`) embed Room entities (`BookmarkEntities.Label`,
// `WorkspaceEntities.WorkspaceLabelOverride`) and cannot move into this module at all. A payload
// therefore carries their JSON as an opaque `String` and this module never decodes it. Sharing the
// Room entities themselves is a real question, but it belongs to the iOS-compatibility design (can
// Room run there, or does the app bundle its own SQLite), not to a migration batch.
