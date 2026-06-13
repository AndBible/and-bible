# Commentary Block-Based Navigation + Infinite Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make commentaries navigate and infinite-scroll by *content block* (a run of consecutive verses sharing identical rendered content) instead of by single verse, so the same text never repeats and empty verses are skipped.

**Architecture:** A new `CommentaryBlockResolver` (driven by an injectable `CommentaryWalker`) computes block boundaries lazily by comparing the *plain-text* rendering of neighbouring verses. The canonical page key stays a single `Verse` (block start); the resolved verse range is passed to the Vue side as display-only metadata. `CurrentCommentaryPage.next()/previous()` and `BibleView`'s infinite-scroll feed both use the resolver. Vue enables infinite scroll for `COMMENTARY` and renders the range header.

**Tech Stack:** Kotlin (Android, JSword), JUnit (`kotlin.test`-style with JUnit asserts as used in existing tests), Vue 3 + TypeScript, Vitest.

Spec: `docs/superpowers/specs/2026-06-13-commentary-block-navigation-design.md`

---

## File Structure

**Phase 1 — shared render primitive (1 file moved, 7 import updates)**
- Move `OsisToPlainText` from `net.bible.service.llm.tools` to `net.bible.service.sword` so a sword-layer resolver can reuse it without a package cycle.

**Phase 2 — Kotlin core (≤5 files)**
- Create `CommentaryBlockResolver.kt` (`net.bible.service.sword`): render primitives, walker interface, resolver, production walker.
- Modify `GetCommentariesTool.kt`: reuse the shared render primitive.
- Modify `CurrentCommentaryPage.kt`: block-aware `next()/previous()` + public block-key helpers.
- Modify `CurrentPageBase.kt` + `ClientPageObjects.kt`: carry `commentaryRange` on `OsisDocument`.

**Phase 3 — infinite-scroll feed (1 file)**
- Modify `BibleView.kt`: commentary branch in `requestMoreToBeginning/End`.

**Phase 4 — Vue (3 files)**
- Modify `documents.ts`: `commentaryRange` type.
- Modify `infinite-scroll.ts`: enable `COMMENTARY`; extract a testable pure function.
- Modify `OsisDocument.vue`: render the range header.

Run Vue checks from `app/bibleview-js`. Run Kotlin tests with `./gradlew testStandardGoogleplayDebugUnitTest --tests "..."` (requires `dangerouslyDisableSandbox: true`).

---

## Phase 1 — Move `OsisToPlainText` to the sword layer

### Task 1: Move `OsisToPlainText` to `net.bible.service.sword`

**Files:**
- Modify: `app/src/main/java/net/bible/service/llm/tools/OsisToPlainText.kt` (package declaration only)
- Modify imports in: `AgentExecutor.kt`, `GetGenBookContentTool.kt`, `GetVerseContentTool.kt`, `GetCommentariesTool.kt`, `GetDictionaryEntryTool.kt`, and the test `OsisToPlainTextTest.kt`

This is a mechanical, behaviour-preserving move. Keep the file path the same is *not* possible (Kotlin allows mismatched dir/package, but we keep it clean): physically move the file too.

- [ ] **Step 1: Move the file and change its package**

```bash
git mv app/src/main/java/net/bible/service/llm/tools/OsisToPlainText.kt \
       app/src/main/java/net/bible/service/sword/OsisToPlainText.kt
```

Then edit the package line in `app/src/main/java/net/bible/service/sword/OsisToPlainText.kt`:

```kotlin
// OLD
package net.bible.service.llm.tools
// NEW
package net.bible.service.sword
```

- [ ] **Step 2: Update all importers**

In each of these files, replace the import line
`import net.bible.service.llm.tools.OsisToPlainText`
with
`import net.bible.service.sword.OsisToPlainText`:

- `app/src/main/java/net/bible/service/llm/agent/AgentExecutor.kt`
- `app/src/main/java/net/bible/service/llm/tools/read/GetGenBookContentTool.kt`
- `app/src/main/java/net/bible/service/llm/tools/read/GetVerseContentTool.kt`
- `app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt`
- `app/src/main/java/net/bible/service/llm/tools/read/GetDictionaryEntryTool.kt`
- `app/src/test/java/net/bible/service/llm/tools/OsisToPlainTextTest.kt`

