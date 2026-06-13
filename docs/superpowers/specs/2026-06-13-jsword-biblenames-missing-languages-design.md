# Design: JSword BibleNames for missing AndBible UI languages

## Background

AndBible 5.1 added several new UI languages (commit `0942141bb`, 2026-04-17:
`ca`, `fil`, `ms`, `ne`, `ur`). The UI language list is curated in
`app/src/main/res/values/arrays.xml` → `prefs_interface_locale_values` (51 languages).

Bible **book names** (used in references, navigation, and reference parsing/search) are
not provided by the Android app. They come from the bundled JSword library
(`AndBible/jsword`, included as a git submodule) via
`ResourceBundle.getBundle("BibleNames", locale)`, which loads
`jsword/src/main/resources/BibleNames_<locale>.properties`.

When no properties file exists for the active locale, Java's `ResourceBundle` fallback
chain ends at the base bundle `BibleNames.properties`, which is **English**. So for those
languages, book names display in English even though the rest of the UI is translated.

Locale plumbing: `BibleApplication.MyLocaleProvider.getUserLocale()` returns
`Locale.getDefault()` (the selected UI locale), with a hardcoded exception mapping
`sr`+`Latn` → `sr-LT`. This is registered with
`LocaleProviderManager.setLocaleProvider(...)`. No app-side change is needed for this
work — the gap is purely the missing JSword resource files.

## Gap analysis

Comparing the 51 curated UI locales against the BibleNames files present in JSword, the
following UI languages have **no** matching BibleNames file and therefore fall back to
English book names:

| Locale | Language          | Added to AndBible        |
|--------|-------------------|--------------------------|
| `ca`   | Catalan           | 2026-04-17 (5.1)         |
| `fil`  | Filipino/Tagalog  | 2026-04-17 (5.1)         |
| `ms`   | Malay             | 2026-04-17 (5.1)         |
| `ne`   | Nepali            | 2026-04-17 (5.1)         |
| `ur`   | Urdu              | 2026-04-17 (5.1)         |
| `uz`   | Uzbek             | 2015 (always missing)    |

All other UI locales resolve correctly, including `zh-Hant-TW`→`zh_TW`,
`zh-Hans-CN`→`zh_CN`, and `sr-Latn`→`sr_LT` (via ResourceBundle fallback / the hardcoded
exception).

### Pre-existing bug discovered: Indonesian shows Hindi book names

`BibleNames_in.properties` is **byte-for-byte identical to `BibleNames_hi.properties`**
(Hindi content, e.g. `Gen.Full=उत्पत्ति`). A correct `BibleNames_id.properties` exists
(real Indonesian, e.g. `Gen.Full=Kejadian`, 127 books) but is **never used**, because Java
normalizes the locale code `id` → the legacy code `in`, so `ResourceBundle` resolves the
Indonesian locale to the corrupt `BibleNames_in.properties` (Hindi).

**Effect:** Indonesian users see Hindi book names. This is in scope for this work.

## Scope

Seven resource changes in the `jsword` submodule, plus tests and a submodule pointer bump
in the main repo:

1. Add `BibleNames_ca.properties` (Catalan)
2. Add `BibleNames_fil.properties` (Filipino)
3. Add `BibleNames_ms.properties` (Malay)
4. Add `BibleNames_ne.properties` (Nepali)
5. Add `BibleNames_ur.properties` (Urdu)
6. Add `BibleNames_uz.properties` (Uzbek)
7. Fix `BibleNames_in.properties` to contain Indonesian (replace Hindi content with the
   content of `BibleNames_id.properties`)

No AndBible app-side code changes. No upstream CrossWire PR (kept in the AndBible fork).

## Design

### File format and location

- Location: `jsword/src/main/resources/BibleNames_<locale>.properties`
- Encoding: **raw UTF-8** (matches existing non-Latin files `ru`/`ar`/`he`/`hi`; no
  `\uXXXX` escapes). Applies to Devanagari (`ne`) and Arabic/Nastaliq (`ur`).
- Keep the CrossWire / LGPL license header comment block at the top of each file.
- Per book, three keys: `<OSIS>.Full`, `<OSIS>.Short`, `<OSIS>.Alt`.

