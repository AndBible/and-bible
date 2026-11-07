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

// #4026: the desktop off-screen (CEF) pane starts as a 1 x 1 DIP view until Compose lays it out. When the document
// was set up before that, the initial scroll was computed for a one-pixel-wide layout (every word on its own line, the
// verse thousands of pixels down) and kept after the real size arrived: the window opened several verses past its
// position (Ecclesiastes 4:1 showed 4:9), or blank, scrolled into the bottom padding. setup_content now waits for a
// usable viewport and scrolls for the layout the reader will see.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, ref} from "vue";
import {useScroll} from "@/composables/scroll";
import {emit} from "@/eventbus";
import {adoptDocumentGeneration, resetDocumentGeneration} from "@/bridge/document-generation";

/** Document y of each element by id; changes with the viewport like a real reflow. */
const layout = {};

function place(id) {
    const el = document.createElement("div");
    el.id = id;
    el.style.lineHeight = "20px";
    el.style.fontSize = "20px";
    el.innerText = "";
    el.getBoundingClientRect = () => {
        const top = layout[id] - window.scrollY;
        return {top, bottom: top + 20, left: 0, right: 100, width: 100, height: 20};
    };
    document.body.appendChild(el);
    return el;
}

function setViewport(width, height) {
    Object.defineProperty(window, "innerWidth", {configurable: true, value: width});
    Object.defineProperty(window, "innerHeight", {configurable: true, value: height});
}

const setup = {jumpToOrdinal: 5, jumpToAnchor: null, jumpToId: null, topOffset: 0, bottomOffset: 0};
let wrapper;

function mountScroll() {
    wrapper = mount(defineComponent({
        setup() {
            useScroll({}, {disableAnimations: true}, ref({topOffset: 0}),
                {highlightOrdinal: () => {}, resetHighlights: () => {}}, ref(Promise.resolve()));
            return () => h("div");
        },
    }), {attachTo: document.body});
}

async function settle() {
    await flushPromises();
    await new Promise(r => setTimeout(r, 0));
    await flushPromises();
}

beforeEach(() => {
    resetDocumentGeneration();
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    vi.stubGlobal("requestAnimationFrame", cb => setTimeout(cb, 0));
    vi.stubGlobal("cancelAnimationFrame", id => clearTimeout(id));
    layout.top = 0;
});

afterEach(() => {
    wrapper?.unmount();
    document.body.innerHTML = "";
    setViewport(1024, 768);
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("setup_content before the pane has its size (#4026)", () => {
    it("scrolls to the verse of the real layout, not of the 1 x 1 placeholder view", async () => {
        setViewport(1, 1);
        mountScroll();
        place("top");
        place("o-5");
        layout["o-5"] = 5200; // one word per line in a one-pixel-wide view

        emit("setup_content", setup);
        await settle();

        // The pane gets its size: the page reflows and the verse is near the top.
        setViewport(530, 730);
        layout["o-5"] = 400;
        window.dispatchEvent(new Event("resize"));
        await settle();

        expect(window.scrollTo).not.toHaveBeenCalledWith(0, 5200);
        expect(window.scrollTo).toHaveBeenLastCalledWith(0, 400);
    });

    it("drops the wait when the document is replaced before the pane got its size", async () => {
        setViewport(1, 1);
        mountScroll();
        place("top");
        place("o-5");
        layout["o-5"] = 5200;

        emit("setup_content", setup);
        await settle();
        await adoptDocumentGeneration(9);

        setViewport(530, 730);
        layout["o-5"] = 400;
        window.dispatchEvent(new Event("resize"));
        await settle();

        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("scrolls at once in a pane that already has its size", async () => {
        setViewport(530, 730);
        mountScroll();
        place("top");
        place("o-5");
        layout["o-5"] = 400;

        emit("setup_content", setup);
        await settle();

        expect(window.scrollTo).toHaveBeenCalledWith(0, 400);
    });
});
