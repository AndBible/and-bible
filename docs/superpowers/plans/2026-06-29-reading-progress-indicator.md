# Reading Progress Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `showReadingProgress` text display option that shows a small, text-only bottom-corner overlay reporting how far through the current document the reader is — percentage + page estimate for EPUB (whole book), percentage + chapter for Bible/commentary (current book).

**Architecture:** Android exposes a type-aware `readingProgress` payload per document (EPUB: whole-book max ordinal + stored char count; Bible/commentary: current book's verse-ordinal range + chapter count, computed from JSword versification). Vue computes percentage from the live `currentVerse` ordinal, and for EPUB derives a stable page count by dividing the stored char count by an empirically-measured "chars per screen" cached per layout signature. A new `ReadingProgress.vue` overlay renders the formatted string.

**Tech Stack:** Kotlin/Android (Room, JSword), Vue 3 + TypeScript (Vitest), sprintf-js for i18n interpolation.

## Global Constraints

- New Kotlin/Vue files use the copyright header: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham" in new files). Edited existing files: update year to `2026` and `Tuomas Airaksinen` → `Sykerö Software / Tuomas Airaksinen` (keep "Martin Denham" if present).
- All user-facing strings go through the translation system. English only (no other languages). Android: `app/src/main/res/values/strings.xml`. Vue: `app/bibleview-js/src/lang/default.yaml`.
- Kotlin/Java: import classes, use simple names (no fully-qualified names in code).
- Default value of the new setting: **off** (`false`).
- Gradle commands require `dangerouslyDisableSandbox: true`. JS-free Kotlin builds: add `-x npmInstall -x npmUpgrade -x jsBuild`.
- Must work in all display modes (dark, light, monochrome/e-ink, no-animations). The overlay is text-only (no colour dependency).
- The enum/XML key name is `SHOW_READING_PROGRESS`; the entity field is `showReadingProgress`; the Room column is `text_display_settings_showReadingProgress`.

---

### Task 1: Kotlin `ReadingProgressInfo` helper

Pure, versification-only computation of the type-aware progress payload, plus its JSON serialization. Pure → unit-testable without SWORD modules.

**Files:**
- Create: `app/src/main/java/net/bible/android/control/page/ReadingProgressInfo.kt`
- Test: `app/src/test/java/net/bible/android/control/page/ReadingProgressInfoTest.kt`

**Interfaces:**
- Produces:
  - `data class ReadingProgressInfo(kind: String, unitStart: Int, unitEnd: Int, chapterCount: Int? = null, currentChapter: Int? = null, charCount: Int? = null)`
  - `val ReadingProgressInfo.asJson: String`
  - `ReadingProgressInfo.forVerseKey(v11n: Versification, verseRange: VerseRange): ReadingProgressInfo`
  - `ReadingProgressInfo.forEpub(maxOrdinal: Int, charCount: Int): ReadingProgressInfo`
- Consumes: `mapToJson(Map<String,String>): String` and `wrapString(String?): String` — existing top-level helpers in the `net.bible.android.control.page` package (used by `CommentaryRangeInfo.asJson` in `ClientPageObjects.kt`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/android/control/page/ReadingProgressInfoTest.kt`:

```kotlin
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

package net.bible.android.control.page

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingProgressInfoTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    @Test
    fun forVerseKey_genesisStart_spansWholeBook() {
        val range = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 1, 1), Verse(kjv, BibleBook.GEN, 1, 5))
        val info = ReadingProgressInfo.forVerseKey(kjv, range)

        assertEquals("bible", info.kind)
        assertEquals(50, info.chapterCount)         // Genesis has 50 chapters
        assertEquals(1, info.currentChapter)
        assertEquals(Verse(kjv, BibleBook.GEN, 1, 1).ordinal, info.unitStart)
        val lastChapter = kjv.getLastChapter(BibleBook.GEN)
        val lastOrdinal = Verse(kjv, BibleBook.GEN, lastChapter, kjv.getLastVerse(BibleBook.GEN, lastChapter)).ordinal
        assertEquals(lastOrdinal, info.unitEnd)
        assertTrue(info.unitEnd > info.unitStart)
        assertEquals(null, info.charCount)
    }

    @Test
    fun forVerseKey_midBook_reportsCurrentChapterButWholeBookUnit() {
        val start = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 1, 1), Verse(kjv, BibleBook.GEN, 1, 1))
        val mid = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 30, 1), Verse(kjv, BibleBook.GEN, 30, 2))

        val startInfo = ReadingProgressInfo.forVerseKey(kjv, start)
        val midInfo = ReadingProgressInfo.forVerseKey(kjv, mid)

        assertEquals(30, midInfo.currentChapter)
        assertEquals(50, midInfo.chapterCount)
        // Unit (whole book) is identical regardless of position within the book:
        assertEquals(startInfo.unitStart, midInfo.unitStart)
        assertEquals(startInfo.unitEnd, midInfo.unitEnd)
    }

    @Test
    fun forEpub_buildsBookKind() {
        val info = ReadingProgressInfo.forEpub(maxOrdinal = 1000, charCount = 50_000)
        assertEquals("book", info.kind)
        assertEquals(0, info.unitStart)
        assertEquals(1000, info.unitEnd)
        assertEquals(50_000, info.charCount)
        assertEquals(null, info.chapterCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (`dangerouslyDisableSandbox: true`):
`./gradlew testStandardGoogleplayDebugUnitTest --tests "*ReadingProgressInfoTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: FAIL — `ReadingProgressInfo` unresolved reference (does not compile).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/net/bible/android/control/page/ReadingProgressInfo.kt`:

```kotlin
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

package net.bible.android.control.page

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification

/**
 * Type-aware reading-progress metadata sent to the WebView so it can render a
 * "how far through" indicator. For verse-keyed documents (Bible/commentary) the unit is
 * the current book; for EPUB/general books the unit is the whole book.
 */
data class ReadingProgressInfo(
    val kind: String,            // "bible" or "book"
    val unitStart: Int,
    val unitEnd: Int,
    val chapterCount: Int? = null,
    val currentChapter: Int? = null,
    val charCount: Int? = null,
) {
    val asJson: String get() = mapToJson(buildMap {
        put("kind", wrapString(kind))
        put("unitStart", unitStart.toString())
        put("unitEnd", unitEnd.toString())
        chapterCount?.let { put("chapterCount", it.toString()) }
        currentChapter?.let { put("currentChapter", it.toString()) }
        charCount?.let { put("charCount", it.toString()) }
    })

    companion object {
        /** Progress relative to the whole Bible book that [verseRange] starts in. */
        fun forVerseKey(v11n: Versification, verseRange: VerseRange): ReadingProgressInfo {
            val book = verseRange.start.book
            val lastChapter = v11n.getLastChapter(book)
            val lastVerseNo = v11n.getLastVerse(book, lastChapter)
            return ReadingProgressInfo(
                kind = "bible",
                unitStart = Verse(v11n, book, 1, 1).ordinal,
                unitEnd = Verse(v11n, book, lastChapter, lastVerseNo).ordinal,
                chapterCount = lastChapter,
                currentChapter = verseRange.start.chapter,
            )
        }

        /** Progress relative to the whole EPUB/general book. */
        fun forEpub(maxOrdinal: Int, charCount: Int): ReadingProgressInfo =
            ReadingProgressInfo(
                kind = "book",
                unitStart = 0,
                unitEnd = maxOrdinal,
                charCount = charCount,
            )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*ReadingProgressInfoTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/control/page/ReadingProgressInfo.kt \
        app/src/test/java/net/bible/android/control/page/ReadingProgressInfoTest.kt
git commit -m "Add ReadingProgressInfo helper for reading-progress metadata"
```

---

### Task 2: Store and expose EPUB total character count

Add a one-row `EpubMeta` table to the per-EPUB database, plus `maxOrdinal`/`totalCharacters` accessors on the backend. `totalCharacters` is computed lazily once (no forced re-optimization of installed books) and cached in the DB.

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/Epub.kt`
- Modify: `app/src/main/java/net/bible/service/sword/epub/EpubBackendState.kt`
- Modify: `app/src/main/java/net/bible/service/sword/epub/EpubBook.kt` (the `EpubBackend` class, ~lines 105-131)

**Interfaces:**
- Consumes: existing `EpubBackendState.dao`, `getKey(frag)`, `read(key)`, `useSaxBuilder`, `useXPathInstance`, `xhtmlNamespace`, `Filters` (all already in `EpubBackendState.kt`).
- Produces:
  - `EpubBackendState.maxOrdinal: Int`
  - `EpubBackendState.totalCharacters: Int`
  - `EpubBackend.maxOrdinal: Int`, `EpubBackend.totalCharacters: Int`
  - `EpubMeta` entity + `EpubDao.getMeta()`, `EpubDao.insert(meta: EpubMeta)`

- [ ] **Step 1: Add the `EpubMeta` entity, DAO methods, migration, and version bump**

In `app/src/main/java/net/bible/android/database/Epub.kt`:

Add the import near the other migration import (line 31 already has `import net.bible.android.database.migrations.Migration`):

```kotlin
import net.bible.android.database.migrations.makeMigration
```

Add the entity after `StyleSheet` (after line 59):

```kotlin
@Entity
class EpubMeta(
    @PrimaryKey val id: Int = 0,
    val totalCharacters: Int,
)
```

Add to the `EpubDao` interface (before its closing `}` at line 85):

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insert(meta: EpubMeta)

    @Query("SELECT * FROM EpubMeta WHERE id = 0")
    fun getMeta(): EpubMeta?
```

Replace the version/migrations/`@Database` block (lines 88-103) with:

```kotlin
const val EPUB_DATABASE_VERSION = 2

private val addEpubMeta = makeMigration(1..2) { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS `EpubMeta` (`id` INTEGER NOT NULL, `totalCharacters` INTEGER NOT NULL, PRIMARY KEY(`id`))")
}

val epubMigrations = arrayOf<Migration>(addEpubMeta)

@Database(
    entities = [
        EpubHtmlToFrag::class,
        EpubFragment::class,
        StyleSheet::class,
        EpubMeta::class,
    ],
    version = EPUB_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class EpubDatabase: RoomDatabase() {
    abstract fun epubDao(): EpubDao
}
```

(The migration is applied automatically — `getEpubDatabase()` in `EpubBook.kt:81` already spreads `*epubMigrations`.)

- [ ] **Step 2: Add `maxOrdinal` and lazy `totalCharacters` to `EpubBackendState`**

In `app/src/main/java/net/bible/service/sword/epub/EpubBackendState.kt`, add these next to `getOrdinalRange` (after line 340, before the class closing brace). They reuse the same BVA-text extraction pattern as `buildSearchIndex` (lines 264-275):

```kotlin
    val maxOrdinal: Int get() = dao.fragments().maxOfOrNull { it.ordinalEnd } ?: 0

    /**
     * Total visible-text character count of the whole book. Computed once from the
     * fragment BVA text and cached in the EpubMeta table; subsequent reads are O(1).
     * This avoids forcing a re-optimization of already-installed EPUBs.
     */
    val totalCharacters: Int get() {
        dao.getMeta()?.let { return it.totalCharacters }
        var total = 0
        for (frag in dao.fragments()) {
            val doc = useSaxBuilder { it.build(StringReader(read(getKey(frag)))) }
            for (bva in useXPathInstance { xp ->
                xp.compile("//ns:BVA", Filters.element(), null, xhtmlNamespace).evaluate(doc)
            }) {
                total += bva.text.length
            }
        }
        dao.insert(EpubMeta(totalCharacters = total))
        return total
    }
```

Add the import for `EpubMeta` near the existing `import net.bible.android.database.EpubFragment` (line 25):

```kotlin
import net.bible.android.database.EpubMeta
```

(`StringReader` is already imported at line 38; `Filters`, `useSaxBuilder`, `useXPathInstance`, `xhtmlNamespace` are already in scope.)

- [ ] **Step 3: Delegate from `EpubBackend`**

In `app/src/main/java/net/bible/service/sword/epub/EpubBook.kt`, in the `EpubBackend` class, add after `getOrdinalRange` (line 130), mirroring that delegation:

```kotlin
    val maxOrdinal get() = state.maxOrdinal
    val totalCharacters get() = state.totalCharacters
```

- [ ] **Step 4: Verify it compiles**

Run (`dangerouslyDisableSandbox: true`):
`./gradlew compileStandardGithubDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL. (Room validates the schema/migration at compile time.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/database/Epub.kt \
        app/src/main/java/net/bible/service/sword/epub/EpubBackendState.kt \
        app/src/main/java/net/bible/service/sword/epub/EpubBook.kt
git commit -m "Store and expose EPUB total character count and max ordinal"
```

---

### Task 3: Emit `readingProgress` metadata to the WebView

Add the `readingProgress` entry to the document hash maps: EPUB and commentary branches in `OsisDocument.asHashMap`, and the Bible branch in `BibleDocument.asHashMap`.

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/page/ClientPageObjects.kt` (`OsisDocument.asHashMap` ~lines 128-165; `BibleDocument.asHashMap` ~lines 176-205)

**Interfaces:**
- Consumes: `ReadingProgressInfo.forVerseKey`, `ReadingProgressInfo.forEpub` (Task 1); `Book.isEpub`, `Book.epubBackend`, `EpubBackend.maxOrdinal`, `EpubBackend.totalCharacters` (Task 2).
- Produces: a `"readingProgress"` key in both hash maps whose value is a JSON object (parsed client-side) or the literal `"null"`.

- [ ] **Step 1: Add imports to `ClientPageObjects.kt`**

Add (alongside the existing JSword/sword imports):

```kotlin
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.epub.epubBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
```

(`BookCategory` is already imported — it is used at line 133.)

- [ ] **Step 2: Build and emit `readingProgress` in `OsisDocument.asHashMap`**

In `OsisDocument.asHashMap`, after the `ordinalRange` block (after line 138, before `return mapOf(`), add:

```kotlin
        val readingProgress: String = when {
            book.isEpub -> book.epubBackend
                ?.let { ReadingProgressInfo.forEpub(it.maxOrdinal, it.totalCharacters).asJson }
                ?: "null"
            book.bookCategory == BookCategory.COMMENTARY && book is SwordBook -> {
                val vr = when (val k = key) {
                    is VerseRange -> k
                    is Verse -> VerseRange(k.versification, k, k)
                    else -> null
                }
                vr?.let {
                    ReadingProgressInfo.forVerseKey(book.versification, it.toV11n(book.versification)).asJson
                } ?: "null"
            }
            else -> "null"
        }
```

Then add this entry to the returned `mapOf(...)`, immediately after the `"ordinalRange" to ordinalRange,` line (line 144):

```kotlin
            "readingProgress" to readingProgress,
```

- [ ] **Step 3: Emit `readingProgress` in `BibleDocument.asHashMap`**

In `BibleDocument.asHashMap`, inside the `super.asHashMap.toMutableMap().apply { ... }` block, add after the existing `put("chapterReadCount", ...)` line (line 203). `vrInV11n` is already in scope (line 178):

```kotlin
            put("readingProgress", ReadingProgressInfo.forVerseKey(swordBook.versification, vrInV11n).asJson)
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileStandardGithubDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/control/page/ClientPageObjects.kt
git commit -m "Emit readingProgress metadata for EPUB, Bible and commentary documents"
```

---

### Task 4: `showReadingProgress` text display setting plumbing

Add the boolean setting end-to-end on the Android side (entity, migration, settings UI, strings, preference mapping). Mirrors the existing `showVerseNumbers`/`showOrdinals` settings exactly.

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt`
- Modify: `app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt`
- Modify: `app/src/main/res/xml/text_display_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/page/OptionsMenuItems.kt`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/TextDisplaySettings.kt`

**Interfaces:**
- Produces: enum `TextDisplaySettings.Types.SHOW_READING_PROGRESS`; entity field `showReadingProgress`; XML key `SHOW_READING_PROGRESS`; strings `prefs_show_reading_progress_title` / `_summary`.

- [ ] **Step 1: `WorkspaceEntities.kt` — field, enum, getValue, setValue, default**

Field: after line 220 (`var showOrdinals: Boolean? = null,`), before the closing `) {` at 221:

```kotlin
        @ColumnInfo(defaultValue = "NULL") var showReadingProgress: Boolean? = null,
```

Enum: after `ORDINALS,` (line 256):

```kotlin
            SHOW_READING_PROGRESS,
```

`getValue` when-block: after `Types.ORDINALS -> showOrdinals` (line 293):

```kotlin
            Types.SHOW_READING_PROGRESS -> showReadingProgress
```

`setValue` when-block: after `Types.ORDINALS -> showOrdinals = value as Boolean?` (line 331):

```kotlin
                Types.SHOW_READING_PROGRESS -> showReadingProgress = value as Boolean?
```

`default` companion val: after `showOrdinals = false,` (line 401):

```kotlin
                showReadingProgress = false,
```

- [ ] **Step 2: `WorkspacesMigrations.kt` — migration + array + version bump**

Add the migration after `addShowOrdinals` (after line 288), mirroring it:

```kotlin
private val addShowReadingProgress = makeMigration(22..23) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `PageManager` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
    _db.execSQL("ALTER TABLE `GlobalTextDisplaySettings` ADD COLUMN `text_display_settings_showReadingProgress` INTEGER DEFAULT NULL")
}
```

Add to the `workspacesMigrations` array after `addShowOrdinals,` (line 311):

```kotlin
    addShowReadingProgress,
```

Bump the version constant (line 314):

```kotlin
const val WORKSPACE_DATABASE_VERSION = 23
```

- [ ] **Step 3: `text_display_settings.xml` — switch entry**

Add after the `ORDINALS`/an existing `SwitchPreferenceCompat` block (mirror lines 141-145):

```xml
        <SwitchPreferenceCompat
            android:key="SHOW_READING_PROGRESS"
            android:title="@string/prefs_show_reading_progress_title"
            android:summary="@string/prefs_show_reading_progress_summary"
            />
```

- [ ] **Step 4: `strings.xml` — title + summary**

Add near the other `prefs_show_*` strings (e.g. after line 116):

```xml
    <string name="prefs_show_reading_progress_title">Reading progress</string>
    <string name="prefs_show_reading_progress_summary">Show how far through the book you are (percentage, page or chapter)</string>
```

- [ ] **Step 5: `OptionsMenuItems.kt` — title mapping (exhaustive `when`)**

Add to the `title` when-block after `TextDisplaySettings.Types.ORDINALS -> R.string.prefs_show_ordinals_title` (line 251):

```kotlin
                TextDisplaySettings.Types.SHOW_READING_PROGRESS -> R.string.prefs_show_reading_progress_title
```

(The `icon` when-block has an `else`, so no icon entry is required — it falls back to the default star icon.)

- [ ] **Step 6: `TextDisplaySettings.kt` — `getPrefItem` (exhaustive `when`)**

Add before the closing `}` of the `getPrefItem` when-block (after line 154):

```kotlin
        Types.SHOW_READING_PROGRESS -> ItemPreference(settings, Types.SHOW_READING_PROGRESS)
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew compileStandardGithubDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL. (Both `getValue`/`setValue`/`title`/`getPrefItem` are exhaustive `when`s — a missing entry fails compilation, so a clean build confirms all sites are wired.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/net/bible/android/database/WorkspaceEntities.kt \
        app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt \
        app/src/main/res/xml/text_display_settings.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/java/net/bible/android/view/activity/page/OptionsMenuItems.kt \
        app/src/main/java/net/bible/android/view/activity/settings/TextDisplaySettings.kt
git commit -m "Add showReadingProgress text display setting"
```

---

### Task 5: Vue config flag and document types

Add the `showReadingProgress` config key and the `DocumentReadingProgress` document field on the TypeScript side.

**Files:**
- Modify: `app/bibleview-js/src/composables/config.ts` (`Config` type ~line 92; `useConfig` defaults ~line 201)
- Modify: `app/bibleview-js/src/types/documents.ts` (`BaseOsisDocument` ~lines 60-80)

**Interfaces:**
- Produces: `Config.showReadingProgress: boolean`; type `DocumentReadingProgress`; `BaseOsisDocument.readingProgress: DocumentReadingProgress | null`.

- [ ] **Step 1: `config.ts` — add to `Config` type and defaults**

In the `Config` type, after `showPageNumber: boolean,` (line 92):

```ts
    showReadingProgress: boolean,
```

In the `useConfig()` reactive defaults, after `showPageNumber: false,` (line 201):

```ts
        showReadingProgress: false,
```

(Do NOT add it to `getNeedBookmarkRefresh` / `getNeedRefreshLocation` — like `showPageNumber`, the indicator does not change text layout, so it needs no refresh. It MUST be present in the reactive defaults, otherwise `setConfig` logs `"Unknown setting"` and drops it.)

- [ ] **Step 2: `documents.ts` — add the `DocumentReadingProgress` type and field**

Add the type near the other document types (e.g. after `BibleViewDocumentType` at line 33):

```ts
export type DocumentReadingProgress =
    | { kind: "bible", unitStart: number, unitEnd: number, chapterCount: number, currentChapter: number }
    | { kind: "book", unitStart: number, unitEnd: number, charCount: number }
```

Add the field to `BaseOsisDocument` (after `ordinalRange: OrdinalRange` at line 71). It is shared by both `OsisDocument` (EPUB/commentary) and `BibleDocumentType`:

```ts
    readingProgress: DocumentReadingProgress | null
```

- [ ] **Step 3: Verify types**

Run from `app/bibleview-js`:
`npm run type-check`
Expected: no new errors.

- [ ] **Step 4: Commit**

```bash
git add app/bibleview-js/src/composables/config.ts app/bibleview-js/src/types/documents.ts
git commit -m "Add showReadingProgress config and DocumentReadingProgress type"
```

---

### Task 6: Vue pure reading-progress logic + tests

Pure, DOM-free functions for the percentage/page/chapter math and current-document resolution. TDD.

**Files:**
- Create: `app/bibleview-js/src/composables/reading-progress.ts`
- Test: `app/bibleview-js/src/__tests__/reading-progress.spec.js`

**Interfaces:**
- Consumes: `DocumentReadingProgress` (Task 5).
- Produces:
  - `computePercent(ordinal, unitStart, unitEnd): number | null`
  - `estimateCharsPerPage(textLength, scrollHeight, pageHeight): number | null`
  - `computeTotalPages(charCount, charsPerPage): number`
  - `computeCurrentPage(percent, totalPages): number`
  - `layoutSignature(parts: (number|string)[]): string`
  - `resolveReadingProgress(documents, currentVerse): DocumentReadingProgress | null`

- [ ] **Step 1: Write the failing tests**

Create `app/bibleview-js/src/__tests__/reading-progress.spec.js`:

```js
import {describe, expect, it} from "vitest";
import {
    computePercent,
    estimateCharsPerPage,
    computeTotalPages,
    computeCurrentPage,
    layoutSignature,
    resolveReadingProgress,
} from "@/composables/reading-progress";

describe("computePercent", () => {
    it("is 0 at unit start and 100 at unit end", () => {
        expect(computePercent(100, 100, 300)).toBe(0);
        expect(computePercent(300, 100, 300)).toBe(100);
        expect(computePercent(200, 100, 300)).toBe(50);
    });
    it("clamps outside the range", () => {
        expect(computePercent(50, 100, 300)).toBe(0);
        expect(computePercent(400, 100, 300)).toBe(100);
    });
    it("returns null for a zero-length unit", () => {
        expect(computePercent(100, 100, 100)).toBeNull();
        expect(computePercent(100, 300, 100)).toBeNull();
    });
});

describe("estimateCharsPerPage", () => {
    it("scales text length by the page/scroll ratio", () => {
        // 6000 chars over 3000px tall content, 1000px viewport page => 2000 chars/page
        expect(estimateCharsPerPage(6000, 3000, 1000)).toBe(2000);
    });
    it("returns null for non-positive inputs", () => {
        expect(estimateCharsPerPage(0, 3000, 1000)).toBeNull();
        expect(estimateCharsPerPage(6000, 0, 1000)).toBeNull();
        expect(estimateCharsPerPage(6000, 3000, 0)).toBeNull();
    });
});

describe("computeTotalPages / computeCurrentPage", () => {
    it("rounds total pages and never goes below 1", () => {
        expect(computeTotalPages(540000, 1800)).toBe(300);
        expect(computeTotalPages(100, 1800)).toBe(1);
    });
    it("maps percent onto a 1..total page range", () => {
        expect(computeCurrentPage(0, 300)).toBe(1);
        expect(computeCurrentPage(100, 300)).toBe(300);
        expect(computeCurrentPage(50, 300)).toBe(150);
    });
});

describe("layoutSignature", () => {
    it("is stable for equal inputs and differs when a part changes", () => {
        expect(layoutSignature([16, "serif", 1000])).toBe(layoutSignature([16, "serif", 1000]));
        expect(layoutSignature([16, "serif", 1000])).not.toBe(layoutSignature([18, "serif", 1000]));
    });
});

describe("resolveReadingProgress", () => {
    const bookRp = {kind: "book", unitStart: 0, unitEnd: 1000, charCount: 50000};
    const genRp = {kind: "bible", unitStart: 0, unitEnd: 100, chapterCount: 50, currentChapter: 1};
    const exodRp = {kind: "bible", unitStart: 101, unitEnd: 200, chapterCount: 40, currentChapter: 1};

    it("returns null when no document has progress", () => {
        expect(resolveReadingProgress([{}], 5)).toBeNull();
        expect(resolveReadingProgress([], 5)).toBeNull();
    });
    it("picks the document whose ordinalRange contains currentVerse", () => {
        const docs = [
            {readingProgress: genRp, ordinalRange: [0, 100]},
            {readingProgress: exodRp, ordinalRange: [101, 200]},
        ];
        expect(resolveReadingProgress(docs, 150)).toBe(exodRp);
        expect(resolveReadingProgress(docs, 5)).toBe(genRp);
    });
    it("falls back to the first progress doc when currentVerse is null or unmatched", () => {
        const docs = [{readingProgress: bookRp, ordinalRange: [0, 1000]}];
        expect(resolveReadingProgress(docs, null)).toBe(bookRp);
        expect(resolveReadingProgress(docs, 99999)).toBe(bookRp);
    });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run from `app/bibleview-js`: `npm run test:ci -- reading-progress`
Expected: FAIL — cannot resolve `@/composables/reading-progress`.

- [ ] **Step 3: Write the implementation**

Create `app/bibleview-js/src/composables/reading-progress.ts`:

```ts
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

import {DocumentReadingProgress} from "@/types/documents";

/** Percentage (0..100) of the way from unitStart to unitEnd, or null for a degenerate unit. */
export function computePercent(ordinal: number, unitStart: number, unitEnd: number): number | null {
    if (unitEnd <= unitStart) return null;
    const p = (ordinal - unitStart) / (unitEnd - unitStart);
    return Math.min(1, Math.max(0, p)) * 100;
}

/** Empirically-measured characters per screenful, or null if inputs are not measurable yet. */
export function estimateCharsPerPage(textLength: number, scrollHeight: number, pageHeight: number): number | null {
    if (textLength <= 0 || scrollHeight <= 0 || pageHeight <= 0) return null;
    return textLength * pageHeight / scrollHeight;
}

export function computeTotalPages(charCount: number, charsPerPage: number): number {
    return Math.max(1, Math.round(charCount / charsPerPage));
}

export function computeCurrentPage(percent: number, totalPages: number): number {
    return Math.min(totalPages, Math.max(1, Math.round((percent / 100) * totalPages)));
}

export function layoutSignature(parts: (number | string)[]): string {
    return parts.join("|");
}

type ProgressDoc = { readingProgress?: DocumentReadingProgress | null; ordinalRange?: number[] };

/**
 * Resolve the reading-progress payload of the document the reader is currently in:
 * the one whose ordinalRange contains currentVerse, else the first document that has
 * progress metadata.
 */
export function resolveReadingProgress(
    documents: ProgressDoc[],
    currentVerse: number | null,
): DocumentReadingProgress | null {
    const withRp = documents.filter(d => !!d.readingProgress);
    if (withRp.length === 0) return null;
    if (currentVerse !== null) {
        const match = withRp.find(d =>
            !!d.ordinalRange && currentVerse >= d.ordinalRange[0] && currentVerse <= d.ordinalRange[1]);
        if (match) return match.readingProgress!;
    }
    return withRp[0].readingProgress!;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run from `app/bibleview-js`: `npm run test:ci -- reading-progress`
Expected: PASS (all groups).

- [ ] **Step 5: Lint**

Run from `app/bibleview-js`: `npm run lint`
Expected: no errors for the new file.

- [ ] **Step 6: Commit**

```bash
git add app/bibleview-js/src/composables/reading-progress.ts \
        app/bibleview-js/src/__tests__/reading-progress.spec.js
git commit -m "Add pure reading-progress computation helpers with tests"
```

---

### Task 7: `useReadingProgress` composable

Wire the pure helpers to live state: resolve the current document, measure chars-per-page once per layout signature, and produce the formatted display string.

**Files:**
- Create: `app/bibleview-js/src/composables/use-reading-progress.ts`

**Interfaces:**
- Consumes: Task 6 helpers; `Config` (Task 5); `DocumentReadingProgress` (Task 5); `sprintf` from `@/utils`. Formatting uses the three i18n keys via a local `ProgressStrings` param type, so this composable type-checks independently of Task 8's YAML.
- Produces: `useReadingProgress(config, documents, currentVerse, calculatedConfig, topElement, strings) : { progressText: ComputedRef<string | null> }`

- [ ] **Step 1: Write the composable**

Create `app/bibleview-js/src/composables/use-reading-progress.ts`:

```ts
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

import {computed, ComputedRef, nextTick, onMounted, ref, Ref, watch} from "vue";
import {Config} from "@/composables/config";
import {DocumentReadingProgress} from "@/types/documents";
import {sprintf} from "@/utils";
import {
    computeCurrentPage,
    computePercent,
    computeTotalPages,
    estimateCharsPerPage,
    layoutSignature,
    resolveReadingProgress,
} from "@/composables/reading-progress";

// Minimum rendered text length before we trust a chars-per-page measurement.
const MIN_TEXT_FOR_MEASURE = 400;

type LayoutConfig = { value: { pageHeight: number; marginLeft: number; marginRight: number } };
type ProgressDoc = { readingProgress?: DocumentReadingProgress | null; ordinalRange?: number[] };
type ProgressStrings = {
    readingProgressPercent: string;
    readingProgressPage: string;
    readingProgressChapter: string;
};

export function useReadingProgress(
    config: Config,
    documents: ProgressDoc[],
    currentVerse: Ref<number | null>,
    calculatedConfig: LayoutConfig,
    topElement: Ref<HTMLElement | null>,
    strings: ProgressStrings,
): { progressText: ComputedRef<string | null> } {
    const charsPerPageCache = new Map<string, number>();
    const charsPerPage = ref<number | null>(null);

    function signature(): string {
        const cc = calculatedConfig.value;
        return layoutSignature([config.fontSize, config.fontFamily, cc.marginLeft, cc.marginRight, cc.pageHeight, window.innerWidth]);
    }

    function remeasure(): void {
        const sig = signature();
        const cached = charsPerPageCache.get(sig);
        if (cached !== undefined) {
            charsPerPage.value = cached;
            return;
        }
        const el = topElement.value;
        if (!el) return;
        const textLength = el.textContent?.length ?? 0;
        if (textLength < MIN_TEXT_FOR_MEASURE) return;
        const cpp = estimateCharsPerPage(textLength, el.scrollHeight, calculatedConfig.value.pageHeight);
        if (cpp !== null) {
            charsPerPageCache.set(sig, cpp);
            charsPerPage.value = cpp;
        }
    }

    // Re-measure when the layout signature changes (font size, margins, viewport, rotation).
    watch(() => signature(), () => {
        charsPerPage.value = charsPerPageCache.get(signature()) ?? null;
        nextTick(remeasure);
    });
    // Re-measure as content loads (infinite scroll appends documents).
    watch(() => documents.length, () => nextTick(remeasure));
    onMounted(() => nextTick(remeasure));

    const progressText = computed<string | null>(() => {
        const rp = resolveReadingProgress(documents, currentVerse.value);
        if (!rp) return null;
        const ordinal = currentVerse.value ?? rp.unitStart;
        const percent = computePercent(ordinal, rp.unitStart, rp.unitEnd);
        if (percent === null) return null;
        const pct = Math.round(percent);

        if (rp.kind === "bible") {
            return sprintf(strings.readingProgressChapter, pct, rp.currentChapter, rp.chapterCount);
        }
        // kind === "book" (EPUB / general book)
        if (charsPerPage.value === null) {
            return sprintf(strings.readingProgressPercent, pct);
        }
        const totalPages = computeTotalPages(rp.charCount, charsPerPage.value);
        const page = computeCurrentPage(percent, totalPages);
        return sprintf(strings.readingProgressPage, pct, page, totalPages);
    });

    return {progressText};
}
```

- [ ] **Step 2: Verify types and lint**

Run from `app/bibleview-js`: `npm run type-check && npm run lint`
Expected: no errors. (Formatting uses the local `ProgressStrings` param type, so this is independent of Task 8's YAML.)

- [ ] **Step 3: Commit**

```bash
git add app/bibleview-js/src/composables/use-reading-progress.ts
git commit -m "Add useReadingProgress composable with layout-cached page sizing"
```

---

### Task 8: `ReadingProgress.vue` overlay + BibleView wiring

Add the i18n strings, a dumb presentational component, its test, and wire the composable + component into `BibleView.vue` with `.pagenumber`-style CSS.

**Files:**
- Modify: `app/bibleview-js/src/lang/default.yaml`
- Create: `app/bibleview-js/src/components/ReadingProgress.vue`
- Test: `app/bibleview-js/src/__tests__/reading-progress-component.spec.js`
- Modify: `app/bibleview-js/src/components/BibleView.vue`

**Interfaces:**
- Consumes: `useReadingProgress` (Task 7); `config.showReadingProgress` (Task 5).
- Produces: `<ReadingProgress :text="..."/>` rendering a `.reading-progress` overlay when `text` is non-null.

- [ ] **Step 1: Add i18n strings to `default.yaml`**

Add near the other reading/progress strings (around lines 75-80). Note sprintf-js needs `%%` for a literal percent sign:

```yaml
readingProgressPercent: "%s%%"
readingProgressPage: "%s%% · page %s/~%s"
readingProgressChapter: "%s%% · ch %s/%s"
```

- [ ] **Step 2: Write the failing component test**

Create `app/bibleview-js/src/__tests__/reading-progress-component.spec.js`:

```js
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

import {shallowMount} from "@vue/test-utils";
import {describe, it, expect} from "vitest";
import ReadingProgress from "@/components/ReadingProgress.vue";

describe("ReadingProgress.vue", () => {
    it("renders the text when provided", () => {
        const wrapper = shallowMount(ReadingProgress, {props: {text: "47% · page 142/~300"}});
        expect(wrapper.find(".reading-progress").exists()).toBe(true);
        expect(wrapper.text()).toContain("47% · page 142/~300");
    });
    it("renders nothing when text is null", () => {
        const wrapper = shallowMount(ReadingProgress, {props: {text: null}});
        expect(wrapper.find(".reading-progress").exists()).toBe(false);
    });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run from `app/bibleview-js`: `npm run test:ci -- reading-progress-component`
Expected: FAIL — cannot resolve `@/components/ReadingProgress.vue`.

- [ ] **Step 4: Write the component**

Create `app/bibleview-js/src/components/ReadingProgress.vue`:

```vue
<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div v-if="text" class="reading-progress">
    <div class="reading-progress-text">{{ text }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ text: string | null }>();
</script>

<style lang="scss">
.reading-progress {
  z-index: 5;
  position: fixed;
  right: 2mm;
  bottom: 0;
  // Stacked one row above where the page-number overlay sits (it is 0.5cm tall at
  // margin-bottom 2mm), so the two never overlap when both are enabled.
  margin-bottom: calc(2mm + 0.6cm);
  padding: 0 2mm;
  height: 0.5cm;
  display: flex;
  align-items: center;
  font-size: 70%;
  font-weight: bold;
  color: var(--text-color);
  background: rgba(207, 207, 207, 0.71);
  border-radius: 0.5cm;
  .noAnimation & {
    background-color: var(--background-color);
    border: 1px solid var(--text-color);
  }
}
</style>
```

- [ ] **Step 5: Run the test to verify it passes**

Run from `app/bibleview-js`: `npm run test:ci -- reading-progress-component`
Expected: PASS (2 tests).

- [ ] **Step 6: Wire into `BibleView.vue`**

Add the imports (with the other component/composable imports near lines 130-152):

```ts
import ReadingProgress from "@/components/ReadingProgress.vue";
import {useReadingProgress} from "@/composables/use-reading-progress";
```

Instantiate the composable after `currentVerse` is available (after line 222). `config`, `documents`, `calculatedConfig`, `topElement`, `strings` are all already defined above in the file:

```ts
const {progressText} = useReadingProgress(config, documents, currentVerse, calculatedConfig, topElement, strings);
```

Add the overlay in the template right after the `.pagenumber` block (after line 73):

```html
    <ReadingProgress v-if="config.showReadingProgress" :text="progressText"/>
```

(The overlay's vertical stacking offset — `margin-bottom: calc(2mm + 0.6cm)` set in Step 4 — keeps it one row above the page-number overlay, so the two never overlap when both settings are on. No further CSS is needed here.)

- [ ] **Step 7: Validate Vue**

Run from `app/bibleview-js`: `npm run test:ci && npm run lint && npm run type-check`
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add app/bibleview-js/src/lang/default.yaml \
        app/bibleview-js/src/components/ReadingProgress.vue \
        app/bibleview-js/src/__tests__/reading-progress-component.spec.js \
        app/bibleview-js/src/components/BibleView.vue
git commit -m "Add reading-progress overlay and wire it into BibleView"
```

---

## Manual verification (after all tasks)

Build the app, then on a device:

1. Enable **Reading progress** in text display settings.
2. Open an **EPUB** book: indicator shows `NN% · page X/~Y`; `Y` stays constant while scrolling; `X` advances smoothly; changing font size re-derives `Y` once.
3. Open a **Bible** and a **commentary**: indicator shows `NN% · ch X/Y` for the current book; crossing a book boundary (infinite scroll) updates the unit.
4. Open a **dictionary**: indicator is hidden.
5. Check dark, light, monochrome/e-ink, and no-animations modes: overlay is text-only and readable; does not overlap the page-number overlay when both are enabled.

Build commands (`dangerouslyDisableSandbox: true`):
```bash
cd app/bibleview-js && npm run build-debug && cd ../..
./gradlew assembleStandardGithubDebug
```

---

## Self-Review notes (spec coverage)

- Type-aware indicator (EPUB/Bible/commentary; others hidden): Tasks 1, 3, 6, 7.
- Percentage from monotonic ordinals: `computePercent` (Task 6), wired in Task 7.
- EPUB stored char count (lazy, no forced re-optimization) + max ordinal: Task 2.
- Stable page count via layout-signature-cached empirical chars-per-page: Task 7 (`remeasure`, `signature`, cache) + `estimateCharsPerPage`/`computeTotalPages`/`computeCurrentPage` (Task 6).
- `showReadingProgress` setting (default off) end-to-end: Task 4 (Android) + Task 5 (Vue).
- Bottom-corner text-only overlay, monochrome-safe, no overlap with page number: Task 8.
- Tests: pure Kotlin helper (Task 1), pure Vue logic (Task 6), component (Task 8).
- Edge cases (0%/100% clamp, zero-length unit hidden, page omitted until measured, single-chapter book): covered by `computePercent`/`resolveReadingProgress` tests (Task 6) and the `charsPerPage === null` branch (Task 7).