Note: files in the same `net.bible.service.llm.tools.read` package that referenced `OsisToPlainText` via the parent package import must now use the explicit `net.bible.service.sword` import. Verify with grep after editing (Step 3).

- [ ] **Step 3: Verify no stale references remain**

Run: `grep -rn "llm.tools.OsisToPlainText\|llm\.tools\b.*OsisToPlainText" app/src`
Expected: no matches.
Run: `grep -rln "OsisToPlainText" app/src` and confirm every hit either is the moved file or imports `net.bible.service.sword.OsisToPlainText`.

- [ ] **Step 4: Compile + run the moved test**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.OsisToPlainTextTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Move OsisToPlainText to service.sword package

It is a generic OSIS->plaintext utility, not LLM-specific. Moving it to
the sword layer lets the upcoming CommentaryBlockResolver reuse it without
creating a service.sword <-> service.llm.tools package cycle."
```

---

## Phase 2 — Kotlin core

### Task 2: Create `CommentaryBlockResolver` with tests

**Files:**
- Create: `app/src/main/java/net/bible/android/control/page/CommentaryBlockResolver.kt`
- Test: `app/src/test/java/net/bible/android/control/page/CommentaryBlockResolverTest.kt`

The resolver lives in `control.page` (its only consumers are `CurrentCommentaryPage` and `CurrentPageBase`, both here). It depends on `net.bible.service.sword.OsisToPlainText` and `SwordContentFacade` — both lower layers, no cycle.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/android/control/page/CommentaryBlockResolverTest.kt`:

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
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentaryBlockResolverTest {
    private val v11n = Versifications.instance().getVersification("KJV")

    /** Build consecutive John 1 verses 1..count. */
    private fun johnVerses(count: Int): List<Verse> =
        (1..count).map { Verse(v11n, BibleBook.JOHN, 1, it) }

    /** Fake walker: ordered verse list + content map; index-based traversal. */
    private class FakeWalker(
        private val verses: List<Verse>,
        private val content: Map<Verse, String?>,
    ) : CommentaryWalker {
        override fun next(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i in 0 until verses.lastIndex) verses[i + 1] else null
        }
        override fun prev(verse: Verse): Verse? {
            val i = verses.indexOf(verse)
            return if (i > 0) verses[i - 1] else null
        }
        override fun render(verse: Verse): String? = content[verse]
    }

    private fun resolver(verses: List<Verse>, content: Map<Verse, String?>) =
        CommentaryBlockResolver(FakeWalker(verses, content))

    @Test
    fun `resolveBlock expands from the middle to start and end`() {
        val vs = johnVerses(7)
        // verses 1..5 share "A"; 6 is empty; 7 is "B"
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val block = resolver(vs, content).resolveBlock(vs[2]) // start from John 1:3
        assertEquals(vs[0], block.start)
        assertEquals(vs[4], block.end)
        assertEquals("A", block.content)
    }

    @Test
    fun `resolveBlock on an empty verse returns that verse with null content`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to null, vs[2] to "B")
        val block = resolver(vs, content).resolveBlock(vs[1])
        assertEquals(vs[1], block.start)
        assertEquals(vs[1], block.end)
        assertNull(block.content)
    }

    @Test
    fun `nextBlockStart skips empty verses to the next non-empty block`() {
        val vs = johnVerses(7)
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val r = resolver(vs, content)
        // current block ends at verse 5 (index 4)
        assertEquals(vs[6], r.nextBlockStart(vs[4]))
    }

    @Test
    fun `prevBlockStart returns the start of the previous block`() {
        val vs = johnVerses(7)
        val content = mapOf(
            vs[0] to "A", vs[1] to "A", vs[2] to "A", vs[3] to "A", vs[4] to "A",
            vs[5] to null, vs[6] to "B",
        )
        val r = resolver(vs, content)
        // before block starting at verse 7 (index 6) → previous block starts at verse 1
        assertEquals(vs[0], r.prevBlockStart(vs[6]))
    }

    @Test
    fun `single-verse blocks resolve to themselves`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        val r = resolver(vs, content)
        val block = r.resolveBlock(vs[1])
        assertEquals(vs[1], block.start)
        assertEquals(vs[1], block.end)
        assertEquals(vs[2], r.nextBlockStart(vs[1]))
        assertEquals(vs[0], r.prevBlockStart(vs[1]))
    }

    @Test
    fun `nextBlockStart returns null at the end`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        assertNull(resolver(vs, content).nextBlockStart(vs[2]))
    }

    @Test
    fun `prevBlockStart returns null at the start`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to "A", vs[1] to "B", vs[2] to "C")
        assertNull(resolver(vs, content).prevBlockStart(vs[0]))
    }

    @Test
    fun `all-empty input yields no navigation targets`() {
        val vs = johnVerses(3)
        val content = mapOf(vs[0] to null, vs[1] to null, vs[2] to null)
        val r = resolver(vs, content)
        assertNull(r.nextBlockStart(vs[0]))
        assertNull(r.prevBlockStart(vs[2]))
    }

    @Test
    fun `identical content across a chapter boundary collapses into one block`() {
        // Regression for OSTicket #3303: John 1:7 + John 2:1 share content "X"
        val v1 = Verse(v11n, BibleBook.JOHN, 1, 7)
        val v2 = Verse(v11n, BibleBook.JOHN, 2, 1)
        val v3 = Verse(v11n, BibleBook.JOHN, 2, 2)
        val vs = listOf(v1, v2, v3)
        val content = mapOf(v1 to "X", v2 to "X", v3 to "Y")
        val block = resolver(vs, content).resolveBlock(v2)
        assertEquals(v1, block.start)
        assertEquals(v2, block.end)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.CommentaryBlockResolverTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — compilation error, `CommentaryBlockResolver` / `CommentaryWalker` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/net/bible/android/control/page/CommentaryBlockResolver.kt`:

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

import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.useSaxBuilder
import net.bible.service.sword.OsisToPlainText
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.StringReader

/**
 * Renders a commentary entry's raw OSIS XML for [key], or null when the entry is empty
 * (blank or `<div/>`). Shared by [GetCommentariesTool][net.bible.service.llm.tools.read.GetCommentariesTool]
 * and [renderComparable] so the read+blank-check is defined once.
 */
fun renderCommentaryFragmentXml(book: Book, key: Key): String? {
    val outputter = XMLOutputter(Format.getRawFormat())
    val xml = outputter.outputString(SwordContentFacade.readOsisFragment(book, key))
    return if (xml.isBlank() || xml == "<div/>") null else xml
}

/**
 * Produces a stable comparison key for a commentary verse: the entry rendered to plain text,
 * or null if empty. Plain text (not raw XML) is compared so that entries which are semantically
 * identical but differ only in per-verse OSIS metadata (e.g. across a chapter boundary) still
 * collapse into one block — mirroring the dedup behaviour in GetCommentariesTool.
 */
fun renderComparable(book: Book, verse: Verse): String? {
    val xml = renderCommentaryFragmentXml(book, verse) ?: return null
    val text = OsisToPlainText.convert(useSaxBuilder { it.build(StringReader(xml)).rootElement }).trim()
    return text.ifBlank { null }
}

/** A run of consecutive verses sharing identical rendered content. [content] is null for an empty verse. */
data class CommentaryBlock(val start: Verse, val end: Verse, val content: String?)

/** Abstracts verse traversal + rendering so the resolver can be unit-tested without a real book. */
interface CommentaryWalker {
    /** Next verse, or null at the end of the traversable range. */
    fun next(verse: Verse): Verse?
    /** Previous verse, or null at the start of the traversable range. */
    fun prev(verse: Verse): Verse?
    /** Comparison content for [verse], or null when it has no commentary entry. */
    fun render(verse: Verse): String?
}

/**
 * Resolves commentary content blocks lazily. Empty verses act as block separators and are
 * skipped during navigation. A small per-resolver cache avoids re-rendering a verse touched
 * by more than one walk; callers create a fresh resolver per navigation action.
 */
class CommentaryBlockResolver(private val walker: CommentaryWalker) {
    private val cache = HashMap<Verse, String?>()
    private fun render(verse: Verse): String? = cache.getOrPut(verse) { walker.render(verse) }

    /**
     * Expands the block containing [verse]. If [verse] itself is empty, returns a single-verse
     * block with null content (no snapping to a neighbouring block).
     */
    fun resolveBlock(verse: Verse): CommentaryBlock {
        val content = render(verse) ?: return CommentaryBlock(verse, verse, null)
        var start = verse
        while (true) {
            val p = walker.prev(start) ?: break
            if (render(p) == content) start = p else break
        }
        var end = verse
        while (true) {
            val n = walker.next(end) ?: break
            if (render(n) == content) end = n else break
        }
        return CommentaryBlock(start, end, content)
    }

    /** Start verse of the next non-empty block after [blockEnd], or null at the end. */
    fun nextBlockStart(blockEnd: Verse): Verse? {
        var v = blockEnd
        while (true) {
            v = walker.next(v) ?: return null
            if (render(v) != null) return v
        }
    }

    /** Start verse of the previous non-empty block before [blockStart], or null at the start. */
    fun prevBlockStart(blockStart: Verse): Verse? {
        var v = blockStart
        while (true) {
            v = walker.prev(v) ?: return null
            if (render(v) != null) return resolveBlock(v).start
        }
    }
}

/** Production walker backed by JSword traversal + plain-text rendering. */
class SwordCommentaryWalker(
    private val book: AbstractPassageBook,
    private val bibleTraverser: BibleTraverser,
) : CommentaryWalker {
    override fun next(verse: Verse): Verse? =
        try { bibleTraverser.getNextVerse(book, verse).takeIf { it != verse } } catch (e: Exception) { null }
    override fun prev(verse: Verse): Verse? =
        try { bibleTraverser.getPrevVerse(book, verse).takeIf { it != verse } } catch (e: Exception) { null }
    override fun render(verse: Verse): String? = renderComparable(book, verse)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.CommentaryBlockResolverTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (all 9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/control/page/CommentaryBlockResolver.kt \
        app/src/test/java/net/bible/android/control/page/CommentaryBlockResolverTest.kt
git commit -m "Add CommentaryBlockResolver for content-block boundary detection

Lazy, incremental resolver that merges consecutive commentary verses with
identical plain-text rendering into a block and skips empty verses. Driven
by an injectable CommentaryWalker so it is unit-testable; SwordCommentaryWalker
is the JSword-backed production impl."
```

---

### Task 3: Reuse the shared render primitive in `GetCommentariesTool`

**Files:**
- Modify: `app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt:228-245`

Behaviour-preserving: the tool keeps comparing its final rendered content (text or XML); only the read + blank-check is delegated to the shared `renderCommentaryFragmentXml`.

- [ ] **Step 1: Replace the inline render with the shared primitive**

In `GetCommentariesTool.kt`, the current block (around lines 228-245) reads:

```kotlin
                val useXml = args.format == ContentFormat.XML
                val renderedVerses = verses.map { verse ->
                    val content = try {
                        val fragment = SwordContentFacade.readOsisFragment(commentary, verse)
                        val xml = outputter.outputString(fragment)
                        when {
                            xml.isBlank() || xml == "<div/>" -> null
                            useXml -> xml
                            else -> OsisToPlainText.convert(
                                useSaxBuilder { it.build(StringReader(xml)).rootElement },
                                injectAnchors = true
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                    RenderedVerse(verse.osisID, content)
                }
```

Replace it with:

```kotlin
                val useXml = args.format == ContentFormat.XML
                val renderedVerses = verses.map { verse ->
                    val content = try {
                        val xml = renderCommentaryFragmentXml(commentary, verse)
                        when {
                            xml == null -> null
                            useXml -> xml
                            else -> OsisToPlainText.convert(
                                useSaxBuilder { it.build(StringReader(xml)).rootElement },
                                injectAnchors = true
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                    RenderedVerse(verse.osisID, content)
                }
```

Add the import near the other `net.bible.service.sword` imports:

```kotlin
import net.bible.android.control.page.renderCommentaryFragmentXml
```

The local `val outputter = XMLOutputter(Format.getRawFormat())` (around line 213) may now be unused — if so, remove it and the now-unused `Format`/`XMLOutputter` imports. Verify usage first: `grep -n "outputter\|XMLOutputter\|Format\." app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt`. Remove only if there are no remaining references.

- [ ] **Step 2: Run the existing dedup test to verify behaviour is preserved**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.GetCommentariesDedupTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (unchanged — it tests `deduplicateConsecutiveBlocks` directly).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt
git commit -m "Reuse shared renderCommentaryFragmentXml in GetCommentariesTool

Delegates the read + blank-check to the shared primitive so the
read-and-dedup logic is defined in one place. Final-content comparison
(text or XML) is unchanged."
```

---

### Task 4: Block-aware navigation in `CurrentCommentaryPage`

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/page/CurrentCommentaryPage.kt:118-139`

- [ ] **Step 1: Add block helpers and rewrite next()/previous()**

In `CurrentCommentaryPage.kt`, add imports:

```kotlin
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.basic.AbstractPassageBook
```

Replace the existing `next()`, `previous()`, `nextVerse()`, `previousVerse()` block (lines 118-139) with:

```kotlin
    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
    override fun next() {
        Log.i(TAG, "Next")
        if (!navigateByBlock(forward = true)) nextVerse()
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
    override fun previous() {
        Log.i(TAG, "Previous")
        if (!navigateByBlock(forward = false)) previousVerse()
    }

    private fun nextVerse() {
        originalVerseRange = null
        setKey(getKeyPlus(1))
    }

    private fun previousVerse() {
        originalVerseRange = null
        setKey(getKeyPlus(-1))
    }

    /** A resolver for the current commentary document, or null for non-commentary / special docs. */
    private fun blockResolver(): CommentaryBlockResolver? {
        val book = currentDocument as? AbstractPassageBook ?: return null
        if (book.bookCategory != BookCategory.COMMENTARY) return null
        return CommentaryBlockResolver(SwordCommentaryWalker(book, bibleTraverser))
    }

    /**
     * Navigates to the next/previous content block. Returns false when block navigation does not
     * apply (special document or non-commentary), so the caller falls back to verse navigation.
     * Returns true (handled) even at a boundary, where it stays put.
     */
    private fun navigateByBlock(forward: Boolean): Boolean {
        val resolver = blockResolver() ?: return false
        val currentVerse = currentBibleVerse.getVerseSelected(versification)
        val block = resolver.resolveBlock(currentVerse)
        val target = if (forward) resolver.nextBlockStart(block.end) else resolver.prevBlockStart(block.start)
        if (target != null) {
            originalVerseRange = null
            setKey(target)
        }
        return true
    }

    /** Start verse of the next block after the block containing [verse], or null at the end. */
    fun nextBlockStart(verse: Verse): Verse? {
        val resolver = blockResolver() ?: return null
        return resolver.nextBlockStart(resolver.resolveBlock(verse).end)
    }

    /** Start verse of the previous block before the block containing [verse], or null at the start. */
    fun prevBlockStart(verse: Verse): Verse? {
        val resolver = blockResolver() ?: return null
        return resolver.prevBlockStart(resolver.resolveBlock(verse).start)
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/android/control/page/CurrentCommentaryPage.kt
git commit -m "Make CurrentCommentaryPage next()/previous() block-aware

next/previous now jump to the next/previous distinct content block (skipping
empty verses) via CommentaryBlockResolver. Falls back to verse navigation for
special documents (compare/memorize). Adds public nextBlockStart/prevBlockStart
helpers used by the infinite-scroll feed."
```

---

### Task 5: Carry `commentaryRange` on `OsisDocument`

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/page/ClientPageObjects.kt:107-156`
- Modify: `app/src/main/java/net/bible/android/control/page/CurrentPageBase.kt:148-199`
- Modify: `app/src/main/java/net/bible/android/control/page/CurrentCommentaryPage.kt`

- [ ] **Step 1: Add the `CommentaryRangeInfo` type and field to `OsisDocument`**

In `ClientPageObjects.kt`, just above `open class OsisDocument(` (line 107), add:

```kotlin
data class CommentaryRangeInfo(val startOsisRef: String, val endOsisRef: String, val name: String) {
    val asJson: String get() = mapToJson(mapOf(
        "startOsisRef" to wrapString(startOsisRef),
        "endOsisRef" to wrapString(endOsisRef),
        "name" to wrapString(name),
    ))
}
```

Add the constructor parameter to `OsisDocument` (after `aiDocMarkers`, before the closing `)`):

```kotlin
    open val aiDocMarkers: List<AiDocMarkerInfo> = emptyList(),
    val commentaryRange: CommentaryRangeInfo? = null,
): Document {
```

Add to the `asHashMap` map (after the `aiDocMarkers` entry, before the closing `)`):

```kotlin
            "commentaryRange" to (commentaryRange?.asJson ?: "null"),
