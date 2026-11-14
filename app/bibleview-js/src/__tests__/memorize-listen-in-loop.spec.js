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
import {describe, it, expect, vi} from "vitest";
import MemorizeDocument from "@/components/documents/MemorizeDocument.vue";
import {memorizationKey} from "@/types/constants";

globalThis.ResizeObserver = vi.fn().mockImplementation(function () {
    this.observe = vi.fn();
    this.unobserve = vi.fn();
    this.disconnect = vi.fn();
});

// Set per test: the platform capability arrives as appSettings.speakSupported, separate from the user's hidden modal buttons.
const mockAppSettings = {disableBibleModalButtons: [], speakSupported: true};

vi.mock("@/composables", () => ({
    useCommon: () => ({
        strings: {listenInLoop: "Listen in Loop", viewHelp: "View help"},
        android: {saveState: vi.fn(), showHelpDialog: vi.fn()},
        appSettings: mockAppSettings,
    }),
}));

async function openMenu(disabledButtons, speakSupported = true) {
    mockAppSettings.disableBibleModalButtons = disabledButtons;
    mockAppSettings.speakSupported = speakSupported;
    const wrapper = mount(MemorizeDocument, {
        props: {
            document: {
                id: "doc1", type: "memorize", title: "John 3:16",
                texts: [{key: "v1", text: "For God so loved the world"}],
                state: {memorize: {mode: "blur", modeConfig: {}}},
            },
        },
        global: {
            provide: {
                [memorizationKey]: {
                    memorized: new Set(), targets: new Set(),
                    mergeData: vi.fn(), setupIndicatorRendering: vi.fn(),
                },
            },
            stubs: {WordBlur: true, WordScramble: true, WordType: true},
        },
    });
    await wrapper.find(".menu-trigger").trigger("click");
    return wrapper;
}

describe("MemorizeDocument overflow menu: Listen in Loop", () => {
    it("is shown where speech is available", async () => {
        const wrapper = await openMenu([]);
        expect(wrapper.find(".dropdown-menu").text()).toContain("Listen in Loop");
    });

    it("is hidden where the platform cannot speak (speakSupported=false)", async () => {
        const wrapper = await openMenu([], false);
        expect(wrapper.find(".dropdown-menu").exists()).toBe(true);
        expect(wrapper.find(".dropdown-menu").text()).not.toContain("Listen in Loop");
    });

    it("stays shown when the user only hides the SPEAK modal button", async () => {
        const wrapper = await openMenu(["SPEAK"], true);
        expect(wrapper.find(".dropdown-menu").text()).toContain("Listen in Loop");
    });
});
