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

// #3975: content requested for the previous document must never be inserted into the next one. The page stamps its
// calls with the generation it holds at send time, so an old chapter inserted after clear_document would report its
// verses with the NEW generation and native could not tell them apart. These tests run the real infinite-scroll
// composable.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, reactive} from "vue";
import {useInfiniteScroll} from "@/composables/infinite-scroll";

function deferred() {
    let resolve;
    const promise = new Promise(r => { resolve = r; });
    return {promise, resolve};
}

let wrapper;
let scroll;
let documents;
let topRequests;
let endRequests;

function mountScroll() {
    documents = reactive([{id: "old.John.3", type: "bible"}]);
    topRequests = [];
    endRequests = [];
    const android = {
        requestPreviousChapter: () => { const d = deferred(); topRequests.push(d); return d.promise; },
        requestNextChapter: () => { const d = deferred(); endRequests.push(d); return d.promise; },
    };
    const Host = defineComponent({
        setup() {
            scroll = useInfiniteScroll(android, documents, reactive({infiniteScroll: true}));
            return () => h("div", [h("div", {id: "bottom"})]);
        },
    });
    wrapper = mount(Host, {attachTo: document.body});
}

/** What BibleView's clear_document does to the composable and the document list, then the next document arrives. */
async function replaceDocument() {
    scroll.documentsCleared();
    documents.splice(0);
    documents.push({id: "new.Gen.1", type: "bible"});
    await flushPromises();
}

beforeEach(() => {
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    vi.stubGlobal("requestAnimationFrame", cb => setTimeout(cb, 0));
    mountScroll();
});

afterEach(() => {
    wrapper?.unmount();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("infinite scroll across clear_document (#3975)", () => {
    it("a previous chapter buffered during a touch is not inserted into the next document", async () => {
        scroll.loadTextAtTop();
        window.dispatchEvent(new Event("touchstart"));
        topRequests[0].resolve({id: "old.John.2", type: "bible"});
        await flushPromises();
        // The finger is down: the old chapter waits for touchend.
        expect(documents.map(d => d.id)).toEqual(["old.John.3"]);

        await replaceDocument();
        window.dispatchEvent(new Event("touchend"));
        await flushPromises();

        expect(documents.map(d => d.id)).toEqual(["new.Gen.1"]);
    });

    it("a load-more-at-top gesture of the previous document does not request content for the next one", async () => {
        window.dispatchEvent(new Event("touchstart"));
        window.dispatchEvent(new WheelEvent("wheel", {deltaY: -10}));
        expect(topRequests.length).toBe(0);

        await replaceDocument();
        window.dispatchEvent(new Event("touchend"));
        await flushPromises();

        expect(topRequests.length).toBe(0);
        expect(documents.map(d => d.id)).toEqual(["new.Gen.1"]);
    });

    it("a chapter answered after the clear is dropped, and paging works for the next document", async () => {
        scroll.loadTextAtTop();
        await replaceDocument();
        topRequests[0].resolve({id: "old.John.2", type: "bible"});
        await flushPromises();
        expect(documents.map(d => d.id)).toEqual(["new.Gen.1"]);

        scroll.loadTextAtTop();
        topRequests[1].resolve({id: "new.Gen.0", type: "bible"});
        await flushPromises();
        expect(documents.map(d => d.id)).toEqual(["new.Gen.0", "new.Gen.1"]);
    });
});