```

- [ ] **Step 2: Add the open hook in `CurrentPageBase` and pass it through**

In `CurrentPageBase.kt`, add a default-null open hook (place it just before `getPageContent`, around line 147):

```kotlin
    /** Subclasses (commentary) may supply a verse-range descriptor shown by the Vue side. */
    protected open fun commentaryRangeFor(key: Key): CommentaryRangeInfo? = null
```

In the `OsisDocument(...)` construction inside `getPageContent` (around line 181-191), add the argument:

```kotlin
            aiDocMarkers = aiDocMarkers,
            commentaryRange = commentaryRangeFor(key),
        )
```

- [ ] **Step 3: Override the hook in `CurrentCommentaryPage`**

In `CurrentCommentaryPage.kt`, add imports if not already present:

```kotlin
import net.bible.android.control.page.CommentaryRangeInfo
```
(same package, so no import needed — skip if it resolves) and `import org.crosswire.jsword.passage.VerseRange` already exists.

Add the override (place near `nextBlockStart`/`prevBlockStart`):

```kotlin
    override fun commentaryRangeFor(key: Key): CommentaryRangeInfo? {
        val resolver = blockResolver() ?: return null
        val verse = KeyUtil.getVerse(key)
        val block = resolver.resolveBlock(verse)
        if (block.content == null) return null
        val name = if (block.start == block.end) block.start.name
            else VerseRange(versification, block.start, block.end).name
        return CommentaryRangeInfo(block.start.osisRef, block.end.osisRef, name)
    }
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/control/page/ClientPageObjects.kt \
        app/src/main/java/net/bible/android/control/page/CurrentPageBase.kt \
        app/src/main/java/net/bible/android/control/page/CurrentCommentaryPage.kt
