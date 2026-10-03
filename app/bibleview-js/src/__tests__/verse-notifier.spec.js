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

import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {defineComponent, h, ref, computed} from "vue";
import {mount} from "@vue/test-utils";
import {useVerseNotifier} from "@/composables/verse-notifier";

const LINE_HEIGHT = 10;
// The guard only treats movement as "scrolling up" within 2 * lineHeight of the anchor,
// so upward distances in these tests must stay below this to exercise the guarded path.
const JITTER_WINDOW = 2 * LINE_HEIGHT;

// Mounted wrappers, unmounted in afterEach - setupWindowEventListener only detaches its
// scroll listener on unmount, so leaving them mounted would let earlier tests' notifiers
// keep handling every dispatched scroll event.
const wrappers = [];

// Builds a detached ".ordinal" element (as rendered by OSIS/Verse.vue) inside a ".document"
// container carrying the osisRef, matching what verse-notifier looks up via closest().
function makeOrdinalElement(ordinal, osisRef) {
    const doc = document.createElement("div");
    doc.className = "document";
    doc.dataset.osisRef = osisRef;
    const el = document.createElement("span");
    el.className = "ordinal";
    el.dataset.ordinal = String(ordinal);
    doc.appendChild(el);
    document.body.appendChild(doc);
    return el;
}

function mountNotifier() {
    let exposed;
    const scrolledToOrdinal = vi.fn();
    const calculatedConfig = ref({topOffset: 0, marginLeft: 0, marginRight: 0});
    const lineHeight = computed(() => LINE_HEIGHT);
    const isScrolling = ref(false);

    const TestComponent = defineComponent({
        setup() {
            exposed = useVerseNotifier(
                calculatedConfig,
                {scrolledToOrdinal},
                {isScrolling},
                lineHeight,
            );
            return () => h("div");
        },
    });

    const wrapper = mount(TestComponent);
    wrappers.push(wrapper);
    return {wrapper, isScrolling, scrolledToOrdinal, ...exposed};
}

// jsdom does not implement window.scrollTo()/scrollY - override scrollY with a plain
// writable property so the code under test can read a real, settable scroll position.
function setScrollY(y) {
    window.scrollY = y;
}

// Advances scroll and dispatches the scroll event; then advances fake timers past the
// verse-notifier throttle window (50ms) so the handler's leading-edge call runs synchronously
// and the throttle is reset for the next dispatch.
function scrollTo(y) {
    setScrollY(y);
    window.dispatchEvent(new Event("scroll"));
    vi.advanceTimersByTime(51);
}

