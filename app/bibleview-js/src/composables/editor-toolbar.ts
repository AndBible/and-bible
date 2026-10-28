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

/** The nearest ancestor of [el] that scrolls vertically itself, or null when only the document does. */
export function findScrollContainer(el: Element): HTMLElement | null {
    let node = el.parentElement;
    while (node && node !== document.body && node !== document.documentElement) {
        const overflowY = getComputedStyle(node).overflowY;
        if (overflowY === "auto" || overflowY === "scroll") return node;
        node = node.parentElement;
    }
    return null;
}

/**
 * True when [el] lives in its own scrolling or fixed box (the bookmark modal's bounded editor
 * container), so scrolling the document cannot move it.
 */
export function isInsideOwnScrollBox(el: Element): boolean {
    let node = el.parentElement;
    while (node && node !== document.body && node !== document.documentElement) {
        const style = getComputedStyle(node);
        if (style.position === "fixed" || style.overflowY === "auto" || style.overflowY === "scroll") {
            return true;
        }
        node = node.parentElement;
    }
    return false;
}

/**
 * Scrolls the document so that an inline note editor's toolbar [bar] is above the bottom
 * toolbar / keyboard. Does nothing for an editor inside the bookmark modal: there the toolbar
 * sticks to the bottom of the modal's editor container, and a document scroll would only move
 * the Bible text behind the modal (#3870).
 */
export function scrollEditorToolbarIntoView(bar: Element, bottomOffset: number, disableAnimations: boolean) {
    if (isInsideOwnScrollBox(bar)) return;
    const barRect = bar.getBoundingClientRect();
    const visibleBottom = window.innerHeight - bottomOffset;
    if (barRect.bottom > visibleBottom) {
        window.scrollBy({top: barRect.bottom - visibleBottom + 8, behavior: disableAnimations ? "instant" : "smooth"});
    }
}
