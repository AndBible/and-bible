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

// #4118: Back in a Memorize window must close its open popup (the overflow menu, the Scramble/Type settings gear)
// instead of going past it. Native Back consumes the press only when the page reported a modal open
// (reportModalState(true)) and then closes it with the close_modals event.

import {mount} from "@vue/test-utils";
import {describe, it, expect, vi, afterEach} from "vitest";
import {defineComponent, h, nextTick, provide, reactive} from "vue";
import MemorizeDocument from "@/components/documents/MemorizeDocument.vue";
import WordScramble from "@/components/memorize/WordScramble.vue";
import WordType from "@/components/memorize/WordType.vue";
import {memorizationKey, modalKey, readingProgressSettingsKey} from "@/types/constants";
import {useModal} from "@/composables/modal";
import {emit} from "@/eventbus";

globalThis.ResizeObserver = vi.fn().mockImplementation(function () {
    this.observe = vi.fn();
    this.unobserve = vi.fn();
    this.disconnect = vi.fn();
});

vi.mock("@/composables", () => ({
    useCommon: () => ({
        strings: new Proxy({}, {get: (_, key) => String(key)}),
        android: {saveState: vi.fn(), showHelpDialog: vi.fn()},
        appSettings: {disableBibleModalButtons: [], speakSupported: true},
    }),
}));

let wrapper = null;
afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
});

/** Mounts [component] under a host that provides the real modal registry, as BibleView.vue does. */
function mountWithModals(component, props, shown = reactive({value: true})) {
    const android = {reportModalState: vi.fn()};
    const Host = defineComponent({
        setup() {
            provide(modalKey, useModal(android));
            return () => shown.value ? h(component, props) : null;
        },
    });
    wrapper = mount(Host, {
        attachTo: document.body,
        global: {
            provide: {
                [memorizationKey]: {
                    memorized: new Set(), targets: new Set(),
                    mergeData: vi.fn(), setupIndicatorRendering: vi.fn(),
                },
                [readingProgressSettingsKey]: {
                    settings: reactive({
                        autoMarkMemorized: true, memorizeTypeFullWords: false, memorizeWordVisibility: "light",
                        memorizeErrorHeatmap: true, memorizeScrambleHideUsed: false,
                    }),
                    updateSettings: vi.fn(),
                },
            },
            stubs: {WordBlur: true},
        },
    });
    return {wrapper, android};
}

const textItems = [{key: "v1", text: "For God so loved the world"}];

describe("Memorize popups close on Back (#4118)", () => {
    it("the overflow menu reports itself open and closes on close_modals", async () => {
        const {wrapper, android} = mountWithModals(MemorizeDocument, {
            document: {
                id: "doc1", type: "memorize", title: "John 3:16", texts: textItems,
                state: {memorize: {mode: "blur", modeConfig: {}}},
            },
        });
        await wrapper.find(".menu-trigger").trigger("click");
        expect(wrapper.find(".dropdown-menu").exists()).toBe(true);
        expect(android.reportModalState).toHaveBeenLastCalledWith(true);

        emit("close_modals");
        await nextTick();
        expect(wrapper.find(".dropdown-menu").exists()).toBe(false);
        expect(android.reportModalState).toHaveBeenLastCalledWith(false);
    });

    for (const [name, component] of [["WordScramble", WordScramble], ["WordType", WordType]]) {
        it(`the ${name} settings popup reports itself open and closes on close_modals`, async () => {
            const {wrapper, android} = mountWithModals(component, {textItems, modeConfig: undefined});
            await wrapper.find(".settings-trigger").trigger("click");
            expect(wrapper.find(".settings-popup").exists()).toBe(true);
            expect(android.reportModalState).toHaveBeenLastCalledWith(true);

            emit("close_modals");
            await nextTick();
            expect(wrapper.find(".settings-popup").exists()).toBe(false);
            expect(android.reportModalState).toHaveBeenLastCalledWith(false);
        });
    }

    it("closing a popup by tapping its trigger again reports the page modal-free", async () => {
        const {wrapper, android} = mountWithModals(WordType, {textItems, modeConfig: undefined});
        await wrapper.find(".settings-trigger").trigger("click");
        await wrapper.find(".settings-trigger").trigger("click");
        expect(wrapper.find(".settings-popup").exists()).toBe(false);
        expect(android.reportModalState).toHaveBeenLastCalledWith(false);
    });

    it("a popup that unmounts while open does not leave the page reported as modal", async () => {
        // E.g. the window's document is replaced while the gear popup is open.
        const shown = reactive({value: true});
        const {wrapper, android} = mountWithModals(WordScramble, {textItems, modeConfig: undefined}, shown);
        await wrapper.find(".settings-trigger").trigger("click");
        expect(android.reportModalState).toHaveBeenLastCalledWith(true);
        shown.value = false;
        await nextTick();
        expect(wrapper.find(".settings-popup").exists()).toBe(false);
        expect(android.reportModalState).toHaveBeenLastCalledWith(false);
    });
});
