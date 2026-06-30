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
import {calcHelperLinePositions, calcPageScrollDistance, helperLinePercents} from "@/composables/page-scroll";

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
