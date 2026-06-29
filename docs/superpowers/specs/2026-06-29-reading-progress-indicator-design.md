# Reading Progress Indicator — Design

**Date:** 2026-06-29
**Branch:** `epub-reading-progress`
**Status:** Approved design, pending implementation plan

## Summary

Add a new text display option, `showReadingProgress`, that shows the reader how far
through the current document they are. The indicator is a small, unobtrusive,
text-only overlay in the bottom corner of the BibleView (same visual style as the
existing page-number overlay), and it updates live as the user scrolls.

The feature is **type-aware**: the meaning of "how far through" depends on the
document type. A single setting drives one indicator whose unit and format adapt to
the document being read.

| Document type        | Unit (what 100% means) | Display format example      |
|----------------------|------------------------|-----------------------------|
| EPUB / general book  | the whole book         | `47% · page 142/~300`       |
| Bible                | the current book       | `47% · ch 23/50`            |
| Commentary           | the current book       | `47% · ch 23/50`            |
| Dictionary / other   | — (not supported)      | indicator hidden            |

This first iteration implements **EPUB, Bible, and commentary** (commentary is
verse-keyed and reuses the Bible computation via a shared helper). General books,
dictionaries, and other types are left unsupported for now, but the architecture is
type-aware so they can be added later without rework.

Note on rendering classes: Bibles render as `BibleDocument`, but **commentaries render
as plain `OsisDocument`** (via `CurrentCommentaryPage` → `CurrentPageBase`), with the
commentary `SwordBook` as `book` and a `VerseRange` as `key`. So the commentary path is
*not* the `BibleDocument` path — it needs its own branch in `OsisDocument.asHashMap`
that reuses the same verse-key helper.

## Goals

- Show live reading position for EPUB books (percentage + estimated page) relative to
  the **whole book**.
- Show live reading position for Bibles/commentaries (percentage + chapter) relative
  to the **current book**.
- Be a per-window/workspace/global text display setting, following the existing
  `TextDisplaySettings` inheritance and the standard 7-step checklist.
- Work in all display modes (dark, light, monochrome/e-ink, no-animations). Text-only
  overlay → no colour dependency.

## Non-goals

- Not a "chapters read" / reading-plan tracker. AndBible already has
  `autoTrackReading`, `ChapterReadHistory`, and reading plans — those record *which
  chapters were marked read*. This feature is a **live position indicator** for the
  current book, a separate concept. The two must not be conflated.
- No new persisted per-user reading state. The indicator is derived from the current
  scroll position and document metadata.
- No pixel-accurate page count. The page count is explicitly an estimate (shown with
  a `~`); the design prioritises **stability** over precision.

## Behaviour

- The indicator renders only when:
  - `config.showReadingProgress` is enabled, **and**
  - the current document type is supported (EPUB/general book, Bible, or commentary),
    **and**
  - the required progress metadata is present and well-formed.
- It updates as the current ordinal changes (driven by the existing
  `verse-notifier` `currentVerse`).
- Placement: floating, translucent, text-only box in the bottom corner, reusing the
  `.pagenumber` styling. Must not overlap awkwardly with the existing page-number
  overlay when both are enabled (see Edge cases).
- Default: **off** (consistent with `showPageNumber`).

### Percentage

**Bible / commentary** — verse ordinals are *absolute* within the book's versification:

`percent = clamp01((currentOrdinal − unitStart) / (unitEnd − unitStart)) × 100`

- `currentOrdinal` — top visible ordinal, already flowing via `verse-notifier`.
- `unitStart` / `unitEnd` — the book's first/last absolute verse ordinal (per-document metadata).

**EPUB / general book** — anchor ordinals **restart at 0 for every spine item** (see
`SwordContentFacade.addAnchors`, called once per spine item with `ordinal = 0`). A
single ordinal is therefore *not* a whole-book position. Whole-book position is the
**cumulative ordinal offset** of the current fragment plus the in-fragment offset:

```
globalOrdinal = fragmentOffset + (currentOrdinal − fragmentLocalStart)
percent       = clamp01(globalOrdinal / bookOrdinalSpan) × 100
```

- `fragmentOffset` — sum of all earlier fragments' ordinal spans (per-document metadata).
- `bookOrdinalSpan` — total ordinal span of the whole book (per-document metadata).
- `fragmentLocalStart` — the fragment's local `ordinalRange[0]` (already in metadata).
- The current fragment is resolved by `currentKey` (osisRef), **not** ordinal
  containment — local ordinals overlap across loaded fragments.

`percent` is monotonic and smooth while scrolling. Guard against a zero-length unit
(`bookOrdinalSpan == 0`, or Bible `unitEnd == unitStart`) → indicator hidden.

### EPUB page estimate

The page count is derived from two separately-sourced quantities:

1. **`bookCharCount`** — total character count of the whole book. **Device-independent,
   constant per book, stored once** (see Android section). This is the stable input.
