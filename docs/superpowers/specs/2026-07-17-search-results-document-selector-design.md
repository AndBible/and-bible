# Search results document selector

**Date:** 2026-07-17
**Status:** Design approved, pending implementation plan
**Origin:** OSTicket 3361 — a user could not view "Find all occurrences" (Strong's) results in their preferred translation.

## Problem

"Find all occurrences" (the link shown for a Strong's number in the dictionary view) runs a Strong's-number search and shows the results in a single, fixed translation. Which translation is used is derived implicitly from the links window's current Bible (`LinkControl.showAllOccurrences` uses `currentPageManager.currentBible.currentDocument`), forced into `SELECTED_TRANSLATIONS` as one document.

Consequences:
- The only way to change the result translation is to change the document in the links window *before* clicking the link — undiscoverable.
- If the links-window Bible has no Strong's numbers (e.g. an Arabic translation), it silently falls back to an English Strong's Bible, and there is no way to pick a different one from the results screen.

This is a UX limitation, **not** a broken regression. The Strong's feature did break when multi-translation search was introduced, but that was already repaired (commit `242befe02`, "Fixed strongs search feature which broke after multi translation search implementation"). The current behaviour works; it just offers no document choice at the results stage.

## Goal

Let the user change which document(s) search results come from **from the search results screen itself**, re-running the search live. Applies to both Strong's "find all occurrences" and normal searches.

## Scope decision (semantic model)

For a Strong's search, "change document" means **choose among Strong's-enabled Bibles** — the chosen document is both searched and displayed. This reuses the existing multi-translation search infrastructure directly (a Strong's-number query only matches Strong's-tagged modules, so the candidate list is restricted to those).

Explicitly **out of scope:** decoupling the searched document from the display document (e.g. searching KJV but rendering result verses in Arabic). That would fully satisfy the original Arabic ticket even without an Arabic Strong's module, but requires rendering result rows from a different module than the one searched, diverging from the multi-search model. Not pursued now.

Implication: the Arabic reporter benefits only if an Arabic Strong's-tagged module exists. The general capability (pick the result document, discoverable at the results stage) is the shipped improvement.

## Design

### UX / behaviour

- `SearchResults` gains a clickable document selector **in the toolbar** showing the current selected translations as abbreviations (e.g. `KJV` or `KJV, BSB`).
- Tapping it opens `Dialogs.multiselect(...)` — the same chooser the search screen (`Search.kt`) already uses.
- The candidate list depends on the search type:
  - **Strong's search:** only Strong's-enabled Bibles (`hasFeature(FeatureType.STRONGS_NUMBERS)`).
  - **Normal search:** all Bibles (same list as the search screen). Bonus: change/add translations while viewing results without returning to the search screen.
- Selection is **multi-select** in both cases (consistent with existing multi-translation search; results are grouped per translation by `getMultiSearchResults`). Selecting a single document is effectively single-select with no extra code.
- On confirm: update `selectedTranslations`, re-run `getMultiSearchResults(selectedTranslations, searchText)`, refresh the list and the results-count title.

### Implementation (small surface)

1. **`SearchControl.kt`** — add constant `IS_STRONGS_SEARCH`.
2. **`LinkControl.kt`** (`showAllOccurrences`) — set intent extra `IS_STRONGS_SEARCH = true`. Existing `SELECTED_TRANSLATIONS = [strongsBible]` remains the initial selection.
3. **`SearchResults.kt`**
   - Read the `IS_STRONGS_SEARCH` extra.
   - Add the toolbar selector (via view binding) that shows `selectedTranslations` and opens `Dialogs.multiselect`.
   - Compute the candidate list with a pure, unit-testable function `candidateDocuments(strongsSearch: Boolean, allBibles: List<SwordBook>)`: filter to `STRONGS_NUMBERS` when `strongsSearch`, else return all.
   - On confirm: update state, re-run the search, refresh list + title. If a selected document is unindexed, redirect to `SearchIndex` (same handling as `Search.kt`).
4. **Layout / strings** — add the toolbar element to the search-results toolbar. **No new translatable strings:** the element shows the selected translation abbreviations (already data), and the chooser dialog reuses the existing `choose_translations` string (also used for the element's accessibility content description).

### Search-type detection

Detected via the explicit `IS_STRONGS_SEARCH` intent extra set by `LinkControl`, rather than inspecting `searchText` for `strong:` — more robust and avoids false positives from user-typed queries.

### Persistence

The selector **remembers** the chosen documents so the next search restores them:

- **Normal Bible searches** share the manual search screen's existing key
  (`SearchControl.SEARCH_TRANSLATIONS_PREF` = `"search_selected_translations"`). It is the same
  selection either way, so results-screen changes round-trip to the search screen and back.
- **Strong's find-all-occurrences** uses its own key
  (`SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF`), since its selection is a Strong's-enabled
  subset. `LinkControl.showAllOccurrences` restores it as the initial selection (keeping only
  installed, Strong's-enabled books) and falls back to the auto-selected Strong's bible when
  nothing usable is remembered.

`SearchResults.persistSelection()` writes the appropriate key whenever the selection changes.
(This supersedes an earlier session-local design; persistence matches the behaviour described in
issue #1982 — "from that point on all results show in the preferred module".)

### Unindexed documents and the index-build flow

Both entry points route an unindexed target to `SearchIndex` **carrying the full search context**
(`SEARCH_TEXT`, `SELECTED_TRANSLATIONS`, and `IS_STRONGS_SEARCH` for the Strong's flow).
`SearchIndex` forwards its extras to `SearchIndexProgressStatus`, which — with `SEARCH_TEXT`
present — proceeds straight to `SearchResults` after indexing rather than falling back to the
`Search` entry screen. Additionally:

- `LinkControl.showAllOccurrences` checks the index status of the document actually being searched
  (`checkStrongs(searchBible)`), not only when it equals the current bible — otherwise an unindexed
  fallback Strong's bible silently returned "0 verses" (`getMultiSearchResults` skips unindexed
  books).
- `Search.onResume` reloads the persisted selection, so returning to the search screen (which can
  resurface via `onRestart` without a fresh `onCreate`) reflects a change made in the results
  selector.

### Dead code cleanup (separate commit)

`SearchControl.TARGET_DOCUMENT` is written in three places (`BibleView.kt`, `Search.kt`, `LinkControl.kt`) but read nowhere — the display translation is driven entirely by `SELECTED_TRANSLATIONS` / `SEARCH_DOCUMENT`. Remove the constant and its write sites as an independent cleanup commit, not mixed into the feature.

## Edge cases

- **One candidate document:** selector still shows (indicates where results come from); dialog lists one item. Harmless.
- **No Strong's Bible:** `showAllOccurrences` already errors out before reaching `SearchResults` (`no_indexed_bible_with_strongs_ref`), so with the Strong's flag the candidate list is always ≥ 1.
- **Unindexed selected document:** redirect to `SearchIndex` (reuse `Search.kt` logic); return to results after indexing.
- **Empty selection:** dialog applies only when `selected.isNotEmpty()` (same as `Search.kt`).

## Testing

- **Unit test** `candidateDocuments(strongsSearch, allBibles)`: `true` → only `STRONGS_NUMBERS` documents; `false` → all documents. Pure function, tested with fake/mocked books.
- **Intent-extra honoured:** `IS_STRONGS_SEARCH` drives the candidate filtering.
- Live re-run of the search requires a built index (CI-only Bible modules), so it is not unit-tested; coverage focuses on the pure filtering logic.

## Non-goals

- Decoupling searched document from displayed document (see Scope decision).
- Any change to how results are grouped or rendered beyond re-running with a new selection.
- Broader search-screen refactoring.
