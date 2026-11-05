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

// The native document generation of the content this page shows (#3975, osTicket 3508). Native sends it with each
// document's `clear_document`; native-bridge.ts echoes it with every call, and native drops calls that act on the
// window's current document (scroll reports, paging, links, ...) when the generation is not the admitted one. A web
// view can outlive its document and even its window (a workspace switch hands it over), so this is how native tells
// the page's calls about the old content from calls about the new one.

import {nextTick} from "vue";

/** No document received yet; native never admits it. */
export const NO_DOCUMENT_GENERATION = 0;

let generation = NO_DOCUMENT_GENERATION;

export function documentGeneration(): number {
    return generation;
}

/**
 * Switches to [next] once the cleared content is gone from the DOM. Call it after clearing the documents: until Vue has
 * re-rendered, the old elements are still on screen and a position measured from them still belongs to the old
 * generation; after the render, nothing of the old document is left to measure or click.
 */
export function adoptDocumentGeneration(next: number | null | undefined): Promise<void> {
    const value = next ?? NO_DOCUMENT_GENERATION;
    return nextTick(() => {
        generation = value;
    });
}

/** For tests. */
export function resetDocumentGeneration(): void {
    generation = NO_DOCUMENT_GENERATION;
}
