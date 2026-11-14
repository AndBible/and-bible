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

// Regression guard for #4094: in the compact bookmark panel the Markdown editor lives in a
// bounded `overflow-y: auto` container (EditableText's constraintDisplayHeight). The toolbar's
// popup menus (heading, text style, indent) open upwards above the toolbar; positioned
// absolutely inside that scroll container they were clipped to its few visible lines, so only
// H3 showed and a tap on "H2" went to the text behind. The menus must be placed against the
// viewport (position: fixed, from the toggle button's on-screen rectangle) so no scrolling
// ancestor clips them.
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {reactive, ref} from "vue";
import MarkdownEditor from "@/components/MarkdownEditor.vue";
import {androidKey, appSettingsKey, configKey, customFeaturesKey, keyboardKey, modalKey, stringsKey} from "@/types/constants";

const MENU_HEIGHT = 120;
let wrapper;

beforeEach(() => {
    window.visualViewport = new EventTarget();
});

afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    document.body.innerHTML = "";
    delete window.visualViewport;
    vi.restoreAllMocks();
});

/** Rect of the toggle button's wrapper; mutable so a test can "scroll" it. */
let wrapperRect;

async function mountInCompactPanel(text = "short note") {
    // The compact bookmark panel: a fixed dialog with a bounded, scrolling editor container.
    document.body.innerHTML =
        '<div id="modals"></div><div style="position: fixed"><div id="host" style="overflow-y: auto; max-height: 80px"></div></div>';
    const appSettings = reactive({topOffset: 0, bottomOffset: 50, disableAnimations: true, llmConfigured: false});
    wrapper = mount(MarkdownEditor, {
        attachTo: document.getElementById("host"),
        props: {text},
        global: {
            provide: {
                [appSettingsKey]: appSettings,
                [configKey]: {},
                [androidKey]: {refChooserDialog: vi.fn(), openDownloads: vi.fn()},
                [stringsKey]: new Proxy({}, {get: (_, key) => String(key)}),
                [keyboardKey]: {editorMode: ref(0)},
                [customFeaturesKey]: {parse: vi.fn(), features: new Set()},
                [modalKey]: {register() {}},
            },
            stubs: {FontAwesomeIcon: true, InputText: true},
        },
    });
    await flushPromises();
    const original = Element.prototype.getBoundingClientRect;
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function () {
        if (this.classList.contains("md-popup-wrapper")) return {...wrapperRect, toJSON() {}};
        if (this.classList.contains("md-popup-menu")) {
            return {top: 0, bottom: MENU_HEIGHT, left: 0, right: 60, width: 60, height: MENU_HEIGHT, toJSON() {}};
        }
        return original.call(this);
    });
}

function rect(top, left = 30) {
    return {top, bottom: top + 40, left, right: left + 40, width: 40, height: 40};
}

async function openMenu(index) {
    await wrapper.findAll(".md-popup-wrapper > .pell-button")[index].trigger("click");
    await flushPromises();
    return wrapper.find(".md-popup-menu");
}

describe("MarkdownEditor toolbar popup menus (#4094)", () => {
    it.each([
        ["heading", 0, ["H1", "H2", "H3"]],
        ["text style", 1, null],
        ["indent", 2, null],
    ])("places the %s menu against the viewport above its button", async (_, index, labels) => {
        wrapperRect = rect(500);
        await mountInCompactPanel();
        const menu = await openMenu(index);
        expect(menu.exists()).toBe(true);
        const style = menu.element.style;
        // Fixed: no scrolling ancestor (the bounded editor container) can clip it.
        expect(style.position).toBe("fixed");
        expect(style.left).toBe("30px");
        expect(style.bottom).toBe(`${window.innerHeight - 500}px`);
        expect(style.top).toBe("");
        if (labels) expect(menu.findAll(".md-popup-option").map(b => b.text())).toEqual(labels);
    });

    it("opens below the button when there is no room above it", async () => {
        wrapperRect = rect(40);
        await mountInCompactPanel();
        const style = (await openMenu(0)).element.style;
        expect(style.position).toBe("fixed");
        expect(style.top).toBe("80px");
        expect(style.bottom).toBe("");
    });

    it("follows the button when the editor container scrolls", async () => {
        wrapperRect = rect(500);
        await mountInCompactPanel();
        const menu = await openMenu(0);
        wrapperRect = rect(400);
        document.getElementById("host").dispatchEvent(new Event("scroll"));
        await flushPromises();
        expect(menu.element.style.bottom).toBe(`${window.innerHeight - 400}px`);
    });

    it("follows the button when the soft keyboard resizes the viewport", async () => {
        wrapperRect = rect(500);
        await mountInCompactPanel();
        const menu = await openMenu(0);
        wrapperRect = rect(300);
        window.visualViewport.dispatchEvent(new Event("resize"));
        await flushPromises();
        expect(menu.element.style.bottom).toBe(`${window.innerHeight - 300}px`);
    });

    it("applies the chosen heading level from the menu", async () => {
        wrapperRect = rect(500);
        await mountInCompactPanel("short note");
        const menu = await openMenu(0);
        await menu.findAll(".md-popup-option")[1].trigger("click");
        await flushPromises();
        expect(wrapper.find("textarea").element.value).toBe("## short note");
        expect(wrapper.find(".md-popup-menu").exists()).toBe(false);
    });
});
