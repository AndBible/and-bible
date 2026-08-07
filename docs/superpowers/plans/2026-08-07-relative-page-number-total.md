# Relative Page Number `x/y` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the relative page number overlay as `x/y`, where `y` is the relative page number at the end of the currently loaded content (i.e. the document's page count when the origin is at the start).

**Architecture:** A new pure function `calcRelativePageNumbers` in `app/bibleview-js/src/composables/page-scroll.ts` does the arithmetic and is unit-tested in isolation. `BibleView.vue` tracks `maxScrollY` (`document.documentElement.scrollHeight - window.innerHeight`) in a ref kept fresh by a `ResizeObserver` on `document.documentElement`, feeds it to the pure function, and renders `{{ pageNumber }}/{{ pageCount }}` in the existing `.pagenumber` overlay.

**Tech Stack:** Vue 3 (`<script setup>`, TypeScript), Vitest, SCSS. Frontend only — no Kotlin, no database, no new strings.

**Spec:** `docs/superpowers/specs/2026-08-07-relative-page-number-total-design.md`

## Global Constraints

- All work happens in `app/bibleview-js/`. Run commands from that directory.
- Validation command for every task: `npm run test:ci && npm run lint && npm run type-check`.
- Do **not** add translation strings — the overlay renders digits and a slash only.
- Do **not** touch the separate *Reading progress* feature (`showReadingProgress`, `use-reading-progress.ts`, `reading-progress.ts`, `ReadingProgress.vue`).
- Do **not** change database entities, migrations, `TextDisplaySettings`, or `text_display_settings.xml` — the existing `showPageNumber` boolean setting is reused as-is.
- No new colours, backgrounds or animations, so dark / light / monochrome (e-ink) / no-animation modes stay unaffected.
- Existing files already carry a copyright header; leave it, but update the year to 2026 if it is older (it is already 2026 in both files touched here).
- Commit after each task. Do not push.

---

### Task 1: Pure page-number arithmetic

**Files:**
- Modify: `app/bibleview-js/src/composables/page-scroll.ts` (append after `calcPageScrollDistance`, which ends at line 80)
- Test: `app/bibleview-js/src/__tests__/page-scroll.spec.js` (append a new `describe` block at the end; also extend the import on line 19)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `calcRelativePageNumbers(scrollY: number, scrollYAtStart: number, maxScrollY: number, scrollAmount: number): {current: number, total: number}` — exported from `@/composables/page-scroll`. `current` is fractional and may be negative; `total` is a non-negative integer (already `Math.ceil`-ed).

- [ ] **Step 1: Write the failing tests**

In `app/bibleview-js/src/__tests__/page-scroll.spec.js`, change the import on line 19 from:

```js
import {calcHelperLinePositions, calcPageScrollDistance, helperLinePercents} from "@/composables/page-scroll";
```

to:

```js
import {
    calcHelperLinePositions,
    calcPageScrollDistance,
    calcRelativePageNumbers,
    helperLinePercents,
} from "@/composables/page-scroll";
```

Then append this block at the end of the file (after the closing `});` of the `calcPageScrollDistance` describe):

