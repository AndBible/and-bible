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

// Regression guard for #3634: expanding and closing the verse action dialog must not leave
// the Bible window in a state that blocks scrolling (modal still reported open to Android,
// a dialog left in the DOM, or a touch listener cancelling document scroll gestures).
import {afterEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, provide, reactive} from "vue";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import {useModal} from "@/composables/modal";
import {addEventFunction, addEventOrdinalInfo} from "@/utils";
import {
    androidKey, appSettingsKey, calculatedConfigKey, configKey, globalBookmarksKey, keyboardKey, modalKey, ordinalHighlightKey, stringsKey,
} from "@/types/constants";

let wrapper;
const originalObserver = window.ResizeObserver;

afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    document.body.innerHTML = "";
    window.ResizeObserver = originalObserver;
});

function mountVerseActions() {
    document.body.innerHTML = '<div id="modals"></div>';
    window.ResizeObserver = class {observe() {} disconnect() {}};
    const appSettings = reactive({topOffset: 0, bottomOffset: 0, limitAmbiguousModalSize: true});
    const android = {
        reportModalState: vi.fn(),
        setLimitAmbiguousModalSize: vi.fn(v => { appSettings.limitAmbiguousModalSize = v; }),
    };
    const bookmark = {id: "b", type: "bookmark", text: "verse", labels: [], originalOrdinalRange: [1, 1],
        createdAt: 0, lastUpdatedOn: 0};
    const host = defineComponent({
        setup() {
            provide(modalKey, useModal(android));
            return () => h(AmbiguousSelection);
        },
    });
    wrapper = mount(host, {
        attachTo: document.body,
        global: {
            provide: {
                [appSettingsKey]: appSettings,
                [configKey]: {showBookmarks: true},
                [calculatedConfigKey]: {},
                [androidKey]: android,
                [stringsKey]: new Proxy({}, {get: (_, key) => String(key)}),
                [keyboardKey]: {setupKeyboardListener() {}},
                [ordinalHighlightKey]: {resetHighlights() {}, highlightOrdinal() {}, hasHighlights: {value: false}},
                [globalBookmarksKey]: {
                    bookmarkMap: new Map([["b", bookmark]]),
                    bookmarkLabels: new Map(),
                    bookmarkIdsByOrdinal: new Map(),
                },
            },
            stubs: {FontAwesomeIcon: true, AmbiguousActionButtons: true, AmbiguousSelectionBookmarkButton: true},
        },
    });
    return {android, appSettings, selection: wrapper.findComponent(AmbiguousSelection)};
}

function touch(type, target) {
    const event = new Event(type, {bubbles: true, cancelable: true});
    event.touches = [{clientX: 10, clientY: 100}];
    target.dispatchEvent(event);
    return event;
}

/** A vertical swipe on the Bible text; returns whether page JS cancelled any of its events. */
function swipeOnText() {
    const text = document.createElement("p");
    document.body.appendChild(text);
    try {
        return [touch("touchstart", text), touch("touchmove", text), touch("touchend", text)]
            .some(e => e.defaultPrevented);
    } finally {
        text.remove();
    }
}

describe("verse action dialog expand + close (#3634)", () => {
    it.each([
        ["close button", () => document.querySelector(".modal-toolbar button:last-child").click()],
        ["Escape", () => document.dispatchEvent(new KeyboardEvent("keyup", {key: "Escape"}))],
    ])("leaves the window scrollable after closing with %s", async (_name, close) => {
        const {android, appSettings, selection} = mountVerseActions();

        const click = new MouseEvent("click");
        addEventOrdinalInfo(click, {ordinal: 1, osisRef: "BIBLE"});
        addEventFunction(click, null, {bookmarkId: "b", priority: 0});
        const handled = selection.vm.handle(click);
        await flushPromises();
        expect(document.querySelector(".modal-content")).not.toBeNull();
        expect(android.reportModalState).toHaveBeenLastCalledWith(true);

        // Drag the header a bit, as a finger landing on it does, then expand the dialog.
        const header = document.querySelector(".modal-header");
        touch("touchstart", header);
        touch("touchmove", document);
        touch("touchend", document);
        const expand = [...document.querySelectorAll(".modal-toolbar button")]
            .find(b => b.innerHTML.includes("expand-arrows-alt"));
        expect(expand).toBeTruthy();
        expand.click();
        await flushPromises();
        expect(appSettings.limitAmbiguousModalSize).toBe(false);
        expect(document.querySelector(".modal-content.limit")).toBeNull();

        close();
        await handled;
        await flushPromises();

        expect(document.querySelector(".modal-content")).toBeNull();
        expect(document.querySelector(".modal-backdrop")).toBeNull();
        expect(android.reportModalState).toHaveBeenLastCalledWith(false);
        expect(swipeOnText()).toBe(false);
    });
});