git commit -m "Pass commentary block verse-range to the Vue side

OsisDocument gains an optional commentaryRange (start/end osisRef + display
name). CurrentPageBase exposes an open commentaryRangeFor() hook;
CurrentCommentaryPage computes the block range via the resolver."
```

---

## Phase 3 — Infinite-scroll feed

### Task 6: Commentary branch in `BibleView.requestMoreToBeginning/End`

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt:2196-2266`

- [ ] **Step 1: Add a commentary branch to `requestMoreToBeginning`**

In `requestMoreToBeginning` (line 2196), the body currently is `if (isBible) { ... } else { ...general book... }`. Change it to insert a commentary branch between them. Replace the `else {` that starts the general-book branch (line 2211) so the structure becomes:

```kotlin
        } else if (isCommentary) {
            val currentPage = window.pageManager.currentCommentary
            val first = firstKey as? Verse ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            val prevStart = currentPage.prevBlockStart(first) ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            firstKey = prevStart
            chapterLoadJobs += scope.launch(Dispatchers.IO) {
                val doc = currentPage.getPageContent(prevStart)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        } else {
```

(The existing general-book branch stays as the final `else`.)

- [ ] **Step 2: Add a commentary branch to `requestMoreToEnd`**

In `requestMoreToEnd` (line 2232), similarly insert between the `isBible` branch and the general-book `else`:

```kotlin
        } else if (isCommentary) {
            val currentPage = window.pageManager.currentCommentary
            val last = lastKey as? Verse ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            val nextStart = currentPage.nextBlockStart(last) ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            lastKey = nextStart
            chapterLoadJobs += scope.launch(Dispatchers.IO) {
                val doc = currentPage.getPageContent(nextStart)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        } else {
```

Note: `firstKey`/`lastKey` are initialised by the `initialKey` setter (line 1467-1470) to the page's verse, which for commentaries is the first block's start — correct starting point for both directions.

- [ ] **Step 3: Compile**

Run: `./gradlew compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/page/BibleView.kt
git commit -m "Feed commentary content blocks to infinite scroll

requestMoreToBeginning/End gain a commentary branch that walks to the
previous/next content block via CurrentCommentaryPage.prev/nextBlockStart and
returns the block's page content, or null at a boundary."
```

---

## Phase 4 — Vue

### Task 7: Add the `commentaryRange` type

**Files:**
- Modify: `app/bibleview-js/src/types/documents.ts:54-79`

- [ ] **Step 1: Add the type and field**

In `documents.ts`, just above `interface BaseOsisDocument` (line 54), add:

```typescript
export interface CommentaryRange {
    startOsisRef: string
    endOsisRef: string
    name: string
}
```

