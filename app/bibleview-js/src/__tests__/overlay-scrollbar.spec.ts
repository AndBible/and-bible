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

import {mount} from "@vue/test-utils";
import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {nextTick} from "vue";
import {scrollTopForThumbTop, thumbGeometry} from "@/code/overlay-scrollbar";
import OverlayScrollbar from "@/components/OverlayScrollbar.vue";

describe("overlay scrollbar geometry", () => {
    const base = {viewportHeight: 1000, trackTop: 0, trackHeight: 1000, minThumb: 24};

    it("hides the thumb when the page does not overflow", () => {
        expect(thumbGeometry({...base, scrollTop: 0, scrollHeight: 1000})).toBeNull();
        expect(thumbGeometry({...base, scrollTop: 0, scrollHeight: 900})).toBeNull();
    });

    it("sizes the thumb by the visible fraction and places it by the scrolled fraction", () => {
        expect(thumbGeometry({...base, scrollTop: 0, scrollHeight: 4000})).toEqual({top: 0, height: 250});
        expect(thumbGeometry({...base, scrollTop: 3000, scrollHeight: 4000})).toEqual({top: 750, height: 250});
        expect(thumbGeometry({...base, scrollTop: 1500, scrollHeight: 4000})).toEqual({top: 375, height: 250});
    });

    it("keeps the thumb inside the track between the toolbar insets", () => {
        const g = thumbGeometry({...base, trackTop: 80, trackHeight: 820, scrollTop: 3000, scrollHeight: 4000})!;
        expect(g.top + g.height).toBeCloseTo(900);
        expect(thumbGeometry({...base, trackTop: 80, trackHeight: 820, scrollTop: 0, scrollHeight: 4000})!.top).toBe(80);
    });

    it("never makes the thumb smaller than the minimum on a very long page", () => {
        const g = thumbGeometry({...base, scrollTop: 999000, scrollHeight: 1000000})!;
        expect(g.height).toBe(24);
        expect(g.top + g.height).toBeCloseTo(1000);
    });

    it("clamps overscroll (rubber band) to the track ends", () => {
        expect(thumbGeometry({...base, scrollTop: -200, scrollHeight: 4000})!.top).toBe(0);
        expect(thumbGeometry({...base, scrollTop: 5000, scrollHeight: 4000})!.top).toBe(750);
    });

    it("maps a dragged thumb position back to the scroll offset", () => {
        const g = {...base, scrollHeight: 4000};
        expect(scrollTopForThumbTop(g, 375)).toBe(1500);
        expect(scrollTopForThumbTop(g, -50)).toBe(0);
        expect(scrollTopForThumbTop(g, 2000)).toBe(3000);
    });
});

