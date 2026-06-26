# Document Sync — Testing Corrections Design

**Date:** 2026-06-26
**Status:** Approved (design phase)
**Builds on:** [2026-06-24-document-sync-design.md](2026-06-24-document-sync-design.md)

## Summary

A batch of corrections to the `CloudDocumentsActivity` management view and the
document-sync wiring, found during first hands-on testing. Five changes:

1. Redesign the filter bar to look like the Download Documents screen, with a
   name search, a status filter, and a document-category filter.
2. Stop listing virtual (DB-backed) documents — AI Documents and other
   MyDocuments — which sync via the AI/DB sync, not document sync.
3. Only enable document sync once the user commits via "Start syncing"; backing
   out of the auto-mode setup screen must leave sync off.
4. Rename and conditionally hide the confusing "Select" overflow menu item.
5. Lowercase the cloud folder name (`…-sync-documents`) to match the other
   sync folders.

(There are five user-facing changes; item 2 also carries two small related
cleanups.)

## 1. Filter bar redesign

### Current state

`activity_cloud_documents.xml` shows a `RadioGroup` of status filters
(All / Installed / Cloud / Updates / Blocked) inside a `HorizontalScrollView`.
`CloudDocumentsActivity.applyFilter()` filters `allItems` by the single selected
status.

### Target

Replace the radio group with a single horizontal filter row modelled on
`document_selection.xml` (the Download / Choose Documents screen). **Three
controls** (three is the practical maximum for the row width):

| Control | Widget | Behaviour |
|---|---|---|
| Name search | `EditText` (free text) | Case-insensitive substring match against the document name. |
| Status | `Spinner` | All / Installed / Cloud / Updates / Blocked (the existing statuses). |
| Category | `Spinner` | Document category, reusing the existing `@array/documentTypes`. |

No language filter (deliberately dropped for space; category was chosen as the
more useful of the two).

### Category spinner

Reuse the existing `@array/documentTypes` array (All / Bible / Commentary /
Dictionary / Book / Map / Addons) and the same index → `BookCategory` mapping
used by `DocumentSelectionBase`:

| Index | Array entry | BookCategory |
|---|---|---|
| 0 | All | (no filter — show all) |
| 1 | Bible | `BIBLE` |
| 2 | Commentary | `COMMENTARY` |
| 3 | Dictionary | `DICTIONARY` |
| 4 | Book | `GENERAL_BOOK` |
| 5 | Map | `MAPS` |
| 6 | Addons | `AND_BIBLE` |

Note: unlike `DocumentSelectionBase` (where index 0 excludes `AND_BIBLE`), index
0 here means "no category filter" and shows everything, which is less
surprising in a management view.

### Data change: category must be known for cloud-only documents

`DocumentStatusItem.type` is the archive type (`SWORD` / `MyBible` / …), not the
book category. Local books expose `book.bookCategory`, but cloud-only documents
have no category today — `meta.json` does not store it. To filter cloud-only
items by category we add it to the manifest:

- **`DocumentSyncMeta`**: add `val category: String = ""` (the `BookCategory`
  name; default `""` keeps backward/forward compatibility — `Json` is configured
  with `ignoreUnknownKeys = true` and `encodeDefaults = true`).
- **`DocumentSync.pushDocument()`**: set `category = book.bookCategory.name`.
- **`DocumentSync.DocumentStatusItem`**: add `val category: BookCategory?` —
  resolved from `book.bookCategory` for locally installed items, or parsed from
  the cloud meta's `category` string for cloud-only items. A missing/blank/
  unparseable cloud category resolves to `null`, which matches only the "All"
  filter.

### Filter application

`applyFilter()` combines all three predicates (status AND name-substring AND
category). To make this unit-testable, extract a pure function:

```
filterItems(items, status, nameQuery, category): List<DocumentStatusItem>
```

The activity keeps the three control states and calls this on any change.

## 2. Hide virtual (DB-backed) documents

### Problem

The list shows **AI Documents** (and any user MyDocument). These are DB-backed
books that already synchronise through the AI/DB sync; they must not appear in
document sync.

### Investigation: why they leak, and which marker to use

`DocumentSync.installedSyncableBooks()` currently filters only `!isPseudoBook`.

- `isPseudoBook` (`AndBiblePseudoBook`) has a **narrow** meaning: a placeholder
  for a copyright-restricted Bible translation shown in the download list as
  "Not Available" (`FakeBookFactory.getPseudoBookConf`). It is **not** a general
  "virtual book" flag. Changing it to also cover MyDocuments was rejected: it
  would grey out MyDocuments in the document chooser (`DocumentListItem`
  `disabled_background`) and blank their search names (`DocumentSelectionBase`),
  because the chooser intentionally treats MyDocuments as real, selectable books.
- `isSpecial` (`AndBibleSpecial`) does **not** cover AI Documents either.
  `createMyDocumentMetadata` (`MyDocumentBackend`) sets only `AndBibleMyDocument`
  on MyDocument books. `isSpecial` is set only on the four helper books (My
  Notes, Journal, Compare, Memorize), which are not added to `Books.installed()`
  and therefore never reach `DocumentSync.scan()` in the first place.