### Key semantics (from `BibleNames.java`)

- `.Full` — full book name (display + matched for reference parsing).
- `.Short` — short/abbreviated name. If omitted, the code sets `short = full`.
- `.Alt` — comma-separated list of **alternate input spellings/abbreviations**, used
  **only** for parsing user-typed references in `getBook(find)` (matched alongside Full and
  Short). It is **not** a display name. A value matching `^#.*` (e.g. `#Gen.Alt`) means
  "undefined" and is treated as empty.

### Fallback semantics (key design lever)

`BibleNames_<locale>` has the English base `BibleNames.properties` as its `ResourceBundle`
parent. Any key **absent** from a localized file is inherited from English. Therefore:

- We never fabricate names we are unsure of — uncertain entries are simply omitted and
  fall back to English.
- A partial file is valid and parses without error.

### Coverage policy

- **Required, translated carefully:** the 66 canonical books + the three intro keys
  (`INTRO_BIBLE`, `INTRO_OT`, `INTRO_NT`).
- **Deuterocanonical books:** best-effort. Include where a well-established native name
  exists; otherwise omit the key → English fallback. (For `ms` this is free, since the full
  127-entry Indonesian `id` file is a close reference.)

### `.Alt` policy — conservative, no guessing

- Default every `.Alt` to the `#<Key>` placeholder (undefined).
- Add real alternates **only** where the language has a genuinely established, commonly
  typed abbreviation convention. Wrong guesses are harmless but pollute the parser; missing
  alternates just mean the user types the Full or Short name (both already matched).

### Translation approach

Produced inline by Claude Code (no subagents — only 6 files), language by language, using
existing related JSword files as cross-references:

- `ms` (Malay) ← **`id`** (real Indonesian, very close orthography) — **not** the corrupt
  `in` file.
- `ca` (Catalan) ← `es` / `fr` as cross-reference.
- `ne` (Nepali) ← `hi` (Devanagari neighbour) as cross-reference.
- `fil` (Filipino) — established Tagalog book names.
- `ur` (Urdu), `uz` (Uzbek) — established Bible-translation book names; conservative on Alt.

### Indonesian fix

Replace the content of `BibleNames_in.properties` with the content of
`BibleNames_id.properties` (correct Indonesian). Both `in` and `id` files then carry the
same Indonesian content, so the locale resolves correctly regardless of `id`/`in`
normalization.

## Verification

- `cd jsword && ./gradlew test` runs `BibleNamesTest`.
- Add one test per new locale following the existing `testLoadXX()` pattern
  (`testLoadCA`, `testLoadFIL`, `testLoadMS`, `testLoadNE`, `testLoadUR`, `testLoadUZ`),
  which loads the bundle and verifies it parses and all keys resolve (no
  `MissingResourceException`, valid format).
- Add a **stronger assertion** than load-only for the new locales: assert that a known
  book's localized name differs from the English name (e.g. Genesis), proving localization
  actually applies rather than silently falling back to English.
- Add/extend a test for the Indonesian fix: assert that the `in` locale now yields
  Indonesian (`Kejadian`) rather than Hindi.

## Git / commit plan

- `jsword` submodule (`AndBible/jsword`): currently detached HEAD — check out the tracked
  branch first. **One commit per language** (resource file + its test), and the Indonesian
  fix as its own separate commit.
- Main repo (`and-bible`): separate commit bumping the submodule pointer (`git add jsword`).
- This design doc lives in the main repo.

## Non-goals / out of scope

- No upstream contribution to CrossWire JSword.
- No changes to the AndBible UI language list or locale plumbing.
- No new languages beyond the six identified + the Indonesian fix.
- Exhaustive deuterocanonical coverage is not required (English fallback is acceptable).

## Risks

- **Translation accuracy:** book-name conventions vary; mitigated by leaning on established
  names and existing reference files, and by omitting (→ English) anything uncertain rather
  than guessing.
- **`.Alt` noise:** mitigated by the conservative policy above.
- **Encoding:** raw UTF-8 must be preserved on commit (no accidental re-encoding); verified
  against existing non-Latin files.
