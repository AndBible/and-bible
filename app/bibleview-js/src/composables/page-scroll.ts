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

/**
 * Geometry for page scrolling and the e-ink scroll helper lines.
 *
 * Both deliberately ignore the reading top margin: the visible content area is
 * treated as the full region between the toolbar (top offset without the margin)
 * and the bottom bar. This keeps the top helper line in the same place — and the
 * page up/down distance the same — whether or not a top margin is configured.
 */

/** Helper line positions (as page percentages) for each supported scroll amount. */
export const helperLinePercents: Record<number, number[]> = {
    25: [25, 50, 75],
    33: [33, 66],
    50: [50],
    66: [33, 66],
    75: [25, 75],
};

/**
 * Vertical positions (px from the viewport top) of the scroll helper lines.
 *
 * @param pageScrollAmount configured page scroll amount in percent
 * @param topOffset full top offset including the reading top margin (calculatedConfig.topOffset)
 * @param pageHeight content height with the top margin already subtracted (calculatedConfig.pageHeight)
 * @param topMargin the reading top margin in px (calculatedConfig.topMargin)
 */
export function calcHelperLinePositions(
    pageScrollAmount: number,
    topOffset: number,
    pageHeight: number,
    topMargin: number,
): number[] {
    const percents = helperLinePercents[pageScrollAmount];
    if (!percents) return [];
    const contentTop = topOffset - topMargin;
    const contentHeight = pageHeight + topMargin;
    return percents.map(p => contentTop + contentHeight * (p / 100));
}

/**
 * Distance (px) a single page up/down scroll moves.
 *
 * At 100% a 1.5 line-height overlap is subtracted so the last partially-visible
 * line is not skipped between pages.
 *
 * @param pageHeight content height with the top margin already subtracted (calculatedConfig.pageHeight)
 * @param topMargin the reading top margin in px (calculatedConfig.topMargin)
 * @param pageScrollAmount configured page scroll amount in percent
 * @param lineHeight current line height in px
 */
export function calcPageScrollDistance(
    pageHeight: number,
    topMargin: number,
    pageScrollAmount: number,
    lineHeight: number,
): number {
    const contentHeight = pageHeight + topMargin;
    let amount = contentHeight * (pageScrollAmount / 100);
    if (pageScrollAmount === 100) {
        amount -= 1.5 * lineHeight; // 1.5 times because last line might otherwise be displayed partially
    }
    return amount;
}

/**
 * Largest meaningful scroll position: the one that brings the end of the text to
 * the bottom edge of the readable area.
 *
 * This is deliberately not `scrollHeight - innerHeight`. The `#bottom` element
 * carries a tall padding so the reader can scroll past the last line, and that
 * padding must not be counted as pages.
 *
 * @param contentEnd document position where the text ends (`#bottom`'s offsetTop)
 * @param viewportHeight window.innerHeight
 * @param bottomOffset height of the bottom bar covering the viewport, if any
 */
export function calcMaxScrollY(
    contentEnd: number,
    viewportHeight: number,
    bottomOffset: number,
): number {
    return Math.max(0, contentEnd - (viewportHeight - bottomOffset));
}

/**
 * Relative page numbers for the page-number overlay: the current position and
 * the end of the currently loaded content, both measured from the top of that
 * content.
 *
 * Numbering is one-based: the top of the loaded content is page 1, so opening a
 * document halfway down the first page reads as 1.5, and the total is the page
 * count of the loaded content (one page for the first screenful plus one per
 * page scroll needed to reach the end). When infinite scroll prepends chapters,
 * both numbers grow accordingly — they always describe the position within the
 * loaded content.
 *
 * @param scrollY current vertical scroll position in px
 * @param maxScrollY largest scrollable position (scrollHeight - viewport height)
 * @param scrollAmount distance a single page scroll moves (calcPageScrollDistance)
 * @returns fractional `current` page and a rounded-up `total`, both >= 1
 */
export function calcRelativePageNumbers(
    scrollY: number,
    maxScrollY: number,
    scrollAmount: number,
): {current: number, total: number} {
    // Before the layout is measured scrollAmount can be 0 or NaN — avoid NaN/Infinity output.
    if (!(scrollAmount > 0)) return {current: 1, total: 1};
    return {
        current: 1 + scrollY / scrollAmount,
        total: 1 + Math.max(0, Math.ceil(maxScrollY / scrollAmount)),
    };
}