In `OsisDocument` (line 76-79), add the field:

```typescript
export interface OsisDocument extends BaseOsisDocument {
    type: "osis",
    highlightedOrdinalRange: Nullable<OrdinalRange>
    commentaryRange: Nullable<CommentaryRange>
}
```

- [ ] **Step 2: Type-check**

Run (from `app/bibleview-js`): `npm run type-check`
Expected: PASS (no new errors).

- [ ] **Step 3: Commit**

```bash
git add app/bibleview-js/src/types/documents.ts
git commit -m "Add CommentaryRange type to OsisDocument"
```

---

### Task 8: Enable infinite scroll for commentaries + testable predicate

**Files:**
- Modify: `app/bibleview-js/src/composables/infinite-scroll.ts:41,169-183`
- Test: `app/bibleview-js/src/__tests__/infinite-scroll.spec.js` (create)

- [ ] **Step 1: Write the failing test**

Create `app/bibleview-js/src/__tests__/infinite-scroll.spec.js`:

```javascript
import {describe, it, expect} from "vitest";
import {supportsChapterNavigation} from "@/composables/infinite-scroll";

function osisDoc(bookCategory, extra = {}) {
    return {type: "osis", bookCategory, isAiDocument: false, ...extra};
}

describe("supportsChapterNavigation", () => {
    it("returns false for an empty document list", () => {
        expect(supportsChapterNavigation([])).toBe(false);
    });
    it("supports bible documents", () => {
        expect(supportsChapterNavigation([{type: "bible"}])).toBe(true);
    });
    it("supports commentary documents", () => {
        expect(supportsChapterNavigation([osisDoc("COMMENTARY")])).toBe(true);
    });
    it("supports general book documents", () => {
        expect(supportsChapterNavigation([osisDoc("GENERAL_BOOK")])).toBe(true);
    });
    it("does not support dictionary documents", () => {
        expect(supportsChapterNavigation([osisDoc("DICTIONARY")])).toBe(false);
    });
    it("does not support AI documents", () => {
        expect(supportsChapterNavigation([osisDoc("GENERAL_BOOK", {isAiDocument: true})])).toBe(false);
    });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `app/bibleview-js`): `npx vitest run src/__tests__/infinite-scroll.spec.js`
Expected: FAIL — `supportsChapterNavigation` is not exported.

- [ ] **Step 3: Add `COMMENTARY` and extract the predicate**

In `infinite-scroll.ts`, line 41, add `"COMMENTARY"`:

```typescript
    const enabledCategories: Set<BookCategory> = new Set(["BIBLE", "GENERAL_BOOK", "COMMENTARY"]);
