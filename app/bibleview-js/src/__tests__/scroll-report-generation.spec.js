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

// #3975 / osTicket 3508: a scroll report carries the native document generation of the content it was measured from,
// through infinite-scroll additions and across clear_document, so native can drop the reports of a replaced page.

import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {h, ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";
import {documentGeneration, resetDocumentGeneration} from "@/bridge/document-generation";

const reports = vi.hoisted(() => []);

// The real BibleView, verse notifier and generation handling; native services replaced. The android mock records the
// generation the native bridge would post with the report (native-bridge.ts reads the same documentGeneration()).
vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", async () => {
    const {documentGeneration} = await import("@/bridge/document-generation");
    return {
        useAndroid: () => ({scrolledToOrdinal: (key, ordinal) => reports.push({ordinal, generation: documentGeneration()})}),
        clearLog: () => {},
    };
});
vi.mock("@/composables/keyboard", () => ({useKeyboard: () => ({})}));
vi.mock("@/composables/use-reading-progress", () => ({useReadingProgress: () => ({progressText: ref("")})}));
vi.mock("@/composables/infinite-scroll", () => ({useInfiniteScroll: () => ({documentSupportsChapterNavigation: ref(false), infiniteScrollIsEnabled: ref(false), documentsCleared: () => {}})}));
vi.mock("@/composables/sharing", () => ({useSharing: () => {}}));
vi.mock("@/composables/features", () => ({useCustomFeatures: () => ({})}));
vi.mock("@/composables/custom-css", () => ({useCustomCss: () => ({customCssPromises: []})}));
vi.mock("@/composables/ordinal-highlight", () => ({useOrdinalHighlight: () => ({resetHighlights: () => {}})}));
vi.mock("@/composables/modal", () => ({useModal: () => ({closeModals: () => {}})}));
vi.mock("@/composables/memorization", () => ({useMemorization: () => ({})}));

let wrapper;
let probed = null;

/** The element the verse notifier's probe hits: verse [ordinal] of a rendered document. */
function verse(ordinal) {
    const el = document.createElement("span");
    el.className = "ordinal";
    el.dataset.ordinal = String(ordinal);
    document.body.appendChild(el);
    return el;
}

/** A user scroll that lands on [ordinal] (past the notifier's 50 ms throttle and the 100 ms jump of a clear). */
async function scrollOnto(ordinal) {
    await new Promise(resolve => setTimeout(resolve, 120));
    probed = verse(ordinal);
    window.dispatchEvent(new Event("scroll"));
    await flushPromises();
}

beforeEach(() => {
    reports.splice(0);
    resetDocumentGeneration();
    window.bibleViewDebug = {};
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
    vi.stubGlobal("scrollY", 0);
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    document.elementFromPoint = () => probed;
    wrapper = mount(BibleView, {attachTo: document.body, global: {stubs: {
        BookmarkModal: true, DevelopmentMode: true, AmbiguousSelection: true,
        DocumentBroker: {props: ["document"], render() { return h("div", {class: "document"}, this.document.id); }},
    }}});
    // jsdom has no innerText; scrollToId("top") logs the element's text.
    document.getElementById("top").innerText = "";
});

afterEach(() => {
    wrapper?.unmount();
    delete document.elementFromPoint;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("scroll report generation (#3975)", () => {
    it("reports carry the generation of the document they were rendered from", async () => {
        emit("clear_document", 5);
        await flushPromises();
        window.bibleViewDebug.documents.push({id: "John.3", type: "bible"});
        await flushPromises();
        await scrollOnto(10);
        expect(reports).toEqual([{ordinal: 10, generation: 5}]);

        // Infinite scroll adds the next chapter: same document load, same generation.
        window.bibleViewDebug.documents.push({id: "John.4", type: "bible"});
        await flushPromises();
        await scrollOnto(20);
        expect(reports.at(-1)).toEqual({ordinal: 20, generation: 5});

        // Native replaces the document. Until the old content has left the DOM, whatever the page reports or does is
        // still about the old document, so it keeps the old generation (native-bridge.spec covers a call in between).
        emit("clear_document", 6);
        expect(documentGeneration()).toBe(5);
        await flushPromises();
        expect(document.querySelectorAll(".document").length).toBe(0);
        expect(documentGeneration()).toBe(6);

        window.bibleViewDebug.documents.push({id: "John.6", type: "bible"});
        await flushPromises();
        await scrollOnto(40);
        expect(reports.at(-1)).toEqual({ordinal: 40, generation: 6});
    });

    it("a clear without a generation leaves the page with none (native never admits it)", async () => {
        emit("clear_document", 5);
        await flushPromises();
        emit("clear_document");
        await flushPromises();
        expect(documentGeneration()).toBe(0);
    });
});