describe("OverlayScrollbar.vue", () => {
    let scrollHeight = 4000;
    const scrolls: number[] = [];
    const originalScrollTo = window.scrollTo;
    let fine = true;
    let changeListeners: Array<() => void> = [];

    beforeEach(() => {
        fine = true;
        changeListeners = [];
        window.matchMedia = ((q: string) => ({
            get matches() { return q === "(any-pointer: fine)" && fine; },
            media: q,
            addEventListener: (_: string, l: () => void) => changeListeners.push(l),
            removeEventListener: (_: string, l: () => void) => { changeListeners = changeListeners.filter(x => x !== l); },
        })) as unknown as typeof window.matchMedia;
        scrollHeight = 4000;
        scrolls.length = 0;
        Object.defineProperty(document.documentElement, "scrollHeight", {configurable: true, get: () => scrollHeight});
        Object.defineProperty(window, "innerHeight", {configurable: true, value: 1000});
        Object.defineProperty(window, "scrollY", {configurable: true, writable: true, value: 0});
        window.scrollTo = ((x: number, y: number) => {
            scrolls.push(y);
            (window as any).scrollY = y;
            window.dispatchEvent(new Event("scroll"));
        }) as typeof window.scrollTo;
    });
    afterEach(() => {
        window.scrollTo = originalScrollTo;
        delete (document.documentElement as any).scrollHeight;
        delete (window as any).matchMedia;
        document.body.style.userSelect = "";
    });

    /** jsdom has no PointerEvent; a plain Event with the pointer fields is what the component reads. */
    function pointer(type: string, fields: Record<string, unknown>): Event {
        const e = new Event(type, {bubbles: true, cancelable: true});
        for (const [k, v] of Object.entries(fields)) Object.defineProperty(e, k, {value: v});
        return e;
    }

    function thumbStyle(wrapper: ReturnType<typeof mount>) {
        return (wrapper.find(".overlay-scrollbar-thumb").element as HTMLElement).style;
    }

    it("follows the page scroll position", async () => {
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        expect(thumbStyle(wrapper).height).toBe("250px");
        expect(thumbStyle(wrapper).transform).toBe("translateY(0px)");

        (window as any).scrollY = 1500;
        window.dispatchEvent(new Event("scroll"));
        await new Promise(r => requestAnimationFrame(() => r(null)));
        await nextTick();
        expect(thumbStyle(wrapper).transform).toBe("translateY(375px)");
        wrapper.unmount();
    });

    it("is not rendered when the page does not overflow", async () => {
        scrollHeight = 800;
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        expect(wrapper.find(".overlay-scrollbar-thumb").exists()).toBe(false);
        wrapper.unmount();
    });

    it("scrolls the page when the thumb is dragged with a mouse", async () => {
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        const thumb = wrapper.find(".overlay-scrollbar-thumb").element;
        thumb.dispatchEvent(pointer("pointerdown", {pointerType: "mouse", button: 0, clientY: 100, pointerId: 1}));
        window.dispatchEvent(pointer("pointermove", {clientY: 200, pointerId: 1}));
        expect(scrolls.at(-1)).toBe(400);
        window.dispatchEvent(pointer("pointerup", {pointerId: 1}));
        window.dispatchEvent(pointer("pointermove", {clientY: 600, pointerId: 1}));
        expect(scrolls.at(-1)).toBe(400);
        wrapper.unmount();
    });

    it("leaves touches alone so finger scrolling and taps reach the page", async () => {
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        const thumb = wrapper.find(".overlay-scrollbar-thumb").element;
        const down = pointer("pointerdown", {pointerType: "touch", button: 0, clientY: 100, pointerId: 2});
        thumb.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(false);
        window.dispatchEvent(pointer("pointermove", {clientY: 300, pointerId: 2}));
        expect(scrolls).toEqual([]);
        wrapper.unmount();
    });

    it("is hidden without a fine pointer and appears when one connects", async () => {
        fine = false;
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        expect(wrapper.find(".overlay-scrollbar").exists()).toBe(false);
        expect(wrapper.find(".overlay-scrollbar-thumb").exists()).toBe(false);

        fine = true;
        changeListeners.forEach(l => l());
        await nextTick();
        expect(wrapper.find(".overlay-scrollbar-thumb").exists()).toBe(true);
        wrapper.unmount();
    });

    it("captures the pointer and suppresses text selection during a drag, then restores both", async () => {
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        const thumb = wrapper.find(".overlay-scrollbar-thumb").element as HTMLElement;
        const captured: number[] = [];
        const released: number[] = [];
        thumb.setPointerCapture = (id: number) => { captured.push(id); };
        thumb.releasePointerCapture = (id: number) => { released.push(id); };
        document.body.style.userSelect = "text";

        thumb.dispatchEvent(pointer("pointerdown", {pointerType: "mouse", button: 0, clientY: 100, pointerId: 7}));
        expect(captured).toEqual([7]);
        expect(document.body.style.userSelect).toBe("none");

        window.dispatchEvent(pointer("pointerup", {pointerId: 7}));
        expect(released).toEqual([7]);
        expect(document.body.style.userSelect).toBe("text");
        wrapper.unmount();
    });

    it("releases capture and selection when the drag is cancelled", async () => {
        const wrapper = mount(OverlayScrollbar, {props: {topInset: 0, bottomInset: 0}, attachTo: document.body});
        await nextTick();
        const thumb = wrapper.find(".overlay-scrollbar-thumb").element as HTMLElement;
        const released: number[] = [];
        thumb.releasePointerCapture = (id: number) => { released.push(id); };
        thumb.dispatchEvent(pointer("pointerdown", {pointerType: "mouse", button: 0, clientY: 100, pointerId: 3}));
        window.dispatchEvent(pointer("pointercancel", {pointerId: 3}));
        expect(released).toEqual([3]);
        expect(document.body.style.userSelect).toBe("");
        wrapper.unmount();
    });
});
