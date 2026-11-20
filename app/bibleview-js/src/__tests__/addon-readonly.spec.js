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

// Add-on bookmark collections (#4162) are read-only: no delete/edit on add-on rows, no layout changes in an add-on StudyPad.

import {mount} from "@vue/test-utils";
import {defineComponent, h, provide, reactive, ref, nextTick} from "vue";
import {describe, it, expect, vi} from "vitest";
import BookmarkButtons from "@/components/BookmarkButtons.vue";
import StudyPadRow from "@/components/StudyPadRow.vue";
import StudyPadDocument from "@/components/documents/StudyPadDocument.vue";
import {useGlobalBookmarks, } from "@/composables/bookmarks";
import {isAddonBookmark, isAddonLabel, readOnlyStudyPad} from "@/composables/addon";
import {androidKey, exportModeKey, globalBookmarksKey, scrollKey} from "@/types/constants";

const appSettings = reactive({monochromeMode: false, studyPadCursors: {}, autoAssignLabels: [], disableClickToEdit: true});
const strings = new Proxy({}, {get: (_, key) => String(key)});

vi.mock("@/composables", () => ({
    useCommon: () => ({strings, appSettings, sprintf: (s) => s}),
}));

window.bibleViewDebug = {};

const labelId = "label-1";
const ADDON_LABEL = {id: labelId, name: "Collection", isRealLabel: true, style: {color: 0xFF0000FF}, addonModule: "MyAddon"};
const USER_LABEL = {...ADDON_LABEL, addonModule: null};

function bookmark(id, extra = {}) {
    return {
        id, type: "bookmark", hashCode: 1, labels: [labelId], primaryLabelId: labelId, bookInitials: "KJV",
        bookAbbreviation: "KJV", verseRangeAbbreviated: "Gen 1:1", osisRef: "Gen.1.1", v11n: "KJV",
        text: "t", notes: null, hasNote: false, wholeVerse: true, customIcon: null, editAction: {mode: null, content: null},
        orderNumber: 0, indentLevel: 0, expandContent: false, ordinalRange: [1, 1], addonModule: null,
        bookmarkToLabel: {bookmarkId: id, labelId}, bookmarkToLabels: [],
        ...extra,
    };
}

function mountButtons(b, android) {
    const labels = reactive(new Map([[labelId, {...USER_LABEL, color: 0xFF0000FF}]]));
    return mount(BookmarkButtons, {
        props: {bookmark: b, inBookmarkModal: true, showStudyPadButtons: true},
        global: {
            provide: {[androidKey]: android, [globalBookmarksKey]: {bookmarkLabels: labels}},
            stubs: {FontAwesomeIcon: {props: ["icon"], template: "<i :class=\"'icon-' + (typeof icon === 'string' ? icon : 'obj')\"/>"}, AreYouSure: true, AskBookmarkSettings: true},
        },
    });
}

describe("addon helpers", () => {
    it("treats any non-null addonModule, including empty string, as add-on", () => {
        expect(isAddonBookmark({addonModule: "X"})).toBe(true);
        expect(isAddonBookmark({addonModule: ""})).toBe(true);
        expect(isAddonBookmark({addonModule: null})).toBe(false);
        expect(isAddonBookmark({})).toBe(false);
        expect(isAddonLabel({addonModule: ""})).toBe(true);
        expect(readOnlyStudyPad(ADDON_LABEL)).toBe(true);
        expect(readOnlyStudyPad(USER_LABEL)).toBe(false);
    });
});

describe("BookmarkButtons on an add-on bookmark", () => {
    it("hides delete and row-writing actions but keeps share, study pad and info", () => {
        const w = mountButtons(bookmark("b1", {addonModule: "MyAddon"}), {});
        expect(w.find(".icon-trash").exists()).toBe(false);
        expect(w.find(".icon-custom-whole-verse-true").exists()).toBe(false);
        expect(w.find(".icon-obj").exists()).toBe(false); // bookmark settings (icon / edit action)
        expect(w.find(".icon-share-alt").exists()).toBe(true);
        expect(w.find(".icon-file-alt").exists()).toBe(true);
    });

    it("keeps delete for the user's own bookmark", () => {
        const w = mountButtons(bookmark("b2"), {});
        expect(w.find(".icon-trash").exists()).toBe(true);
        expect(w.find(".icon-custom-whole-verse-true").exists()).toBe(true);
    });

    it("keeps add note / edit outside the modal for an add-on bookmark", () => {
        const w = mount(BookmarkButtons, {
            props: {bookmark: bookmark("b1", {addonModule: "MyAddon"})},
            global: {
                provide: {[androidKey]: {}, [globalBookmarksKey]: {bookmarkLabels: reactive(new Map([[labelId, {...USER_LABEL, color: 1}]]))}},
                stubs: {FontAwesomeIcon: {props: ["icon"], template: "<i :class=\"'icon-' + icon\"/>"}, AreYouSure: true, AskBookmarkSettings: true},
            },
        });
        expect(w.find(".icon-edit").exists()).toBe(true);
        expect(w.find(".icon-info-circle").exists()).toBe(true);
        expect(w.find(".icon-trash").exists()).toBe(false);
    });
});

const ButtonIcon = {props: ["icon"], template: "<i :class=\"'icon-' + icon\"/>"};

async function mountRow(entry, label) {
    const w = mount(StudyPadRow, {
        props: {journalEntry: entry, label},
        global: {
            provide: {[androidKey]: {}, [exportModeKey]: ref(false)},
            stubs: {FontAwesomeIcon: ButtonIcon, BookmarkText: true, EditableText: true},
        },
    });
    await w.find(".menu > div").trigger("click"); // expand the ButtonRow
    return w;
}
const textEntry = {id: "t1", type: "journal", hashCode: 2, labelId, text: "hi", orderNumber: 1, indentLevel: 0, contentType: null};