```js
describe("calcRelativePageNumbers", () => {
    it("counts pages from the origin and rounds the total up", () => {
        // origin at the top, 800px pages, 12100px of scrollable content
        // → current = 2400/800 = 3, total = ceil(12100/800) = ceil(15.125) = 16
        expect(calcRelativePageNumbers(2400, 0, 12100, 800)).toEqual({current: 3, total: 16});
    });

    it("keeps the current page fractional", () => {
        expect(calcRelativePageNumbers(2000, 0, 12100, 800).current).toBeCloseTo(2.5);
    });

    it("reports an exact total without rounding up a whole page", () => {
        // 12800/800 = 16 exactly — must stay 16, not 17
        expect(calcRelativePageNumbers(0, 0, 12800, 800).total).toBe(16);
    });

    it("measures both numbers from a reset origin", () => {
        // Origin reset at 2400 → current is 0 and the total is what remains below it:
        // ceil((12100 - 2400)/800) = ceil(12.125) = 13
        expect(calcRelativePageNumbers(2400, 2400, 12100, 800)).toEqual({current: 0, total: 13});
    });

    it("reports zero total when the content fits on one screen", () => {
        // maxScrollY <= 0 means nothing to scroll — the total must not go negative
        expect(calcRelativePageNumbers(0, 0, 0, 800)).toEqual({current: 0, total: 0});
        expect(calcRelativePageNumbers(0, 0, -50, 800)).toEqual({current: 0, total: 0});
    });

    it("never reports a negative total when the origin is past the end", () => {
        // Elastic overscroll can push the origin below maxScrollY
        expect(calcRelativePageNumbers(12300, 12300, 12100, 800).total).toBe(0);
    });

    it("allows a negative current page above the origin", () => {
        const {current, total} = calcRelativePageNumbers(800, 2400, 12100, 800);
        expect(current).toBe(-2);
        expect(total).toBe(13);
    });

    it("returns zeroes instead of NaN/Infinity when the page size is not measured yet", () => {
        expect(calcRelativePageNumbers(2400, 0, 12100, 0)).toEqual({current: 0, total: 0});
        expect(calcRelativePageNumbers(2400, 0, 12100, -10)).toEqual({current: 0, total: 0});
        expect(calcRelativePageNumbers(2400, 0, 12100, NaN)).toEqual({current: 0, total: 0});
    });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `app/bibleview-js`):

```bash
npm run test:ci -- page-scroll
```

Expected: FAIL — `calcRelativePageNumbers is not a function` (the import resolves to `undefined`).

- [ ] **Step 3: Write the implementation**

Append to `app/bibleview-js/src/composables/page-scroll.ts`:

```ts
/**
 * Relative page numbers for the page-number overlay: how far the current scroll
 * position is from the origin, and how many pages there are between the origin
 * and the end of the currently loaded content.
 *
 * Both numbers share the same origin, so resetting the origin (tapping the
 * overlay) turns the total into "pages remaining from here". With the origin at
 * the start of the content — the normal case — the total is the page count of
 * the loaded document.
 *
 * @param scrollY current vertical scroll position in px
 * @param scrollYAtStart scroll position the page numbering is measured from
 * @param maxScrollY largest scrollable position (scrollHeight - viewport height)
 * @param scrollAmount distance a single page scroll moves (calcPageScrollDistance)
 * @returns fractional `current` page (may be negative above the origin) and a
 *          non-negative, rounded-up `total`
 */
export function calcRelativePageNumbers(
    scrollY: number,
    scrollYAtStart: number,
    maxScrollY: number,
    scrollAmount: number,
): {current: number, total: number} {
    // Before the layout is measured scrollAmount can be 0 or NaN — avoid NaN/Infinity output.
    if (!(scrollAmount > 0)) return {current: 0, total: 0};
    const current = (scrollY - scrollYAtStart) / scrollAmount;
    const total = Math.max(0, Math.ceil((maxScrollY - scrollYAtStart) / scrollAmount));
    return {current, total};
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `app/bibleview-js`):

```bash
npm run test:ci -- page-scroll
```

Expected: PASS — all `calcRelativePageNumbers` tests green, and the pre-existing `calcHelperLinePositions` / `calcPageScrollDistance` tests still green.

- [ ] **Step 5: Run the full validation**

Run (from `app/bibleview-js`):

```bash
npm run test:ci && npm run lint && npm run type-check
```

Expected: all three pass.

- [ ] **Step 6: Commit**

```bash
git add app/bibleview-js/src/composables/page-scroll.ts app/bibleview-js/src/__tests__/page-scroll.spec.js
git commit -m "Add calcRelativePageNumbers for the page number overlay total"
```

---

### Task 2: Render `x/y` in the page-number overlay

**Files:**
- Modify: `app/bibleview-js/src/components/BibleView.vue`
  - line 155 (import)
  - lines 222-226 (`onMounted` / `onUnmounted`)
  - lines 465-468 (`pageNumber` computed)
  - lines 71-73 (template, inside `.pagenumber-text`)
- Test: none — `BibleView.vue` has no component test in this codebase and the arithmetic is covered by Task 1. Verification is the type-check/lint/build plus the manual check in Step 6.

