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

import {describe, it, expect} from "vitest";
import {
    calcHelperLinePositions,
    calcMaxScrollY,
    calcPageScrollDistance,
    calcRelativePageNumbers,
    helperLinePercents,
} from "@/composables/page-scroll";

// A bible viewport with a 50px reading top margin and one without it, but with
// the same overall content area between toolbar and bottom bar:
//   - no margin:   topOffset=100, pageHeight=800, topMargin=0
//   - with margin: topOffset=150, pageHeight=750, topMargin=50
// (calculatedConfig.topOffset includes the margin; pageHeight has it subtracted.)
const NO_MARGIN = {topOffset: 100, pageHeight: 800, topMargin: 0};
const WITH_MARGIN = {topOffset: 150, pageHeight: 750, topMargin: 50};

describe("calcHelperLinePositions", () => {
    it("returns one position per configured percentage", () => {
        expect(calcHelperLinePositions(25, 100, 800, 0)).toHaveLength(helperLinePercents[25].length);
        expect(calcHelperLinePositions(50, 100, 800, 0)).toHaveLength(1);
        expect(calcHelperLinePositions(75, 100, 800, 0)).toHaveLength(2);
    });

    it("places lines at the configured fractions of the content area", () => {
        // contentTop=100, contentHeight=800 → 25/50/75% = 300/500/700
        expect(calcHelperLinePositions(25, 100, 800, 0)).toEqual([300, 500, 700]);
        // 50% → single line at the middle
        expect(calcHelperLinePositions(50, 100, 800, 0)).toEqual([500]);
        // 75% mode shows lines at 25% and 75% (not 3 lines)
        expect(calcHelperLinePositions(75, 100, 800, 0)).toEqual([300, 700]);
    });

    it("ignores the top margin: same positions with or without a margin", () => {
        const withoutMargin = calcHelperLinePositions(50, NO_MARGIN.topOffset, NO_MARGIN.pageHeight, NO_MARGIN.topMargin);
        const withMargin = calcHelperLinePositions(50, WITH_MARGIN.topOffset, WITH_MARGIN.pageHeight, WITH_MARGIN.topMargin);
        expect(withMargin).toEqual(withoutMargin);
    });

    it("keeps the top helper line in the same place regardless of margin", () => {
        const withoutMargin = calcHelperLinePositions(25, NO_MARGIN.topOffset, NO_MARGIN.pageHeight, NO_MARGIN.topMargin);
        const withMargin = calcHelperLinePositions(25, WITH_MARGIN.topOffset, WITH_MARGIN.pageHeight, WITH_MARGIN.topMargin);
        expect(withMargin[0]).toBe(withoutMargin[0]);
    });

    it("returns no lines for an unsupported scroll amount (e.g. 100%)", () => {
        expect(calcHelperLinePositions(100, 100, 800, 0)).toEqual([]);
        expect(calcHelperLinePositions(40, 100, 800, 0)).toEqual([]);
    });
});

describe("calcPageScrollDistance", () => {
    it("scrolls by the configured fraction of the content area for partial scrolls", () => {
        expect(calcPageScrollDistance(800, 0, 50, 20)).toBe(400);
        expect(calcPageScrollDistance(800, 0, 25, 20)).toBe(200);
    });

    it("subtracts a 1.5 line overlap only at 100%", () => {
        expect(calcPageScrollDistance(800, 0, 100, 20)).toBe(800 - 1.5 * 20);
        expect(calcPageScrollDistance(800, 0, 75, 20)).toBe(600); // no subtraction below 100%
    });

    it("ignores the top margin: same distance with or without a margin", () => {
        const withoutMargin = calcPageScrollDistance(NO_MARGIN.pageHeight, NO_MARGIN.topMargin, 50, 20);
        const withMargin = calcPageScrollDistance(WITH_MARGIN.pageHeight, WITH_MARGIN.topMargin, 50, 20);
        expect(withMargin).toBe(withoutMargin);
    });

    it("ignores the top margin at 100% too", () => {
        const withoutMargin = calcPageScrollDistance(NO_MARGIN.pageHeight, NO_MARGIN.topMargin, 100, 20);
        const withMargin = calcPageScrollDistance(WITH_MARGIN.pageHeight, WITH_MARGIN.topMargin, 100, 20);
        expect(withMargin).toBe(withoutMargin);
    });
});

describe("calcRelativePageNumbers", () => {
    it("counts pages from the top of the loaded content and rounds the total up", () => {
        // 800px pages, 12100px of scrollable content
        // → current = 2400/800 + 1 = 4, total = ceil(12100/800) + 1 = ceil(15.125) + 1 = 17
        expect(calcRelativePageNumbers(2400, 12100, 800)).toEqual({current: 4, total: 17});
    });

    it("starts at page 1 at the top of the content", () => {
        expect(calcRelativePageNumbers(0, 12100, 800).current).toBe(1);
    });

    it("keeps the current page fractional", () => {
        expect(calcRelativePageNumbers(2000, 12100, 800).current).toBeCloseTo(3.5);
    });

    it("reports a fractional first page when the document opens mid-content", () => {
        // Opening at a verse halfway down the first page reads as 1.5, not 1.0
        expect(calcRelativePageNumbers(400, 12100, 800).current).toBeCloseTo(1.5);
    });

    it("reports an exact total without rounding up a whole page", () => {
        // 12800/800 = 16 exactly → 16 scrolls past the first page = 17 pages
        expect(calcRelativePageNumbers(0, 12800, 800).total).toBe(17);
    });

    it("reports the current page as the last page at the end of the content", () => {
        const {current, total} = calcRelativePageNumbers(12100, 12100, 800);
        expect(current).toBeCloseTo(16.125);
        expect(total).toBe(17);
    });

    it("reports a single page when the content fits on one screen", () => {
        // maxScrollY <= 0 means nothing to scroll — that is still one page, "1.0/1"
        expect(calcRelativePageNumbers(0, 0, 800)).toEqual({current: 1, total: 1});
        expect(calcRelativePageNumbers(0, -50, 800)).toEqual({current: 1, total: 1});
    });

    it("returns a single page instead of NaN/Infinity when the page size is not measured yet", () => {
        expect(calcRelativePageNumbers(2400, 12100, 0)).toEqual({current: 1, total: 1});
        expect(calcRelativePageNumbers(2400, 12100, -10)).toEqual({current: 1, total: 1});
        expect(calcRelativePageNumbers(2400, 12100, NaN)).toEqual({current: 1, total: 1});
    });
});

describe("calcMaxScrollY", () => {
    // contentEnd is the document position where the text ends (#bottom's offsetTop),
    // so the tall padding below it never counts as pages.
    it("is the scroll position that brings the end of the text to the bottom edge", () => {
        expect(calcMaxScrollY(20000, 1000, 0)).toBe(19000);
    });

    it("stops above a bottom bar so the last line is not hidden behind it", () => {
        // With a 100px bottom bar the readable area ends 100px higher, so the
        // end of the text is reached 100px later.
        expect(calcMaxScrollY(20000, 1000, 100)).toBe(19100);
    });

    it("is zero when the whole text fits on one screen", () => {
        expect(calcMaxScrollY(500, 1000, 0)).toBe(0);
        expect(calcMaxScrollY(1000, 1000, 0)).toBe(0);
    });

    it("is zero rather than negative when the bottom bar covers more than the text", () => {
        expect(calcMaxScrollY(200, 1000, 900)).toBe(100);
        expect(calcMaxScrollY(50, 1000, 900)).toBe(0);
    });
});