describe("StudyPadRow in an add-on StudyPad", () => {
    it("shows no edit/add/delete/indent/drag actions for a text entry", async () => {
        const w = await mountRow(textEntry, ADDON_LABEL);
        for (const icon of ["trash", "edit", "plus-circle", "indent", "outdent", "sort"]) {
            expect(w.find(`.icon-${icon}`).exists(), icon).toBe(false);
        }
    });

    it("shows no layout or delete actions for an add-on bookmark row but keeps info and expand", async () => {
        const w = await mountRow(bookmark("b1", {addonModule: "MyAddon"}), ADDON_LABEL);
        for (const icon of ["trash", "edit", "plus-circle", "indent", "sort"]) {
            expect(w.find(`.icon-${icon}`).exists(), icon).toBe(false);
        }
        expect(w.find(".icon-info-circle").exists()).toBe(true);
        expect(w.find(".icon-expand-arrows-alt").exists()).toBe(true);
    });

    it("keeps an own copy (addonModule null) deletable and its note editable", async () => {
        const w = await mountRow(bookmark("b2"), ADDON_LABEL);
        expect(w.find(".icon-trash").exists()).toBe(true);
        expect(w.find(".icon-edit").exists()).toBe(true);
        // ...but the pad's layout stays locked
        for (const icon of ["plus-circle", "indent", "sort"]) {
            expect(w.find(`.icon-${icon}`).exists(), icon).toBe(false);
        }
    });

    it("leaves a normal StudyPad fully editable", async () => {
        const w = await mountRow(textEntry, USER_LABEL);
        for (const icon of ["trash", "edit", "plus-circle", "indent", "sort"]) {
            expect(w.find(`.icon-${icon}`).exists(), icon).toBe(true);
        }
    });

    it("never calls the bridge for removing the label from an own copy's add-on label via row actions", async () => {
        const android = {removeBookmark: vi.fn(), removeBookmarkLabel: vi.fn()};
        const w = mount(StudyPadRow, {
            props: {journalEntry: bookmark("b1", {addonModule: "MyAddon"}), label: ADDON_LABEL},
            global: {provide: {[androidKey]: android, [exportModeKey]: ref(false)}, stubs: {FontAwesomeIcon: ButtonIcon, BookmarkText: true, EditableText: true}},
        });
        await w.find(".menu > div").trigger("click");
        // the row's button bar did open (info stays available) ...
        expect(w.find(".icon-info-circle").exists()).toBe(true);
        // ... but offers no way to delete the add-on bookmark or detach its add-on label
        expect(w.find(".icon-trash").exists()).toBe(false);
        expect(android.removeBookmark).not.toHaveBeenCalled();
        expect(android.removeBookmarkLabel).not.toHaveBeenCalled();
    });
});

const DraggableStub = defineComponent({
    props: {modelValue: Array, disabled: Boolean},
    emits: ["update:modelValue"],
    render() {
        return h("div", {class: "dragstub"}, (this.modelValue ?? []).map((element, index) => this.$slots.item({element, index})));
    },
});
const StudyPadRowStub = defineComponent({
    props: {journalEntry: Object, label: Object, disableClickToEdit: Boolean},
    render: () => h("div", {class: "row-stub"}),
});

function mountDoc(label, android) {
    const doc = {
        id: "doc", type: "journal", bookmarks: [], genericBookmarks: [], bookmarkToLabels: [], genericBookmarkToLabels: [],
        journalTextEntries: [
            {id: "e1", type: "journal", orderNumber: 0, text: "a", labelId, indentLevel: 0, hashCode: 11},
            {id: "e2", type: "journal", orderNumber: 1, text: "b", labelId, indentLevel: 0, hashCode: 12},
        ],
        label,
    };
    const Host = defineComponent({
        setup() {
            provide(globalBookmarksKey, useGlobalBookmarks({disableBookmarking: false}));
            provide(androidKey, android);
            provide(scrollKey, {scrollToId: vi.fn()});
            provide(exportModeKey, ref(false));
            return () => h(StudyPadDocument, {document: doc});
        },
    });
    return mount(Host, {global: {stubs: {draggable: DraggableStub, StudyPadRow: StudyPadRowStub, FontAwesomeIcon: true}}});
}

describe("reordering an add-on StudyPad", () => {
    function reorder(w) {
        const drag = w.findComponent(DraggableStub);
        const [a, b] = drag.props("modelValue");
        drag.vm.$emit("update:modelValue", [b, a]);
    }

    it("is refused with a toast and never reaches updateOrderNumber", async () => {
        const android = {toast: vi.fn(), updateOrderNumber: vi.fn(), createNewStudyPadEntry: vi.fn()};
        const w = mountDoc(ADDON_LABEL, android);
        reorder(w);
        await nextTick();
        expect(android.toast).toHaveBeenCalledWith("addonReadOnly");
        expect(android.updateOrderNumber).not.toHaveBeenCalled();
    });

    it("disables dragging and hides the add-entry buttons", () => {
        const w = mountDoc(ADDON_LABEL, {toast: vi.fn()});
        expect(w.findComponent(DraggableStub).props("disabled")).toBe(true);
        expect(w.find(".journal-button").exists()).toBe(false);
    });

    it("still reorders a normal StudyPad", async () => {
        const android = {toast: vi.fn(), updateOrderNumber: vi.fn()};
        const w = mountDoc(USER_LABEL, android);
        reorder(w);
        await nextTick();
        expect(android.updateOrderNumber).toHaveBeenCalled();
        expect(android.toast).not.toHaveBeenCalled();
    });
});
