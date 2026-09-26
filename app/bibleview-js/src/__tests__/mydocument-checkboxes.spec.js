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

import {afterEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import OsisDocument from "@/components/documents/OsisDocument.vue";
import {androidKey, appSettingsKey, calculatedConfigKey, configKey, customCssKey, globalBookmarksKey, stringsKey} from "@/types/constants";

vi.mock("@/composables/bookmarks", () => ({useBookmarks: () => {}}));
vi.mock("@/composables/inline-action-icons", () => ({useInlineActionIcons: () => {}}));

const raw = "- [ ] first\n  - [x] child\n- [ ] repeat\n- [ ] repeat\n\n1. [ ] numbered\n   1. [x] nested";
const rendered = `<div class="mydoc-markdown">
  <ul><li>[ ] first<ul><li>[x] child</li></ul></li><li>[ ] repeat</li><li>[ ] repeat</li></ul>
  <ol><li>[ ] numbered<ol><li>[x] nested</li></ol></li></ol>
  <pre><code>- [ ] not a task</code></pre>
</div>`;

function createDocument(isMyDocument = true, html = rendered) {
    return {
        id: "test-page", type: "osis", isMyDocument, isNativeHtml: true, myDocumentPageId: "page-id",
        bookCategory: "GENERAL_BOOK", bookInitials: "MYDOC", osisRef: "page-key", annotateRef: "page-key",
        osisFragment: {xml: html, bookInitials: "MYDOC", language: "en", direction: "ltr", osisRef: "page-key"},
        genericBookmarks: [], aiDocMarkers: [], ordinalRange: {start: 0, end: 0},
    };
}

function mountPage({source = raw, isMyDocument = true, html = rendered, contentType = "MARKDOWN", fetchError = false} = {}) {
    const android = {
        getMyDocumentPageRawContent: fetchError
            ? vi.fn().mockRejectedValue(new Error("page unavailable"))
            : vi.fn().mockResolvedValue({pageId: "page-id", contentType, content: source, title: "Tasks", sourcePromptId: null}),
        saveMyDocumentPageContent: vi.fn(),
    };
    const wrapper = mount(OsisDocument, {
        attachTo: document.body,
        props: {document: createDocument(isMyDocument, html)},
        global: {
            provide: {
                [androidKey]: android,
                [configKey]: {},
                [calculatedConfigKey]: {},
                [appSettingsKey]: {},
                [stringsKey]: {},
                [customCssKey]: {registerBook: () => {}},
                [globalBookmarksKey]: {updateBookmarks: () => {}},
            },
            stubs: {
                DocumentActionMenu: true,
                OpenAllLink: true,
                FeaturesLink: true,
            },
        },
    });
    return {wrapper, android};
}

afterEach(() => {
    document.body.innerHTML = "";
    vi.useRealTimers();
});

describe("MyDocument Markdown task lists", () => {
    it("renders checkboxes for nested and numbered tasks but not fenced code", async () => {
        const {wrapper} = mountPage();
        await flushPromises();

        const boxes = wrapper.findAll('.mydoc-markdown input[type="checkbox"]');
        expect(boxes).toHaveLength(6);
        expect(boxes.map(box => box.element.checked)).toEqual([false, true, false, false, false, true]);
        expect(wrapper.find("pre input").exists()).toBe(false);
        expect(wrapper.find(".mydoc-markdown li").text()).not.toContain("[ ] first");
        wrapper.unmount();
    });

    it("saves only the selected source marker even when labels repeat", async () => {
        const {wrapper, android} = mountPage();
        await flushPromises();
        vi.useFakeTimers();

        const boxes = wrapper.findAll('.mydoc-markdown input[type="checkbox"]');
        await boxes[3].setValue(true);
        await boxes[1].setValue(false);
        vi.advanceTimersByTime(200);

        expect(android.saveMyDocumentPageContent).toHaveBeenCalledOnce();
        expect(android.saveMyDocumentPageContent).toHaveBeenCalledWith("MYDOC", "page-id",
            "- [ ] first\n  - [ ] child\n- [ ] repeat\n- [x] repeat\n\n1. [ ] numbered\n   1. [x] nested", null);
        expect(wrapper.find("pre").text()).toContain("- [ ] not a task");
        wrapper.unmount();
    });

    it("flushes the latest change when leaving the page before the debounce expires", async () => {
        const {wrapper, android} = mountPage();
        await flushPromises();
        vi.useFakeTimers();

        await wrapper.find('.mydoc-markdown input[type="checkbox"]').setValue(true);
        wrapper.unmount();

        expect(android.saveMyDocumentPageContent).toHaveBeenCalledWith("MYDOC", "page-id",
            raw.replace("- [ ] first", "- [x] first"), null);
    });

    it("does not enhance ordinary documents or HTML pages", async () => {
        const ordinary = mountPage({isMyDocument: false});
        await flushPromises();
        expect(ordinary.android.getMyDocumentPageRawContent).not.toHaveBeenCalled();
        expect(ordinary.wrapper.find('input[type="checkbox"]').exists()).toBe(false);
        ordinary.wrapper.unmount();

        const htmlPage = mountPage({contentType: "HTML"});
        await flushPromises();
        expect(htmlPage.wrapper.find('input[type="checkbox"]').exists()).toBe(false);
        htmlPage.wrapper.unmount();
    });

    it("leaves an unmatched rendered list untouched instead of editing the wrong task", async () => {
        const {wrapper, android} = mountPage({html: '<div class="mydoc-markdown"><ul><li>[ ] first</li></ul></div>'});
        await flushPromises();

        expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
        expect(android.saveMyDocumentPageContent).not.toHaveBeenCalled();
        wrapper.unmount();
    });

    it("ignores scroll ordinal badges before a task marker", async () => {
        const {wrapper} = mountPage({
            source: "- [ ] first",
            html: '<div class="mydoc-markdown"><ul><li><span class="ordinal-badge skip-offset">§1</span>[ ] first</li></ul></div>',
        });
        await flushPromises();

        expect(wrapper.find('input[type="checkbox"]').exists()).toBe(true);
        wrapper.unmount();
    });

    it("preserves Windows line endings when updating a nested task", async () => {
        const {wrapper, android} = mountPage({
            source: "- [ ] first\r\n  - [x] child",
            html: '<div class="mydoc-markdown"><ul><li>[ ] first<ul><li>[x] child</li></ul></li></ul></div>',
        });
        await flushPromises();
        vi.useFakeTimers();

        expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(2);
        await wrapper.findAll('input[type="checkbox"]')[1].setValue(false);
        vi.advanceTimersByTime(200);
        expect(android.saveMyDocumentPageContent).toHaveBeenCalledWith("MYDOC", "page-id", "- [ ] first\r\n  - [ ] child", null);
        wrapper.unmount();
    });

    it("keeps the rendered document readable when the raw page cannot be fetched", async () => {
        const {wrapper, android} = mountPage({fetchError: true});
        await flushPromises();

        expect(wrapper.find('.mydoc-markdown').text()).toContain('[ ] first');
        expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
        expect(android.saveMyDocumentPageContent).not.toHaveBeenCalled();
        wrapper.unmount();
    });

    it("never binds a nested checkbox to an identical marker inside a code block", async () => {
        const {wrapper, android} = mountPage({
            source: "- [ ] parent\n  ```\n  - [x] child\n  ```\n  - [x] child",
            html: '<div class="mydoc-markdown"><ul><li>[ ] parent<pre><code>- [x] child</code></pre><ul><li>[x] child</li></ul></li></ul></div>',
        });
        await flushPromises();

        expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(1);
        expect(wrapper.find('ul ul li').text()).toContain('[x] child');
        expect(android.saveMyDocumentPageContent).not.toHaveBeenCalled();
        wrapper.unmount();
    });
});