**Interfaces:**
- Consumes: `calcRelativePageNumbers(scrollY, scrollYAtStart, maxScrollY, scrollAmount)` from Task 1, returning `{current: number, total: number}`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Import the new function**

In `app/bibleview-js/src/components/BibleView.vue`, change line 155 from:

```ts
import {calcHelperLinePositions, calcPageScrollDistance} from "@/composables/page-scroll";
```

to:

```ts
import {calcHelperLinePositions, calcPageScrollDistance, calcRelativePageNumbers} from "@/composables/page-scroll";
```

- [ ] **Step 2: Track `maxScrollY` with a ResizeObserver**

Replace lines 220-226 (the `mounted` ref plus the `onMounted`/`onUnmounted` pair):

```ts
const mounted = ref(false);

onMounted(() => {
    mounted.value = true;
    console.log("BibleView mounted");
})
onUnmounted(() => mounted.value = false)
```

with:

```ts
const mounted = ref(false);

// Largest scrollable position, i.e. the scroll position at the very end of the
// currently loaded content. Kept fresh by a ResizeObserver so it also follows
// infinite-scroll loading, font/margin changes and rotation — not just scrolling.
const maxScrollY = ref(0);
let contentResizeObserver: ResizeObserver | null = null;

function updateMaxScrollY() {
    maxScrollY.value = document.documentElement.scrollHeight - window.innerHeight;
}

onMounted(() => {
    mounted.value = true;
    updateMaxScrollY();
    contentResizeObserver = new ResizeObserver(updateMaxScrollY);
    contentResizeObserver.observe(document.documentElement);
    console.log("BibleView mounted");
})
onUnmounted(() => {
    mounted.value = false;
    contentResizeObserver?.disconnect();
    contentResizeObserver = null;
})
```

- [ ] **Step 3: Compute the two numbers**

Replace lines 465-468:

```ts
const pageNumber = computed(() => {
    const num = (scrollY.value - scrollYAtStart.value) / scrollAmount.value;
    return num.toFixed(1);
});
```

with:

```ts
const pageNumbers = computed(() =>
    calcRelativePageNumbers(scrollY.value, scrollYAtStart.value, maxScrollY.value, scrollAmount.value)
);
const pageNumber = computed(() => pageNumbers.value.current.toFixed(1));
const pageCount = computed(() => pageNumbers.value.total);
```

Leave `resetPageNumber()` below it unchanged — moving the origin correctly changes both numbers.

- [ ] **Step 4: Render `x/y` in the template**

Replace lines 71-73:

```html
      <div class="pagenumber-text">
        {{ pageNumber }}
      </div>
```

with:

```html
      <div class="pagenumber-text">
        {{ pageNumber }}/{{ pageCount }}
      </div>
```

- [ ] **Step 5: Run the full validation**

Run (from `app/bibleview-js`):

```bash
npm run test:ci && npm run lint && npm run type-check
```

Expected: all three pass. `type-check` is the real gate here — it catches a mistyped `pageNumbers.value.current` / `.total` or a missing import.

- [ ] **Step 6: Verify the production build**

Run (from `app/bibleview-js`):

```bash
npm run build-debug
```

Expected: build succeeds with no template compilation errors.

- [ ] **Step 7: Commit**

```bash
git add app/bibleview-js/src/components/BibleView.vue
git commit -m "Show relative page number as x/y

The relative page number overlay now shows the page count of the loaded
content after the current page, e.g. \"3.2/16\". Both numbers share the
same origin, so tapping the overlay to reset it turns the total into the
pages remaining from that point."
```

---

## Manual verification (after both tasks)

Not automatable here — hand off to the user, who runs the app on a device:

1. Enable *Relative page number* in text display settings.
2. Open a general book or an AI document → the overlay reads `0.0/N`, where `N` is that document's page count. Scrolling to the very end reads `N-ish/N`.
3. Open a Bible view → `N` grows as infinite scroll loads more chapters.
4. Tap the overlay mid-document → `x` resets to `0.0` and `N` shrinks to the pages remaining.
5. Change the font size → `N` updates without needing to scroll.
6. Check the overlay in dark, light and monochrome (e-ink) modes — it should look exactly as before, only wider.
