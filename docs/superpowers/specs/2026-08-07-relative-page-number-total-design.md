# Relative page number: show `x/y`

Date: 2026-08-07

## Goal

The *Relative page number* setting (`showPageNumber`) currently renders a single
number — the page offset from the origin position. Show it as `x/y` instead,
where `y` is the relative page number at the very end of the currently loaded
content. When the origin sits at the start of the content (the normal case),
`y` reads as "how many pages this document has".

Example: `3.2/16`.

## Current behaviour

`BibleView.vue`:

```ts
const pageNumber = computed(() => {
    const num = (scrollY.value - scrollYAtStart.value) / scrollAmount.value;
    return num.toFixed(1);
});

function resetPageNumber() {
    scrollYAtStart.value = scrollY.value
}
```

- `scrollY` / `scrollYAtStart` come from `useScroll` (`composables/scroll.ts`).
- `scrollAmount` is `calcPageScrollDistance(...)` from `composables/page-scroll.ts`.
- Tapping the overlay moves the origin to the current position (`x` becomes `0.0`).
- `infinite-scroll.ts` already shifts `scrollYAtStart` when content is prepended
  at the top, so the origin stays anchored to the same text.

## Design

### Calculation

New pure function in `composables/page-scroll.ts` (alongside
`calcPageScrollDistance` / `calcHelperLinePositions`, which are unit-tested in
`__tests__/page-scroll.spec.js`):

```ts
export function calcRelativePageNumbers(
    scrollY: number,
    scrollYAtStart: number,
    maxScrollY: number,
    scrollAmount: number,
): {current: number, total: number}
```

- `current = (scrollY - scrollYAtStart) / scrollAmount`
- `total = Math.ceil((maxScrollY - scrollYAtStart) / scrollAmount)`
- `scrollAmount <= 0` (or non-finite) returns `{current: 0, total: 0}` — guards
  against division by zero before layout is measured.
- `total` is clamped to `>= 0`.
- `current` may be negative (scrolling above the origin), exactly as today.

Formatting stays in the component: `current.toFixed(1)`, `total` as an integer.

### Reactive total

**Revised 2026-08-07:** `scrollHeight - innerHeight` counts the tall
`padding-bottom: 200vh` on `#bottom` (which exists so the reader can scroll past
the last line), which inflated the total by roughly three pages. The end of the
text is `#bottom`'s `offsetTop` instead — the same measure `infinite-scroll.ts`
already uses for its own end-of-content threshold:

`maxScrollY = calcMaxScrollY(contentEnd, viewportHeight, bottomOffset)`, i.e.
`max(0, contentEnd - (viewportHeight - bottomOffset))` — the scroll position that
brings the end of the text to the bottom of the readable area.

It is tracked with a `ResizeObserver` on `document.documentElement`, held in the
`BibleView.vue` setup and disconnected on unmount. This keeps `y` correct when:

- infinite scroll appends or prepends documents,
- font size / margins / page-scroll-amount settings change,
- the device rotates or the window is resized,
- images or fonts finish loading and reflow the page.

A scroll-event-only recomputation was rejected: `y` would go stale whenever the
content height changes without the user scrolling.

`window.innerHeight` is read inside the same handler, so viewport changes are
covered by the observer firing on the root element.

### Origin semantics

**Revised 2026-08-14:** numbering is one-based. The top of the loaded content is
page `1`, not `0`, so a freshly opened document reads `1.0/y` instead of `0.0/y`.
Both numbers gain the same `+1`, so the end of the content still reads `x ≈ y`:

- `current = 1 + scrollY / scrollAmount`
- `total = 1 + max(0, ceil(maxScrollY / scrollAmount))` — one page for the first
  screenful plus one per page scroll needed to reach the end.
- Content shorter than the viewport (`maxScrollY <= 0`) is `1.0/1`, and the
  unmeasured-layout guard returns `{current: 1, total: 1}` rather than zeroes.

**Revised 2026-08-07** (supersedes the `scrollYAtStart` design below, which was
implemented first):

Both numbers are measured from the top of the loaded content, i.e. scroll
position 0. Consequences:

- Opening a document mid-page reads as e.g. `0.5/16`, not `0.0/16`. Previously
  the origin was captured at load time (`scroll.ts`, `setupContent`), so `x` was
  always `0.0` at open regardless of where the view landed.
- Tapping the overlay to move the origin is **removed**. `x/y` now always means
  the same thing, so there is no second mode to switch to.
- When infinite scroll prepends chapters, the origin follows the new top: `x`
  and `y` both grow, and `x/y` keeps describing the position within the loaded
  content. The old `scrollYAtStart` adjustment in `infinite-scroll.ts` is gone.

`scrollYAtStart` had no other consumer, so it is removed from `useScroll`, from
`setupContent`, and from `useInfiniteScroll`'s parameter list (which no longer
takes `UseScroll` at all).

`calcRelativePageNumbers` therefore takes three arguments — `scrollY`,
`maxScrollY`, `scrollAmount` — not four.

### Scope: loaded content only

`y` covers the content currently loaded in the WebView:

- General books, EPUBs and AI documents load as one document → `y` is the whole
  document's page count.
- Bible views grow through infinite scroll → `y` grows as chapters load. This is
  accepted and matches the request ("nykyisen ladatun contentin loppuun").

### UI

`BibleView.vue` template, inside the existing `.pagenumber` overlay:

```html
<div class="pagenumber-text">
  {{ pageNumber }}/{{ pageCount }}
</div>
```

- No new colours, backgrounds or animations → monochrome/e-ink, dark, light and
  no-animation modes are unaffected.
- No new user-facing strings: the output is digits and a slash. The existing
  `page_number_title` / `page_number_summary` strings still describe the feature
  accurately.
- No database, settings or Kotlin changes — this is purely a rendering change
  behind the existing `showPageNumber` setting.

## Testing

Extend `app/bibleview-js/src/__tests__/page-scroll.spec.js` with cases for
`calcRelativePageNumbers`:

1. Basic case: origin at 0, mid-document scroll → expected `current`, `total`.
2. Ceiling rounding: a total of 15.1 pages reports `16`.
3. Origin reset mid-document: `current` is 0 and `total` shrinks to the pages
   remaining below the new origin.
4. Content shorter than the viewport (`maxScrollY <= 0`) → `total` is 0, not
   negative.
5. `scrollAmount` of 0 → `{current: 0, total: 0}` instead of `NaN`/`Infinity`.
6. Scrolled above the origin → `current` is negative, `total` still positive.

Run `npm run test:ci && npm run lint && npm run type-check` in
`app/bibleview-js`. No Android tests are relevant (no Kotlin changes).

## Out of scope

- The separate *Reading progress* overlay (`showReadingProgress`), which
  estimates whole-book pages from character counts, is unchanged.
- No absolute (document-wide) page numbering for Bible views beyond what is
  loaded.