```

Hoist the set and the predicate to module scope so they are testable. Above `export function useInfiniteScroll(` add:

```typescript
const enabledCategories: Set<BookCategory> = new Set(["BIBLE", "GENERAL_BOOK", "COMMENTARY"]);

/**
 * Whether the first document supports adjacent-chapter/block navigation (Bible, commentary, or
 * general book). AI documents are single-page generated content and are excluded. Both the manual
 * chapter controls and infinite scroll derive from this same contract.
 */
export function supportsChapterNavigation(documents: AnyDocument[]): boolean {
    if (documents.length === 0) return false;
    const doc = documents[0];
    if (isOsisDocument(doc)) {
        if (doc.isAiDocument) return false;
        return enabledCategories.has(doc.bookCategory);
    }
    return doc.type === "bible";
}
```

Remove the now-duplicated local `const enabledCategories` (line 41 inside the function) and replace the `documentSupportsChapterNavigation` computed (lines 174-183) with a delegating call:

```typescript
        documentSupportsChapterNavigation = computed(() => supportsChapterNavigation(bibleViewDocuments)),
```

Ensure `isOsisDocument` and `AnyDocument` are imported at the top of the file (add to existing imports from `@/types/documents` if missing). Verify: `grep -n "isOsisDocument\|AnyDocument" app/bibleview-js/src/composables/infinite-scroll.ts`.

- [ ] **Step 4: Run the test to verify it passes**

Run (from `app/bibleview-js`): `npx vitest run src/__tests__/infinite-scroll.spec.js`
Expected: PASS (6 tests).

- [ ] **Step 5: Run lint + type-check**

Run (from `app/bibleview-js`): `npm run lint && npm run type-check`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/bibleview-js/src/composables/infinite-scroll.ts \
        app/bibleview-js/src/__tests__/infinite-scroll.spec.js
git commit -m "Enable infinite scroll for commentaries

Adds COMMENTARY to the infinite-scroll enabled categories and extracts the
document-capability check into a testable module-scope supportsChapterNavigation()."
```

---

### Task 9: Render the block range header in `OsisDocument.vue`

**Files:**
- Modify: `app/bibleview-js/src/components/documents/OsisDocument.vue:36-50,74-87`

- [ ] **Step 1: Add the header and destructure the field**

In `OsisDocument.vue`, add `commentaryRange` to the destructuring (after `aiDocMarkers,` around line 86):

```typescript
    aiDocMarkers,
    commentaryRange,
} = props.document;
```

In the template, inside the `<template v-else>` block, add the range header as the first child (before `<OsisFragment ...>` at line 37):

```html
    <template v-else>
      <h2 v-if="bookCategory === 'COMMENTARY' && commentaryRange" class="commentary-range">{{ commentaryRange.name }}</h2>
      <OsisFragment :is-native-html="document.isNativeHtml" :fragment="osisFragment"/>
```

- [ ] **Step 2: Type-check + lint**

Run (from `app/bibleview-js`): `npm run type-check && npm run lint`
Expected: PASS.

- [ ] **Step 3: Run the full Vue unit suite to confirm no regressions**

Run (from `app/bibleview-js`): `npm run test:ci`
Expected: PASS (existing tests + the new infinite-scroll spec).

- [ ] **Step 4: Commit**

```bash
git add app/bibleview-js/src/components/documents/OsisDocument.vue
git commit -m "Show commentary block verse-range header

Renders the resolved block range (e.g. 'John 1:1-5') above commentary content
so the user sees which verses the deduplicated block covers."
```

---

## Final verification

- [ ] **Kotlin tests**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.CommentaryBlockResolverTest" --tests "*.GetCommentariesDedupTest" --tests "*.OsisToPlainTextTest"` (with `dangerouslyDisableSandbox: true`)
Expected: all PASS.

- [ ] **Kotlin compile (full debug variant)**

Run: `./gradlew assembleStandardGithubDebug` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Vue checks**

Run (from `app/bibleview-js`): `npm run test:ci && npm run lint && npm run type-check`
Expected: all PASS.

- [ ] **Manual smoke test (device/emulator, optional but recommended)**

Open a commentary that stores ranged entries (e.g. one reproducing OSTicket #3303). Verify:
1. The header shows the verse range (e.g. "1-5").
2. "Next" jumps past the whole range to the next distinct block, skipping empty verses.
3. With infinite scroll enabled, scrolling appends distinct blocks without repeating content.
4. Syncing from a Bible window onto a verse mid-block shows the whole block.
