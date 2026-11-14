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

import {setupEventBusListener} from "@/eventbus";

/**
 * How long a tap page turn can belong to a double tap. Android's double tap timeout is 300 ms between the first
 * tap's release and the second press, and the native `double_tap` arrives at the second press (Android) or release
 * (iOS); the window leaves room for the press durations around that.
 */
export const DOUBLE_TAP_WINDOW_MS = 700;

/**
 * Page turns by tapping (the margin zones and the e-ink page buttons) that a double tap undoes (#4083).
 *
 * A double tap is the native full screen toggle, but both of its taps also reach the page as clicks: a double tap in
 * a margin turned the page as well, so every full screen toggle moved the text (Psalm 23 to Psalm 22:23). The page
 * cannot tell a single tap from the first tap of a double tap without delaying every page turn, and only native knows
 * whether it acts on a double tap (the setting, or leaving full screen). So a tap turns the page at once, and when
 * native reports with `double_tap` that it acted on a double tap, the turns of the last [DOUBLE_TAP_WINDOW_MS] are
 * undone and the gesture's remaining tap turns nothing. Key and volume page scrolling never goes through here.
 *
 * @param turnPage turns one page up or down
 * @param scrollTo moves the page to a position at once
 */
export function useTapPageTurns(turnPage: (up: boolean) => void, scrollTo: (y: number) => void) {
    let turns: { fromY: number, at: number }[] = [];
    let ignoreUntil = 0;

    function tapPageTurn(up: boolean) {
        const now = Date.now();
        if (now < ignoreUntil) return;
        turns = turns.filter(t => now - t.at <= DOUBLE_TAP_WINDOW_MS);
        turns.push({fromY: window.scrollY, at: now});
        turnPage(up);
    }

    function onDoubleTap() {
        const now = Date.now();
        const recent = turns.filter(t => now - t.at <= DOUBLE_TAP_WINDOW_MS);
        turns = [];
        ignoreUntil = now + DOUBLE_TAP_WINDOW_MS;
        if (recent.length > 0) scrollTo(recent[0].fromY);
    }

    setupEventBusListener("double_tap", onDoubleTap);

    return {tapPageTurn};
}