- MyDocuments **are** registered via `Books.installed().addBook()`
  (`MyDocumentBookManager.registerDocument`) and carry `AndBibleMyDocument`, so
  the correct and only needed marker is `Book.isMyDocument`.

### Fix

`DocumentSync.installedSyncableBooks()`:

```kotlin
Books.installed().books.filter { !it.isPseudoBook && !it.isMyDocument }
```

### Related cleanups (confirmed in scope)

- **`BackupControl.kt:468`** — `backupModulesViaIntent` lists installed books to
  back up as a SWORD-module zip with `filter { !it.isPseudoBook }`. MyDocuments
  are DB-backed (no on-disk module files) and belong to the DB backup, so add
  `&& !it.isMyDocument` to drop them from the module-backup multiselect.
- **`CurrentPageBase.kt:31`** — remove the unused `isPseudoBook` import (dead
  import; the file actually uses `isMyDocument`).

## 3. Enable sync only on "Start syncing"

### Problem

`SyncSettings.kt` sets `DocumentSyncSettings.enabled = true` immediately when the
master switch is turned on (after sign-in), *before* the user confirms in the
setup screen. Backing out of the auto-mode setup view leaves sync enabled.

`DocumentSyncSettings.enabled` reads/writes the same preference key
(`sync_enable_documents`) that backs the `SwitchPreferenceCompat`, so the switch
state follows `enabled`.

### Fix

- **Auto mode** (`automatic == true`): the master-switch listener does **not**
  set `enabled = true`. It signs in and launches `CloudDocumentsActivity` in
  setup mode (for result). `performSetupSync()` ("Start syncing") sets
  `DocumentSyncSettings.enabled = true` as part of the bulk operation. If the
  user leaves the setup screen (back / up) without pressing Start, `enabled`
  stays `false`. On return to settings, the switch state is re-read from the
  preference (replace the immediate `recreate()` with a refresh triggered when
  the setup activity returns / on resume) so the switch correctly shows off.
- **Manual mode** (`automatic == false`): there is no "Start syncing" CTA, so the
  master switch enables sync immediately (current behaviour) and opens the
  management view. No automatic transfer happens in manual mode regardless, so
  enabling on toggle is the commit.

## 4. "Select" overflow item → "Select multiple", hidden when redundant

### Problem

The overflow (3-dot) "Select" item enters multi-select mode for bulk download.
In setup mode the list is *already* in selection mode, so "Select" appears to do
nothing there. The label is also unclear.

### Fix

- Rename the `cloud_doc_select` string "Select" → "Select multiple".
- In `onPrepareOptionsMenu`, hide the item when the list is already in selection
  mode: `isVisible = !adapter.isSelectionMode()`. This removes it in setup mode
  (where it is a no-op) while keeping it useful as a bulk-download affordance in
  the normal management view.

## 5. Lowercase cloud folder name

### Problem

The cloud folder is `…-sync-DOCUMENTS` (uppercase suffix), inconsistent with the
other sync folders (`…-sync-bookmarks`, etc., which use lowercase
`dbDef.categoryName`).

### Fix

`CloudSync.kt:106`: `DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "DOCUMENTS"` →
`"documents"`, producing `…-sync-documents`.

**Migration:** none. The feature is still in pre-release testing with no
production users, so the old `…-sync-DOCUMENTS` folder is simply orphaned (any
test documents uploaded there stop being visible and the old folder can be
deleted manually from the cloud). No migration code is written.

## Testing

Kotlin-only change → `./gradlew testStandardGoogleplayDebugUnitTest`. No Vue
tests.

- **`DocumentSync.scan()` excludes MyDocuments** — a MyDocument-flagged book is
  not returned in the status list. (Verify the `isMyDocument` filter.)
- **Filter logic** (unit) — the extracted `filterItems(...)` pure function across
  combinations of status × name substring × category, including category `null`
  matching only "All".
- **`DocumentSyncMeta` category round-trip** (unit) — JSON serialises the new
  `category` field, and a meta JSON missing `category` (old client) deserialises
  to `""` (backward compatibility).

Item 3 (enable-on-Start) is mostly Activity-level wiring and is verified by
manual testing on device rather than a unit test.

## Components touched

- `app/src/main/res/layout/activity_cloud_documents.xml` — new filter row.
- `CloudDocumentsActivity.kt` — three-control filter state, `filterItems`
  extraction, `onPrepareOptionsMenu` hiding, setup-mode enable on Start.
- `DocumentSync.kt` — `isMyDocument` filter, `category` on `DocumentStatusItem`
  and in `pushDocument`.
- `DocumentSyncMeta.kt` — `category` field.
- `SyncSettings.kt` — auto-mode enable deferred to setup; switch state refresh on
  return.
- `CloudSync.kt` — lowercase folder suffix.
- `BackupControl.kt` — exclude MyDocuments from module backup.
- `CurrentPageBase.kt` — remove dead import.
- `strings.xml` — rename `cloud_doc_select`; add category-spinner strings if not
  already covered by `@array/documentTypes`.

## Explicitly not done

- No language filter in the management view.
- No migration for the renamed cloud folder.
- No change to the meaning of `isPseudoBook` or `isSpecial`.
