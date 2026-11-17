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
 * Geometry of the iOS overlay scrollbar (issue #4137). WKWebView ignores `::-webkit-scrollbar`, and its native
 * indicator only flashes during a touch drag (never for the Mac wheel path, which moves `contentOffset` directly),
 * so the iOS host draws its own thin thumb. All values are CSS pixels.
 */
export interface ScrollbarInput {
    /** Height of the visible page (window.innerHeight). */
    viewportHeight: number;
    /** Total scrollable page height (documentElement.scrollHeight). */
    scrollHeight: number;
    /** Top of the track: below an overlaid toolbar. */
    trackTop: number;
    /** Track height: the viewport minus the toolbar insets. */
    trackHeight: number;
    /** Smallest thumb height, so the thumb stays visible and grabbable on a very long page. */
    minThumb: number;
}

export interface ThumbGeometry {
    top: number;
    height: number;
}

function clamp(v: number, min: number, max: number) {
    return Math.min(max, Math.max(min, v));
}

function thumbHeight(i: ScrollbarInput): number {
    return Math.min(i.trackHeight, Math.max(i.minThumb, i.trackHeight * i.viewportHeight / i.scrollHeight));
}

/** The thumb for [scrollTop], or null when the page does not overflow (no thumb is shown). */
export function thumbGeometry(i: ScrollbarInput & { scrollTop: number }): ThumbGeometry | null {
    const maxScroll = i.scrollHeight - i.viewportHeight;
    if (maxScroll <= 0 || i.trackHeight <= 0) return null;
    const height = thumbHeight(i);
    // Rubber-band overscroll reports offsets outside 0..maxScroll; keep the thumb at the track ends.
    const fraction = clamp(i.scrollTop / maxScroll, 0, 1);
    return {top: i.trackTop + fraction * (i.trackHeight - height), height};
}

/** The page scroll offset that puts the thumb's top at [thumbTop] (a mouse drag). */
export function scrollTopForThumbTop(i: ScrollbarInput, thumbTop: number): number {
    const maxScroll = i.scrollHeight - i.viewportHeight;
    const travel = i.trackHeight - thumbHeight(i);
    if (maxScroll <= 0 || travel <= 0) return 0;
    return clamp((thumbTop - i.trackTop) / travel, 0, 1) * maxScroll;
}
