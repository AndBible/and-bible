/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

// #3043: a link tapped near the bottom opens the links window, which shrinks this
// window's viewport. The tapped link itself must stay visible.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, ref} from "vue";
import {useScroll} from "@/composables/scroll";
import {emit} from "@/eventbus";

const TOP_OFFSET = 50;

let scrollY = 0;
let innerHeight = 1000;

function setInnerHeight(h) {
    innerHeight = h;
}

/** Element whose bounding rect is derived from a fixed document position and the current scroll. */
function placeElement(el, docTop, height) {
    el.getBoundingClientRect = () => ({
        top: docTop - scrollY,
        bottom: docTop + height - scrollY,
        left: 0, right: 100, width: 100, height,
    });
}

function mountScroll(appSettings) {
    const Host = defineComponent({
        setup() {
            useScroll(
                {},
                appSettings,
                ref({topOffset: TOP_OFFSET}),
                {highlightOrdinal: () => {}, resetHighlights: () => {}},
                ref(null),
            );
            return {};
        },
        template: `<div/>`,
    });
    return mount(Host, {attachTo: document.body});
}

/** A verse spanning document y 400..1000, with the tapped link at its end (950..970). */
function buildLongVerse() {
    const verse = document.createElement("span");
    verse.className = "ordinal";
    const link = document.createElement("a");
    link.textContent = "Gen 1:1";
    verse.appendChild(link);
    document.body.appendChild(verse);
    placeElement(verse, 400, 600);
    placeElement(link, 950, 20);
    return {verse, link};
}

async function resize(height) {
    setInnerHeight(height);
    window.dispatchEvent(new Event("resize"));
    await flushPromises();
}

function linkTopInView(link) {
    return link.getBoundingClientRect().top;
}

function expectLinkVisible(link, appSettings) {
    const rect = link.getBoundingClientRect();
    expect(rect.top).toBeGreaterThanOrEqual(TOP_OFFSET);
    expect(rect.bottom).toBeLessThanOrEqual(innerHeight - appSettings.bottomOffset);
}

describe("scroll anchor of a tapped link (#3043)", () => {
    let wrapper;
    let appSettings;

    beforeEach(() => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        scrollY = 0;
        innerHeight = 1000;
        vi.spyOn(window, "innerHeight", "get").mockImplementation(() => innerHeight);
        vi.spyOn(window, "scrollY", "get").mockImplementation(() => scrollY);
        vi.spyOn(window, "scrollTo").mockImplementation((x, y) => {
            scrollY = y;
        });
        vi.spyOn(window, "requestAnimationFrame").mockImplementation(cb => {
            cb(0);
            return 1;
        });
        appSettings = {topOffset: TOP_OFFSET, bottomOffset: 0, disableAnimations: false};
    });

    afterEach(() => {
        wrapper?.unmount();
        document.body.innerHTML = "";
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it("scrolls the link into view when its verse stays partly visible", async () => {
        wrapper = mountScroll(appSettings);
        const {link} = buildLongVerse();
        expect(linkTopInView(link)).toBeLessThan(1000);

        emit("set_scroll_anchor", link);
        await resize(500);

        expectLinkVisible(link, appSettings);
    });

    it("scrolls a link that is only partly cut off by the new bottom edge", async () => {
        wrapper = mountScroll(appSettings);
        const link = document.createElement("a");
        document.body.appendChild(link);
        placeElement(link, 490, 20);

        emit("set_scroll_anchor", link);
        await resize(500);

        expectLinkVisible(link, appSettings);
    });

    it("keeps the link clear of the bottom toolbar offset", async () => {
        appSettings.bottomOffset = 80;
        wrapper = mountScroll(appSettings);
        const link = document.createElement("a");
        document.body.appendChild(link);
        placeElement(link, 440, 20);

        emit("set_scroll_anchor", link);
        await resize(500);

        expectLinkVisible(link, appSettings);
    });

    it("still keeps the link visible when the viewport shrinks in several steps", async () => {
        wrapper = mountScroll(appSettings);
        const {link} = buildLongVerse();

        emit("set_scroll_anchor", link);
        await resize(990); // link still visible after the first, small step
        expect(window.scrollTo).not.toHaveBeenCalled();
        await resize(500);

        expectLinkVisible(link, appSettings);
    });

    it("does not scroll when the link remains visible", async () => {
        wrapper = mountScroll(appSettings);
        const link = document.createElement("a");
        document.body.appendChild(link);
        placeElement(link, 200, 20);

        emit("set_scroll_anchor", link);
        await resize(500);

        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("forgets the anchor after its time window", async () => {
        wrapper = mountScroll(appSettings);
        const {link} = buildLongVerse();

        emit("set_scroll_anchor", link);
        vi.advanceTimersByTime(5000);
        await resize(500);

        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("falls back to the verse when the tapped link is no longer in the document", async () => {
        wrapper = mountScroll(appSettings);
        const {verse, link} = buildLongVerse();
        placeElement(verse, 900, 40);

        emit("set_scroll_anchor", link);
        link.remove();
        await resize(500);

        const rect = verse.getBoundingClientRect();
        expect(rect.top).toBeGreaterThanOrEqual(TOP_OFFSET);
        expect(rect.top).toBeLessThan(500);
    });
});