2. **`charsPerPage`** — derived from the *current BibleView layout* (font size, line
   spacing, margins, viewport), **measured empirically once per layout signature and
   cached** (see Vue section). This makes the page count reflect the actual device and
   display settings.

```
totalPages   ≈ round(bookCharCount / charsPerPage)
currentPage  ≈ round(percent/100 × totalPages)   // clamped to 1..totalPages
```

`totalPages` is constant while reading (cached per layout signature), so the displayed
`/~Y` does not jump. `currentPage` tracks the monotonic `percent`, so `X` advances
smoothly. The total is recomputed only when the layout signature changes (font size,
spacing, margins, rotation) — an expected, one-time change.

### Bible / commentary chapter

```
currentChapter = readingProgress.currentChapter   // from payload
totalChapters  = readingProgress.chapterCount      // from payload (v11n.getLastChapter(book))
```

Displayed as `ch {currentChapter}/{totalChapters}` alongside the percentage. No page
estimate for Bibles (fixed chapter/verse structure makes chapters the natural unit).

## Architecture

### Android side — exposing progress metadata

Add a type-aware `readingProgress` payload to the document metadata sent to the
WebView (`asHashMap` in `ClientPageObjects.kt`). The JSON object carries only the
fields relevant to the type; absent/`null` when the type is unsupported.

- **`OsisDocument` — EPUB branch (`book.isEpub`):**
  ```json
  { "kind": "book", "fragmentOffset": <n>, "bookOrdinalSpan": <n>, "charCount": <bookCharCount> }
  ```
  - `fragmentOffset` = `EpubBackend.fragmentOffset(key)` — sum of earlier fragments' spans.
  - `bookOrdinalSpan` = `EpubBackend.bookOrdinalSpan` — total of all fragment spans.
  - `charCount` = `EpubBackendState.totalCharacters` (stored, see below).
  - The fragment's local ordinal range is carried separately as the document's
    `ordinalRange` (from `SwordContentFacade.ordinalRangeFor`).
  - General books are a later extension.
- **`OsisDocument` — commentary branch (`book.bookCategory == COMMENTARY`, `book is SwordBook`, `key is VerseRange`):**
  ```json
  { "kind": "bible", "unitStart": <bookFirstOrdinal>, "unitEnd": <bookLastOrdinal>, "chapterCount": <n>, "currentChapter": <c> }
  ```
  - Computed from the commentary book's versification and the `key` verse range.
- **`BibleDocument`:**
  ```json
  { "kind": "bible", "unitStart": <bookFirstOrdinal>, "unitEnd": <bookLastOrdinal>, "chapterCount": <n>, "currentChapter": <c> }
  ```
  - Computed from `swordBook.versification` and `verseRange`.

The verse-key computation is identical for Bible and commentary, so extract it into a
small **pure helper** reused by both branches, e.g.:
```
ReadingProgressInfo.forVerseKey(v11n, verseRange) ->
    { unitStart = <book first verse ordinal>,
      unitEnd   = <book last verse ordinal>,
      chapterCount  = v11n.getLastChapter(book),
      currentChapter = verseRange.start.chapter }
```
where `book = verseRange.start.book`. `currentChapter` is the current document's start
chapter — a good approximation of the live chapter, since Bibles/commentaries load
per chapter/entry and the current document is resolved by scroll position (this
matches the existing `chapterNumber` semantics). The helper is pure → unit-testable
without a heavy SWORD fixture.

Embedding `currentChapter` inside the `readingProgress` payload keeps the Vue side
self-contained (it does not need the separate top-level `chapterNumber` field, which is
absent on `OsisDocument`).

#### Storing the EPUB character count

The per-EPUB optimized database (`EpubDatabase`, one per book, built once by
`optimizeEpub()` and cached as `optimized.sqlite3.gz`) is the natural home.

- Add a single-row metadata entity, e.g. `EpubMeta(@PrimaryKey id: Int = 0, totalCharacters: Int)`.
- Bump `EPUB_DATABASE_VERSION` 1 → 2 and add a migration creating the table.
- **New / re-optimized books:** compute `totalCharacters` during `optimizeEpub()`
  (fragments are already iterated there) and insert it.
- **Already-installed books (db v1, no value):** `EpubBackendState.totalCharacters`
  getter computes it lazily once from the fragment texts, upserts it into the table,
  and returns it. This avoids forcing a costly re-optimization of every installed EPUB.
- `fragmentOffset` / `bookOrdinalSpan` are **not** stored — derived on demand from the fragments.

`totalCharacters` counts visible text characters of the book (the same text content
used for rendering/search), summed across fragments.

### Vue side — computation, component, placement

