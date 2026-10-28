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

// Regression guard for #3870: the note editors keep their toolbar in view by scrolling the
// document. Inside the bookmark modal (position: fixed, a bounded scrolling editor container)
// that scroll only moved the Bible text behind the modal, more so when the soft keyboard
// resized the viewport. Inline editors (StudyPads, My Notes) must keep scrolling the document.
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {reactive, ref} from "vue";
import MarkdownEditor from "@/components/MarkdownEditor.vue";
import HtmlEditor from "@/components/HtmlEditor.vue";
import {androidKey, appSettingsKey, configKey, customFeaturesKey, keyboardKey, modalKey, stringsKey} from "@/types/constants";

let wrapper;
let scrollBy;
const originalScrollBy = window.scrollBy;

beforeEach(() => {
    scrollBy = vi.fn();
    window.scrollBy = scrollBy;
    window.visualViewport = new EventTarget();
    // jsdom has no editing commands; pell calls them on init.
    document.execCommand = vi.fn();
    document.queryCommandState = vi.fn(() => false);
    document.queryCommandValue = vi.fn(() => "");
});

afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    document.body.innerHTML = "";
    window.scrollBy = originalScrollBy;
    delete window.visualViewport;
    delete document.execCommand;
    delete document.queryCommandState;
    delete document.queryCommandValue;
    vi.restoreAllMocks();
});

/**
 * Mounts [editor] inside a host element whose inline style mimics the context: the modal's
 * bounded editor container (`overflow-y: auto` inside a `position: fixed` dialog), or a plain
 * block in the document flow (an inline StudyPad / My Notes editor).
 */
async function mountEditor(editor, {inModal}) {
    document.body.innerHTML = inModal
        ? '<div id="modals"></div><div style="position: fixed"><div id="host" style="overflow-y: auto; max-height: 200px"></div></div>'
        : '<div id="modals"></div><div id="host"></div>';
    const appSettings = reactive({topOffset: 0, bottomOffset: 50, disableAnimations: true, llmConfigured: false});
    wrapper = mount(editor, {
        attachTo: document.getElementById("host"),
        props: {text: "long note\n".repeat(50)},
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
    const content = document.querySelector(".pell-content");
    if (content && document.activeElement !== content) {
        // jsdom does not treat contenteditable as focusable; the browser does.
        content.tabIndex = -1;
        content.focus();
    }
    // The toolbar sits at the end of a long note, far below the visible area.
    const bar = document.querySelector(".pell-actionbar");
    vi.spyOn(bar, "getBoundingClientRect").mockReturnValue({top: 2000, bottom: 2040, left: 0, right: 0, width: 0, height: 40});
    return bar;
}

function keyboardResizesViewport() {
    window.visualViewport.dispatchEvent(new Event("resize"));
}

describe.each([
    ["MarkdownEditor", MarkdownEditor],
    ["HtmlEditor", HtmlEditor],
])("%s toolbar visibility on viewport resize", (_, editor) => {
    it("does not scroll the document behind the bookmark modal", async () => {
        await mountEditor(editor, {inModal: true});
        expect(document.activeElement?.closest("#host")).toBeTruthy();
        keyboardResizesViewport();
        expect(scrollBy).not.toHaveBeenCalled();
    });

    it("still scrolls the document for an inline editor", async () => {
        await mountEditor(editor, {inModal: false});
        keyboardResizesViewport();
        expect(scrollBy).toHaveBeenCalledTimes(1);
        expect(scrollBy.mock.calls[0][0].top).toBeGreaterThan(0);
    });
});

describe("MarkdownEditor auto-resize inside the bookmark modal", () => {
    it("keeps the editor container's scroll position while typing", async () => {
        await mountEditor(MarkdownEditor, {inModal: true});
        const host = document.getElementById("host");
        const textarea = host.querySelector("textarea");
        // jsdom has no layout: emulate the browser clamping the container's scroll offset
        // when the textarea is momentarily collapsed to measure its content height.
        let scrollTop = 0;
        Object.defineProperty(host, "scrollTop", {get: () => scrollTop, set: v => { scrollTop = v; }, configurable: true});
        Object.defineProperty(textarea, "scrollHeight", {
            get: () => { if (textarea.style.height === "0px" || textarea.style.height === "0") scrollTop = 0; return 1500; },
            configurable: true,
        });
        scrollTop = 600;
        textarea.value += "x";
        textarea.dispatchEvent(new Event("input"));
        await flushPromises();
        expect(textarea.style.height).toBe("1500px");
        expect(host.scrollTop).toBe(600);
    });
});
