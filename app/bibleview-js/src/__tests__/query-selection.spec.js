/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

// The page side of the Ctrl+C contract. The native copy path (SelectionCopy.kt) copies a bare string
// as plain text and formats a selection object as verses only for a Bible, so querySelection must
// keep returning a string for text outside a `.document` (popups such as footnotes, #3462).

import {afterEach, beforeAll, describe, expect, it} from "vitest";
import {ref} from "vue";
import {useConfig} from "@/composables/config";
import {useAndroid} from "@/composables/android";

function select(node) {
    const range = document.createRange();
    range.selectNodeContents(node);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
}

describe("querySelection", () => {
    let querySelection;

    beforeAll(() => {
        window.bibleView = {};
        window.bibleViewDebug = {};
        const {config} = useConfig(ref("bible"));
        useAndroid({bookmarks: ref([])}, config);
        querySelection = window.bibleView.querySelection;
    });

    afterEach(() => {
        window.getSelection().removeAllRanges();
        document.body.innerHTML = "";
    });

    it("returns plain text for a selection in a popup outside the document (#3462)", () => {
        document.body.innerHTML = `
            <div id="content"><div class="document" data-book-initials="KJV" data-osis-ref="Gen.1">
                <span class="ordinal" id="o-1">In the beginning</span>
            </div></div>
            <div id="modals"><p id="footnote">Or, In the beginning</p></div>`;
        select(document.getElementById("footnote"));

        expect(querySelection()).toBe("Or, In the beginning");
    });

    it("returns a selection object with document context for text inside a document", () => {
        document.body.innerHTML = `
            <div id="content"><div class="document" data-book-initials="MHC" data-osis-ref="Gen.1">
                <span class="ordinal" id="o-5">Selected paragraph</span>
            </div></div>`;
        select(document.getElementById("o-5"));

        const selection = querySelection();
        expect(typeof selection).toBe("object");
        expect(selection.bookInitials).toBe("MHC");
        expect(selection.startOrdinal).toBe(5);
        expect(selection.text).toBe("Selected paragraph");
    });

    it("returns null without a selection", () => {
        expect(querySelection()).toBeNull();
    });
});
