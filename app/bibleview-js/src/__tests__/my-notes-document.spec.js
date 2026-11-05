import {afterEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import MyNotesDocument from "@/components/documents/MyNotesDocument.vue";
import {configKey, globalBookmarksKey, stringsKey, androidKey, appSettingsKey, calculatedConfigKey} from "@/types/constants";

vi.mock("@/components/MyNoteRow.vue", () => ({default: {
    props: ["bookmark"],
    template: '<div class="note-row">{{ bookmark.id }}</div>',
}}));

function bookmark(id, start, end = start, options = {}) {
    return {id, type: "bookmark", ordinalRange: [start, end], labels: [], hasNote: true, ...options};
}

function mountDocument({showBookmarks = true, bookmarksHideLabels = [], candidates = []} = {}) {
    const globalBookmarks = {
        bookmarks: {value: candidates},
        updateBookmarks: vi.fn(),
    };
    const document = {id: "notes", type: "notes", bookmarks: candidates, verseRange: "John 1:1-3", ordinalRange: [10, 12]};
    const wrapper = mount(MyNotesDocument, {props: {document}, global: {provide: {
        [configKey]: {showBookmarks, bookmarksHideLabels},
        [stringsKey]: {noMyNotesTitle: "No notes", noMyNotesDescription: "Nothing here"},
        [globalBookmarksKey]: globalBookmarks,
        [androidKey]: {},
        [appSettingsKey]: {},
        [calculatedConfigKey]: {},
    }}});
    return {wrapper, globalBookmarks};
}

afterEach(() => vi.restoreAllMocks());

describe("MyNotesDocument.vue", () => {
    it("filters by inclusive range, hidden labels, and note visibility, then sorts by ordinal", () => {
        const candidates = [
            bookmark("later", 12),
            bookmark("label-hidden", 11, 11, {labels: ["hidden"]}),
            bookmark("no-note", 10, 10, {hasNote: false}),
            bookmark("first", 10),
            {id: "generic", type: "generic-bookmark", ordinalRange: [11, 11], labels: [], hasNote: true},
        ];
        const {wrapper, globalBookmarks} = mountDocument({showBookmarks: false, bookmarksHideLabels: ["hidden"], candidates});
        try {
            expect(wrapper.find("h2").text()).toBe("John 1:1-3");
            expect(wrapper.findAll(".note-row").map(node => node.text())).toEqual(["first", "later"]);
            expect(wrapper.findAll(".note-container").map(node => node.attributes("data-ordinal"))).toEqual(["10", "12"]);
            expect(globalBookmarks.updateBookmarks).toHaveBeenCalledWith(candidates);
        } finally {
            wrapper.unmount();
        }
    });

    it("shows the empty-state message when no notes overlap", () => {
        const {wrapper} = mountDocument({candidates: [bookmark("outside", 20)]});
        try {
            expect(wrapper.find("h2").text()).toBe("No notes");
            expect(wrapper.text()).toContain("Nothing here");
            expect(wrapper.findAll(".note-row")).toHaveLength(0);
        } finally {
            wrapper.unmount();
        }
    });
});
