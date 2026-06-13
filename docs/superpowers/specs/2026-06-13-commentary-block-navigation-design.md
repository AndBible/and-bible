# Commentary block-based navigation + infinite scroll

**Date:** 2026-06-13
**Status:** Approved design (pending implementation plan)

## Background and problem

AndBible commentaries (`BookCategory.COMMENTARY`) are currently navigated
verse-by-verse (`CurrentCommentaryPage.next()/previous()` → `nextVerse()`/
`previousVerse()`). Many commentaries store the same content across a range of
verses: a single entry may cover e.g. verses 1–5, or even span a chapter
boundary. This causes two problems:

1. **Navigation**: "next" shows the same text again until the verse range ends —
   the user must press several times to reach new content.
2. **Infinite scroll**: not enabled for commentaries at all
   (`infinite-scroll.ts` `enabledCategories` = only `BIBLE`, `GENERAL_BOOK`).
   If enabled as-is, the same content would repeat multiple times.

The app already has proven deduplication logic for the same problem on the LLM
tool side: `GetCommentariesTool.deduplicateConsecutiveBlocks()`
(`app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt:167-187`).
It merges consecutive verses that share identical **rendered** content — it
compares rendered content rather than the raw OSIS fragment, so an entry that
spans a chapter boundary collapses into a single block (regression test for
OSTicket #3303, `GetCommentariesDedupTest.kt`).

## Goal

In commentaries, the unit of navigation and infinite scroll is a **block** — a
run of consecutive verses with identical rendered content — rather than a single
verse. The same content never repeats; empty verses are skipped.

## Scope

- **Commentaries only** (`BookCategory.COMMENTARY`). Not general books,
  dictionaries, or any other type.

## Design decisions (approved)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Canonical key for a block | **Block start verse** (single `Verse`) | Threading a range key through the `CurrentCommentaryPage`/sync/bookmark/`PageManager` machinery would be a large, risky change. The key model stays single-verse. |
| Range display (e.g. "1–5") | **Separate display/navigation info** | Computed via the dedup logic, passed to the Vue side. The header shows the range; "next" knows where to continue. |
| Empty verses (no entry) | **Skipped** during navigation/scroll | Smooth reading, no empty pages. An empty verse acts as a block boundary. |
| Infinite scroll mode | **Automatic, per the `config.infiniteScroll` setting** | Consistent with Bibles. |
| Approach | **Lazy incremental block resolution** | No precomputing the whole book; the walk is short except for one large entry. Reuses the proven dedup logic. |

## Architecture

### Block definition

A block = consecutive verses whose `renderComparable` result is identical. An
empty verse (`null` content: blank or `<div/>`) flushes the current block and is
skipped (acts as a separator). Comparison is done on rendered content, not the
raw OSIS fragment, so per-verse metadata (e.g. a chapter boundary) does not
prevent collapsing.

### Component 1: shared render function

Extract a shared function from `GetCommentariesTool`:

```
renderComparable(book: SwordBook, verse: Verse): String?
  // raw readOsisFragment → XMLOutputter string
  // null if blank or "<div/>"
```

Both `CommentaryBlockResolver` and `GetCommentariesTool` reuse this read +
blank-check primitive so it is not duplicated. For *boundary comparison*, the
resolver's `renderComparable` converts that raw XML to **plain text** (via
`OsisToPlainText`) before comparing: plain text is insensitive to per-verse OSIS
metadata (e.g. a chapter boundary), so semantically identical entries still
collapse into one block. (`GetCommentariesTool` keeps its own final-content
comparison — text or XML depending on the requested format.)

> Implementation note: an earlier draft of this spec proposed comparing the raw
> `readOsisFragment` output directly. During implementation that was changed to
> plain-text comparison because raw XML can differ in per-verse metadata for the
> same logical entry, which would prevent collapsing. The shared primitive is the
> read + blank-check (`renderCommentaryFragmentXml`); the plain-text comparison
> lives in `renderComparable`.

### Component 2: `CommentaryBlockResolver`

Location: `net.bible.service.sword` (or `control.page`). Lazy, incremental,
per-verse render cache (cleared when the document/key changes).

```
resolveBlock(book, verse) -> (startVerse, endVerse, content?)
  // walks backward to the start and forward to the end comparing renderComparable.
  // Used when sync lands the commentary in the middle of a block (Bible on verse 3 → 1–5).
  // If the verse itself is empty → returns the empty state (no snapping).

nextBlockStart(book, fromVerse) -> Verse?
  // forward, skipping empties; start verse of the next non-empty block. null = end.

prevBlockStart(book, beforeVerse) -> Verse?
  // backward, skipping empties to the previous non-empty verse, then to that
  // block's start. null = start.
```

