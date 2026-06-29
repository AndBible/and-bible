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
    const bookRp = {kind: "book", fragmentOffset: 0, bookOrdinalSpan: 1000, charCount: 50000};
    const genRp = {kind: "bible", unitStart: 0, unitEnd: 100, chapterCount: 50, currentChapter: 1};
    const exodRp = {kind: "bible", unitStart: 101, unitEnd: 200, chapterCount: 40, currentChapter: 1};

    it("returns null when no document has progress", () => {
        expect(resolveReadingProgress([{}], 5, "")).toBeNull();
        expect(resolveReadingProgress([], 5, "")).toBeNull();
    });
    it("matches by osisRef (currentKey) first — required for EPUB overlapping ordinals", () => {
        // Two EPUB fragments with overlapping local ordinal ranges; only the key disambiguates.
        const fragA = {readingProgress: bookRp, ordinalRange: [0, 400], osisRef: "12"};
        const fragB = {readingProgress: bookRp, ordinalRange: [0, 350], osisRef: "13"};
        const docs = [fragA, fragB];
        expect(resolveReadingProgress(docs, 100, "13")).toBe(fragB);
        expect(resolveReadingProgress(docs, 100, "12")).toBe(fragA);
    });
    it("falls back to ordinal containment when no key matches (Bible absolute ordinals)", () => {
        const docs = [
            {readingProgress: genRp, ordinalRange: [0, 100], osisRef: "Gen.1"},
            {readingProgress: exodRp, ordinalRange: [101, 200], osisRef: "Exod.1"},
        ];
        expect(resolveReadingProgress(docs, 150, "")).toBe(docs[1]);
        expect(resolveReadingProgress(docs, 5, "")).toBe(docs[0]);
    });
    it("falls back to the first progress doc when nothing matches", () => {
        const docs = [{readingProgress: bookRp, ordinalRange: [0, 1000], osisRef: "1"}];
        expect(resolveReadingProgress(docs, null, "")).toBe(docs[0]);
        expect(resolveReadingProgress(docs, 99999, "nope")).toBe(docs[0]);
    });
});
