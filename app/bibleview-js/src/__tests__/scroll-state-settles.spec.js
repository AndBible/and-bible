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

// #3697: the title bar verse froze while scrolling. isScrolling gates the verse notifier
// (verse-notifier.ts), so every programmatic scroll must end with isScrolling false again,
// also when an instant scroll (page down, volume key, config change) interrupts an animation.
// Only a touch used to clear the stuck state; keyboard, volume key and wheel scrolling never did.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {defineComponent, ref} from "vue";
import {useScroll} from "@/composables/scroll";
import {emit} from "@/eventbus";

let wrapper;
let scroll;
let frames;

function mountScroll(appSettings = {topOffset: 0, bottomOffset: 0, disableAnimations: false}) {
    const Host = defineComponent({
        setup() {
            scroll = useScroll(
                {},
                appSettings,
                ref({topOffset: 0}),
                {highlightOrdinal: () => {}, resetHighlights: () => {}},
                ref(null),
            );
            return {};
        },
        template: `<div/>`,
    });
    wrapper = mount(Host, {attachTo: document.body});
}

/** Runs the queued animation frames once, at [time] ms. */
function runFrames(time) {
    const queued = frames;
    frames = new Map();
    for (const cb of queued.values()) cb(time);
}

beforeEach(() => {
    vi.useFakeTimers();
    frames = new Map();
    let nextId = 1;
    vi.stubGlobal("requestAnimationFrame", cb => { const id = nextId++; frames.set(id, cb); return id; });
    vi.stubGlobal("cancelAnimationFrame", id => { frames.delete(id); });
    window.scrollTo = vi.fn();
});

afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.unstubAllGlobals();
    vi.useRealTimers();
});

describe("programmatic scroll state settles (#3697)", () => {
    it("an instant scroll during an animation ends the scrolling state", () => {
        mountScroll();
        scroll.doScrolling(500, 1000);
        runFrames(0);
        expect(scroll.isScrolling.value).toBe(true);

        scroll.doScrolling(900, 0); // page down / volume key while the animation runs
        vi.advanceTimersByTime(1000);
        runFrames(1000);

        expect(window.scrollTo).toHaveBeenLastCalledWith(0, 900);
        expect(scroll.isScrolling.value).toBe(false);
    });

    it("a page-down event interrupting a toolbar offset animation does not freeze the state", () => {
        mountScroll();
        emit("set_offsets", 100, 0); // toolbar hides: 500 ms compensation animation
        runFrames(0);
        expect(scroll.isScrolling.value).toBe(true);

        scroll.doScrolling(800, 0);
        vi.advanceTimersByTime(1000);
        runFrames(1000);

        expect(scroll.isScrolling.value).toBe(false);
    });

    it("several instant scrolls in a row after an animation settle", () => {
        mountScroll({topOffset: 0, bottomOffset: 0, disableAnimations: false});
        scroll.doScrolling(500, 1000);
        runFrames(0);
        scroll.doScrolling(700, 0);
        scroll.doScrolling(750, 0);
        vi.advanceTimersByTime(1000);
        expect(scroll.isScrolling.value).toBe(false);
    });

    it("a new animation that replaces an instant scroll still runs and then settles", () => {
        mountScroll();
        scroll.doScrolling(500, 1000);
        runFrames(0);
        scroll.doScrolling(600, 0);
        scroll.doScrolling(1000, 200);
        runFrames(10);
        expect(scroll.isScrolling.value).toBe(true);
        runFrames(300);
        expect(window.scrollTo).toHaveBeenLastCalledWith(0, 1000);
        vi.advanceTimersByTime(1000);
        expect(scroll.isScrolling.value).toBe(false);
    });
});