The walk uses `bibleTraverser.getNextVerse`/`getPrevVerse` semantics (crosses
book boundaries within the whole Bible); it stops at the Bible boundary (when the
traverser returns the same verse).

`GetCommentariesTool.deduplicateConsecutiveBlocks` stays as-is (list-based, for
its own needs) but builds on the same `renderComparable` principle.

### Component 3: navigation — `CurrentCommentaryPage`

- `next()`:
  - `val (_, end, _) = resolver.resolveBlock(book, currentVerse)`
  - `val start = resolver.nextBlockStart(book, end)`
  - `if (start != null) setKey(start)`, otherwise stay put.
- `previous()`:
  - `val (start, _, _) = resolver.resolveBlock(book, currentVerse)`
  - `val prevStart = resolver.prevBlockStart(book, start)`
  - `if (prevStart != null) setKey(prevStart)`.

Boundary: `null` → stay put (as at a verse boundary today).

### Component 4: page content + range info delivery

- `getPageContent(blockStartVerse)` already produces the correct content (the
  block's shared text = the start verse's fragment).
- Add the block's **range info** (start/end osisRef + display name) to the
  commentary `OsisDocument`, computed via `resolveBlock`.
- Vue types (`documents.ts` / `client-objects.ts`): optional `commentaryRange`
  field (start/end osisRef + display name).

### Component 5: infinite scroll feed

**Kotlin `BibleView.kt` (`requestMoreToBeginning`/`requestMoreToEnd`):**
Add a commentary branch alongside the existing `isBible` / general book branches.
- Track block start keys `firstKey`/`lastKey`-style (like general books).
- To end: `nextBlockStart(book, lastBlockEnd)` → `getPageContent(start)` →
  `response`. `null` → `response(callId, null)` (reachedEnd).
- To beginning: `prevBlockStart(book, firstBlockStart)` → likewise.

**Vue `infinite-scroll.ts`:**
- Add `"COMMENTARY"` to the `enabledCategories` set → `documentSupportsChapterNavigation`
  and automatic loading activate per the `config.infiniteScroll` setting.
- Render the range in the header/separator between blocks.

## Data flow

```
Sync Bible→commentary (verse 3):
  setKey(3) → resolveBlock(book, 3) → start=1, end=5
  → display: content + header "1–5", active key = 1

next():
  resolveBlock(book, currentVerse) → end=5
  nextBlockStart(book, 5) → skip empties 6–19 → 20
  setKey(20)

Infinite scroll down:
  requestMoreToEnd → nextBlockStart(book, lastBlockEnd) → next block start
  → getPageContent(start) → asJson → Vue appends the document
```

## Error cases / edge cases

- **Sync to an empty verse**: show the current "no commentary" state, do not snap
  to the nearest block. Navigating from there forward/backward finds the nearest
  non-empty block.
- **Bible boundary**: `nextBlockStart`/`prevBlockStart` returns `null` →
  navigation stops, infinite scroll returns `response(callId, null)` → reachedEnd.
- **One large entry for the whole book**: the walk renders a handful of verses
  (collapses into one block); the render cache prevents repetition when scrolling
  back and forth.
- **Dense per-verse commentary**: content differs immediately → the walk is 1
  step per block.

## Testing

**Kotlin — `CommentaryBlockResolverTest`** (injectable render function, like
`GetCommentariesDedupTest`):
- block from the middle backward to the start (`resolveBlock`)
- forward over empties (`nextBlockStart`)
- backward over empties (`prevBlockStart`)
- single-verse blocks
- all empty → null
- block at module start/end (boundary → null)
- #3303 chapter boundary collapses into one block

**Vue (`*.spec.js`):**
- `COMMENTARY` activates infinite scroll (`documentSupportsChapterNavigation`)
- range header rendering
- skipping empty blocks in the document queue

## No changes

- No new setting (reuse `config.infiniteScroll`).
- No new translation strings (reuse existing verse-reference formatting).
- The range key model is not threaded into the machinery — the active key stays a
  single verse.

## Key files

| File | Change |
|------|--------|
| `GetCommentariesTool.kt` | Extract `renderComparable`; use it |
| `CommentaryBlockResolver.kt` (new) | Block boundary resolution |
| `CurrentCommentaryPage.kt` | Make `next()`/`previous()` block-based |
| `CurrentPageBase.kt` / commentary `getPageContent` | Attach range info to `OsisDocument` |
| `BibleView.kt` | Commentary branch in `requestMoreToBeginning/End` |
| `infinite-scroll.ts` | Add `COMMENTARY` to `enabledCategories` |
| `documents.ts` / `client-objects.ts` | Optional `commentaryRange` field |
| tests (Kotlin + Vue) | as described above |
