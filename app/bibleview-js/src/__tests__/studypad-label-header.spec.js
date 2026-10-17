/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import {defineComponent, h, provide, reactive, ref, nextTick} from "vue";
import {describe, it, expect, vi, afterEach} from "vitest";
import StudyPadDocument from "@/components/documents/StudyPadDocument.vue";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {emit} from "@/eventbus";
import {androidKey, exportModeKey, globalBookmarksKey, scrollKey} from "@/types/constants";

const appSettings = reactive({monochromeMode: false, studyPadCursors: {}, autoAssignLabels: []});

vi.mock("@/composables", () => ({
    useCommon: () => ({strings: {emptyStudyPad: "empty"}, appSettings}),
}));

window.bibleViewDebug = {};

const BLUE = 0xFF0000FF;
const ORANGE = 0xFFFF8000;
const labelId = "label-1";

function makeDocument() {
    return {
        id: "doc", type: "journal", bookmarks: [], genericBookmarks: [], bookmarkToLabels: [],
        genericBookmarkToLabels: [],
        journalTextEntries: [{id: "entry-1", type: "journal", orderNumber: 0, text: "note", labelId}],
        label: {id: labelId, name: "Blue", isRealLabel: true, style: {color: BLUE}},
    };
}

// useGlobalBookmarks registers its event listeners in onMounted, so it must run inside a component.
const Host = defineComponent({
    setup() {
        provide(globalBookmarksKey, useGlobalBookmarks({disableBookmarking: false}));
        provide(androidKey, {});
        provide(scrollKey, {scrollToId: vi.fn()});
        provide(exportModeKey, ref(false));
        return () => h(StudyPadDocument, {document: makeDocument()});
    },
});

// Declared props keep Vue from warning about fallthrough attrs and let tests read the label handed to a row.
const StudyPadRowStub = defineComponent({
    props: {journalEntry: Object, label: Object, disableClickToEdit: Boolean},
    render: () => h("div", {class: "row-stub"}),
});
// Renders the item slot per element, as vuedraggable does, so the rows exist in the test DOM.
const DraggableStub = defineComponent({
    props: {modelValue: Array},
    render() {
        return h("div", (this.modelValue ?? []).map((element, index) => this.$slots.item({element, index})));
    },
});

function mountDoc() {
    return mount(Host, {global: {stubs: {draggable: DraggableStub, StudyPadRow: StudyPadRowStub, FontAwesomeIcon: true}}});
}

afterEach(() => {
    appSettings.monochromeMode = false;
});

const renamed = {id: labelId, name: "Renamed", isRealLabel: true, style: {color: ORANGE}};

describe("StudyPad header follows label edits", () => {
    it("shows the initial name and colour from the document", () => {
        appSettings.monochromeMode = false;
        const header = mountDoc().find(".journal-name");
        expect(header.text()).toBe("Blue");
        expect(header.attributes("style")).toContain("background-color");
    });

    it("updates name and background after update_labels", async () => {
        appSettings.monochromeMode = false;
        const wrapper = mountDoc();
        const before = wrapper.find(".journal-name").attributes("style");
        emit("update_labels", [renamed]);
        await nextTick();
        const header = wrapper.find(".journal-name");
        expect(header.text()).toBe("Renamed");
        expect(header.attributes("style")).not.toBe(before);
        expect(header.attributes("style")).toContain("background-color");
    });

    it("shows no label colour in monochrome mode after an edit", async () => {
        appSettings.monochromeMode = true;
        const wrapper = mountDoc();
        emit("update_labels", [renamed]);
        await nextTick();
        const header = wrapper.find(".journal-name");
        expect(header.text()).toBe("Renamed");
        expect(header.attributes("style") ?? "").not.toContain("background-color");
    });

    it("passes the live label to the rows after update_labels", async () => {
        const wrapper = mountDoc();
        expect(wrapper.findComponent(StudyPadRowStub).props("label").name).toBe("Blue");
        emit("update_labels", [renamed]);
        await nextTick();
        expect(wrapper.findComponent(StudyPadRowStub).props("label")).toMatchObject({id: labelId, name: "Renamed"});
    });

    it("falls back to the document label when the label is deleted", async () => {
        const wrapper = mountDoc();
        emit("update_labels", [renamed]);
        await nextTick();
        expect(wrapper.find(".journal-name").text()).toBe("Renamed");
        emit("delete_labels", [labelId]);
        await nextTick();
        expect(wrapper.find(".journal-name").text()).toBe("Blue");
        expect(wrapper.findComponent(StudyPadRowStub).props("label").name).toBe("Blue");
    });

    it("uses the document label when the label is not among the global labels", () => {
        const wrapper = mountDoc();
        expect(wrapper.find(".journal-name").text()).toBe("Blue");
        expect(wrapper.findComponent(StudyPadRowStub).props("label")).toMatchObject({id: labelId, name: "Blue"});
    });
});