- **Composable `useReadingProgress(config, currentVerse, documents, appSettings, calculatedConfig)`**
  - Resolves the *current document* from `currentVerse` (the same mechanism the title
    bar uses to know the current book), reads its `readingProgress` metadata.
  - Computes `percent` from ordinals.
  - For `kind: "book"`: computes `totalPages`/`currentPage` using the cached
    `charsPerPage` (see below).
  - For `kind: "bible"`: computes chapter `X/Y` from the payload's `currentChapter`
    and `chapterCount`.
  - Returns a ready-to-render display string (via i18n) and `percent`, or `null` when
    unsupported / metadata missing → component hides itself.

- **Stable page sizing (`charsPerPage`)**
  - A **layout signature** is the tuple of layout-affecting settings:
    `(fontSize, lineSpacing, marginLeft, marginRight, viewportWidth, viewportHeight)`.
  - `charsPerPage` is **measured empirically and cached per layout signature**:
    ```
    charsPerPage ≈ renderedTextLength × scrollAmount / scrollHeight
    ```
    where `renderedTextLength` is the text length of the loaded content,
    `scrollHeight` its rendered pixel height, and `scrollAmount` the viewport page
    height (already known to BibleView).
  - This is measured **once per layout signature**, only after a minimum amount of
    representative content has rendered (and optionally averaged over the first couple
    of screenfuls) to avoid locking onto an unrepresentative first fragment (e.g. a
    sparse title page).
  - While the signature is unchanged (normal scrolling), `charsPerPage` — and thus
    `totalPages` — is **constant** → the `/~Y` value does not jump.
  - When the signature changes (font size, spacing, margins, rotation), it is
    re-measured once → `totalPages` updates once. Expected behaviour.
  - Empirical measurement is preferred over a font-metrics formula because it reflects
    the *actual* rendered density (font, margins, screen) without guessing.

- **Component `ReadingProgress.vue`**
  - Rendered in `BibleView.vue` near the `.pagenumber` overlay,
    `v-if="config.showReadingProgress"` (component internally hides when the composable
    returns `null`).
  - Reuses `.pagenumber`-style CSS (bottom corner, translucent, text-only).

- **i18n (`app/bibleview-js/src/lang/default.yaml`)**
  - `reading_progress_page`: `"{percent}% · page {page}/~{total}"`
  - `reading_progress_chapter`: `"{percent}% · ch {chapter}/{total}"`

### Settings plumbing (standard 7-step checklist)

Per `docs/adding-text-display-setting.md`:

1. `WorkspaceEntities.kt` — `showReadingProgress: Boolean? = null` field;
   `Types.READING_PROGRESS`; add to `getValue()`, `setValue()`, and the `default`
   companion.
2. `WorkspacesMigrations.kt` — new migration (3× `ALTER TABLE`: Workspace, PageManager,
   GlobalTextDisplaySettings) + bump `WORKSPACE_DATABASE_VERSION`.
3. `app/src/main/res/xml/text_display_settings.xml` — `SwitchPreferenceCompat`.
4. `app/src/main/res/values/strings.xml` — setting title + summary (English only).
5. `OptionsMenuItems.kt` — title mapping in `Preference.title`.
6. `TextDisplaySettings.kt` — add to `getPrefItem()`.
7. `app/bibleview-js/src/composables/config.ts` — add to `Config` type + default
   (`false`); add to the appropriate refresh predicate if needed.

## Edge cases

- Ordinal at unit start → 0%; at/after unit end → 100% (clamped).
- Zero-length or missing metadata → indicator hidden (no division by zero).
- Single-chapter book → `ch 1/1`.
- EPUB before `charsPerPage` is measured → show percentage only (omit page) until the
  first valid measurement, then add the page; avoids a flash of a wrong page count.
- Infinite scroll crossing Bible book boundaries → indicator follows the current book
  (unit switches with the current document).
- Both `showPageNumber` and `showReadingProgress` enabled → ensure the two overlays do
  not visually collide (stack/offset them).
- Monochrome/e-ink → text-only, no colour; verify contrast in light & dark.

## Testing

- **Vue unit tests** (primary) for the computation:
  - `(metadata, currentOrdinal) → expected display string` for both `book` and `bible`
    kinds.
  - Edge cases: start = 0%, end = 100%, clamping beyond range, single-chapter book,
    missing/zero-length metadata → hidden.
  - `charsPerPage` caching: same layout signature returns cached value; changed
    signature triggers re-measure; page omitted until first measurement.
- **Kotlin unit test** for the pure verse-key helper
  (`ReadingProgressInfo.forVerseKey`) — book first/last ordinal, chapter count, and
  current chapter from versification, covering both a Bible verse range and a
  commentary verse range. No heavy SWORD fixture required.
- EPUB `totalCharacters` computation is covered mainly by Vue/manual testing; a full
  Robolectric EPUB fixture is heavy (see project notes), so an Android unit test for it
  is out of scope unless a lightweight path is found.

## Open questions

None outstanding — design approved.
