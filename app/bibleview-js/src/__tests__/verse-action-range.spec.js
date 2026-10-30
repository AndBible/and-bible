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
import {afterEach, describe, expect, it} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, provide, reactive} from "vue";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import {useModal} from "@/composables/modal";
import {addEventVerseInfo} from "@/utils";
import {
    androidKey, appSettingsKey, calculatedConfigKey, configKey, globalBookmarksKey,
    keyboardKey, modalKey, ordinalHighlightKey, stringsKey,
} from "@/types/constants";

let wrapper;
const originalObserver = window.ResizeObserver;

afterEach(() => {
    wrapper?.unmount();
    document.body.innerHTML = "";
    window.ResizeObserver = originalObserver;
});

function mountVerseActions() {
    document.body.innerHTML = '<div id="modals"></div>';
    window.ResizeObserver = class {observe() {} disconnect() {}};
    const android = {reportModalState() {}};
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
                [appSettingsKey]: reactive({activeWindow: true, activeSince: -1000,
                    topOffset: 0, bottomOffset: 0, limitAmbiguousModalSize: true}),
                [configKey]: {showBookmarks: true},
                [calculatedConfigKey]: {},
                [androidKey]: android,
                [stringsKey]: new Proxy({}, {get: (_, key) => String(key)}),
                [keyboardKey]: {setupKeyboardListener() {}},
                [ordinalHighlightKey]: {resetHighlights() {}, highlightOrdinal() {}, hasHighlights: {value: false}},
                [globalBookmarksKey]: {bookmarkMap: new Map(), bookmarkLabels: new Map(), bookmarkIdsByOrdinal: new Map()},
            },
            stubs: {FontAwesomeIcon: true, AmbiguousActionButtons: true, AmbiguousSelectionBookmarkButton: true},
        },
    });
    return wrapper.findComponent(AmbiguousSelection);
}

function clickVerse(selection, chapter, verse, ordinal) {
    const click = new MouseEvent("click");
    addEventVerseInfo(click, {
        bookInitials: "KJV", bibleBookName: "John", v11n: "KJV", book: "John", chapter, verse, ordinal,
        osisID: `John.${chapter}.${verse}`,
        bibleDocumentInfo: {bookInitials: "KJV", bibleBookName: "John", v11n: "KJV", ordinalRange: [26739, 26809]},
    });
    return selection.vm.handle(click);
}

function title() {
    return document.querySelector(".modal-header .title").textContent.trim();
}

// Removing the endpoint from the title must break these user-visible assertions.
describe("verse action dialog range title (#1492)", () => {
    it("shows a single verse", async () => {
        const selection = mountVerseActions();
        const handled = clickVerse(selection, 6, 39, 26777);
        await flushPromises();
        expect(title()).toBe("John 6:39");
        document.querySelector(".modal-toolbar button:last-child").click();
        await handled;
    });

    it("shows the selected same-chapter range", async () => {
        const selection = mountVerseActions();
        const handled = clickVerse(selection, 6, 39, 26777);
        await flushPromises();
        document.querySelector(".modal-toolbar button").click();
        await flushPromises();
        await clickVerse(selection, 6, 42, 26780);
        await flushPromises();
        expect(title()).toBe("John 6:39-42");
        document.querySelector(".modal-toolbar button:last-child").click();
        await handled;
    });
});