describe("useVerseNotifier", () => {
    let elements;

    beforeEach(() => {
        vi.useFakeTimers();
        elements = [];
        document.body.innerHTML = "";
        document.elementFromPoint = vi.fn(() => elements[0] ?? null);
        Object.defineProperty(window, "scrollY", {value: 0, writable: true, configurable: true});
    });

    afterEach(() => {
        wrappers.splice(0).forEach(w => w.unmount());
        vi.useRealTimers();
        delete document.elementFromPoint;
        document.body.innerHTML = "";
    });

    it("does not advance the verse when scrolling up hits a superscript bleeding into the line above (#3865)", () => {
        setScrollY(200);
        const {currentVerse, currentKey} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(300); // scroll down onto verse 11
        expect(currentVerse.value).toBe(11);
        expect(currentKey.value).toBe("Rom.5");

        // Scrolling up now, but the probe's very first hit is verse 12 (superscript bleed).
        // The bleed happens within a line of the boundary, i.e. inside the jitter window.
        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(292);

        expect(currentVerse.value).toBe(11);
        expect(currentKey.value).toBe("Rom.5");
    });

    it("still allows the verse to decrease while scrolling up", () => {
        setScrollY(300);
        const {currentVerse} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(300);
        expect(currentVerse.value).toBe(11);

        elements = [makeOrdinalElement(10, "Rom.5")];
        scrollTo(250); // scrolling up onto an earlier verse is a legitimate decrease
        expect(currentVerse.value).toBe(10);
    });

    it("allows the verse to advance normally while scrolling down", () => {
        setScrollY(100);
        const {currentVerse} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(150);
        expect(currentVerse.value).toBe(11);

        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(200);
        expect(currentVerse.value).toBe(12);
    });

    it("does not guard across a document/osisRef change even when scrolling up", () => {
        setScrollY(300);
        const {currentVerse, currentKey} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(300);
        expect(currentVerse.value).toBe(11);

        // Different document (osisRef) reporting a higher ordinal - not comparable, must not be blocked.
        elements = [makeOrdinalElement(99, "Rom.6")];
        scrollTo(250);

        expect(currentVerse.value).toBe(99);
        expect(currentKey.value).toBe("Rom.6");
    });

    // Regression test for the real-device repro of #3865: real touch/momentum scrolling
    // produces many raw scroll samples between two verse changes, and individual samples
    // can jitter opposite to the net direction (device log showed scrollY going
    // 1201.14 -> ... -> 1191.62 net upward, yet an intermediate raw sample read even
    // lower than the final one, e.g. 1188 - a comparison against just that one prior
    // sample reads as "scrolling down" even though the verse was last confirmed higher
    // up at 1201.14). The guard must compare against the scrollY where the verse was
    // last CONFIRMED, not against the previous raw sample, or this exact jitter defeats it.
    it("blocks a forward jump even when an intermediate noisy sample looks like scrolling down", () => {
        setScrollY(1201.14);
        const {currentVerse, currentKey} = mountNotifier();

        elements = [makeOrdinalElement(29160, "Rom.5")]; // verse 14 confirmed here
        scrollTo(1201.14);
        expect(currentVerse.value).toBe(29160);

        // Intermediate noisy sample: still verse 14 is found, but at a scrollY briefly
        // lower than the eventual "bug" sample below - this is the jitter that fooled a
        // naive previous-sample-only comparison.
        elements = [makeOrdinalElement(29160, "Rom.5")];
        scrollTo(1188);
        expect(currentVerse.value).toBe(29160);

        // Net movement since verse 14 was confirmed (1201.14 -> 1191.62) is clearly
        // upward, even though this sample is higher than the immediately preceding one.
        elements = [makeOrdinalElement(29161, "Rom.5")]; // verse 15 - must stay blocked
        scrollTo(1191.62);

        expect(currentVerse.value).toBe(29160);
        expect(currentKey.value).toBe("Rom.5");
    });

    it("releases the guard once the user scrolls back down past the anchor", () => {
        setScrollY(300);
        const {currentVerse} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(300); // verse 11 confirmed, anchor = 300
        expect(currentVerse.value).toBe(11);

        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(295); // superscript bleed while scrolling up - blocked
        expect(currentVerse.value).toBe(11);

        scrollTo(305); // genuinely moving down again - the verse must follow
        expect(currentVerse.value).toBe(12);
    });

    // The guard must stay a jitter filter. Anything that moves scrollY out from under us
    // without producing a verse change (the resize handler's bare window.scrollTo, an
    // infinite-scroll insert at the top) leaves the anchor stale; an unbounded guard would
    // then reject every forward verse move until the user scrolled back past it.
    it("does not treat a large upward jump as scrolling up", () => {
        setScrollY(1000);
        const {currentVerse} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(1000);
        expect(currentVerse.value).toBe(11);

        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(1000 - JITTER_WINDOW - 1); // too far up to be a superscript bleed
        expect(currentVerse.value).toBe(12);
    });

    // Regression test for the freeze this guard could otherwise cause: config_changed
    // re-scrolls to the same verse after a text size change, and isScrolling suppresses verse
    // detection for the duration. With the anchor left in the pre-jump coordinate system, the
    // title bar would stay stuck on the old verse for the whole reflow distance.
    it("keeps following the user down after a programmatic scroll moved the document", () => {
        setScrollY(1000);
        const {currentVerse, isScrolling} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(1000); // verse 11 confirmed, anchor = 1000
        expect(currentVerse.value).toBe(11);

        // Text size decreased - the same verse now sits at a much smaller scrollY.
        isScrolling.value = true;
        scrollTo(600);
        isScrolling.value = false;

        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(650); // user scrolls down through the reflowed text
        expect(currentVerse.value).toBe(12);
    });

    it("re-anchors to where a programmatic scroll ended, not to the pre-jump position", () => {
        setScrollY(1000);
        const {currentVerse, isScrolling} = mountNotifier();

        elements = [makeOrdinalElement(11, "Rom.5")];
        scrollTo(1000);
        expect(currentVerse.value).toBe(11);

        isScrolling.value = true;
        scrollTo(600);
        isScrolling.value = false;

        // A bleed just above the new position must still be guarded - which only holds if the
        // anchor moved to 600. A stale 1000 anchor is far outside the jitter window, so the
        // guard would be inactive here and verse 12 would be accepted.
        elements = [makeOrdinalElement(12, "Rom.5")];
        scrollTo(595);
        expect(currentVerse.value).toBe(11);
    });
});
