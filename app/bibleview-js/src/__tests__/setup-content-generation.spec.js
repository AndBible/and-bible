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

// #3975 (codex review C1): the setup_content of a replaced document awaits that document's render. If the document is
// replaced meanwhile, the old continuation must not scroll the NEW content: it would move the page to a same-ordinal
// element of the new document (or to the top), and the resulting scroll report carries the new generation, so native
// could not tell it apart from a genuine one. Runs the real useScroll and infinite-scroll composables.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, reactive, ref, watch} from "vue";
import {useScroll} from "@/composables/scroll";
import {useInfiniteScroll} from "@/composables/infinite-scroll";
import {emit} from "@/eventbus";
import {adoptDocumentGeneration, resetDocumentGeneration} from "@/bridge/document-generation";

function deferred() {
    let resolve;
    const promise = new Promise(r => { resolve = r; });
    return {promise, resolve};
}

/** An element at document y [top]. */
function place(id, top) {
    const el = document.createElement("div");
    el.id = id;
    el.style.lineHeight = "20px";
    el.style.fontSize = "20px";
    el.innerText = "";
    el.getBoundingClientRect = () => ({top: top - window.scrollY, bottom: top + 20 - window.scrollY, left: 0, right: 100, width: 100, height: 20});
    document.body.appendChild(el);
    return el;
}

const setup = {jumpToOrdinal: 5, jumpToAnchor: null, jumpToId: null, topOffset: 0, bottomOffset: 0};

let wrapper;

beforeEach(() => {
    resetDocumentGeneration();
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    vi.stubGlobal("requestAnimationFrame", cb => setTimeout(cb, 0));
    vi.stubGlobal("cancelAnimationFrame", id => clearTimeout(id));
});

afterEach(() => {
    wrapper?.unmount();
    document.body.innerHTML = "";
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("setup_content of a replaced document (#3975)", () => {
    it("does not scroll the next document once the old one is replaced", async () => {
        const oldRender = deferred();
        const documentPromise = ref(oldRender.promise);
        wrapper = mount(defineComponent({
            setup() {
                useScroll({}, {disableAnimations: true}, ref({topOffset: 0}),
                    {highlightOrdinal: () => {}, resetHighlights: () => {}}, documentPromise);
                return () => h("div");
            },
        }), {attachTo: document.body});
        place("top", 0);

        // The old document's setup waits for its render.
        emit("setup_content", setup);
        await flushPromises();

        // Native replaces the document (clear_document) before that render finished; the new content has verse 5 too.
        await adoptDocumentGeneration(7);
        documentPromise.value = Promise.resolve();
        place("o-5", 400);
        window.scrollTo.mockClear();

        oldRender.resolve();
        await flushPromises();
        await new Promise(r => setTimeout(r, 0));

        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("the current document's setup still scrolls", async () => {
        const documentPromise = ref(Promise.resolve());
        wrapper = mount(defineComponent({
            setup() {
                useScroll({}, {disableAnimations: true}, ref({topOffset: 0}),
                    {highlightOrdinal: () => {}, resetHighlights: () => {}}, documentPromise);
                return () => h("div");
            },
        }), {attachTo: document.body});
        place("top", 0);
        await adoptDocumentGeneration(7);
        place("o-5", 400);

        emit("setup_content", setup);
        await flushPromises();

        expect(window.scrollTo).toHaveBeenCalledWith(0, 400);
    });

    it("infinite scroll's top insert does not correct the scroll position of the next document", async () => {
        const documents = reactive([{id: "old.John.3", type: "bible"}]);
        const tops = [];
        let scroll;
        wrapper = mount(defineComponent({
            setup() {
                scroll = useInfiniteScroll({
                    requestPreviousChapter: () => { const d = deferred(); tops.push(d); return d.promise; },
                    requestNextChapter: () => new Promise(() => {}),
                }, documents, reactive({infiniteScroll: true}));
                return () => h("div", [h("div", {id: "bottom"})]);
            },
        }), {attachTo: document.body});

        scroll.loadTextAtTop();
        // Native replaces the document right after the old chapter was inserted, while its scroll correction still
        // waits for the render.
        const stop = watch(() => documents.length, () => {
            if (documents[0]?.id !== "old.John.2") return;
            stop();
            scroll.documentsCleared();
            documents.splice(0);
            documents.push({id: "new.Gen.1", type: "bible"});
            window.scrollTo.mockClear();
        }, {flush: "sync"});
        tops[0].resolve({id: "old.John.2", type: "bible"});
        await flushPromises();
        await new Promise(r => setTimeout(r, 0));

        expect(documents.map(d => d.id)).toEqual(["new.Gen.1"]);
        expect(window.scrollTo).not.toHaveBeenCalled();
    });
});
