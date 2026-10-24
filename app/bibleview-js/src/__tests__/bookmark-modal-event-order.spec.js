import {describe, it, expect, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {defineComponent, h, provide, reactive, ref} from "vue";
import BookmarkModal from "@/components/modals/BookmarkModal.vue";
import EditableText from "@/components/EditableText.vue";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {emit} from "@/eventbus";
import {
    androidKey, appSettingsKey, calculatedConfigKey, configKey, customFeaturesKey, globalBookmarksKey,
    keyboardKey, modalKey, stringsKey,
} from "@/types/constants";

function bookmark(id, verseRangeAbbreviated) {
    return {
        id, type: "bookmark", hashCode: 1, ordinalRange: [1, 1], offsetRange: null,
        labels: [], bookInitials: "KJV", bookName: "King James Version", bookAbbreviation: "KJV",
        createdAt: 0, lastUpdatedOn: 0, text: "", fullText: "", bookmarkToLabels: [],
        primaryLabelId: null, notes: null, notesContentType: "MARKDOWN", hasNote: false,
        wholeVerse: true, customIcon: null, editAction: {mode: null, content: null}, sourcePromptId: null,
        osisRef: "Gen.1.1", originalOrdinalRange: [1, 1], verseRange: verseRangeAbbreviated,
        verseRangeOnlyNumber: "1:1", verseRangeAbbreviated, v11n: "KJV", osisFragment: null,
    };
}

function mountModal() {
    document.body.innerHTML = '<div id="modals"></div>';
    const oldObserver = window.ResizeObserver;
    const oldDebug = window.bibleViewDebug;
    const oldAndroid = window.android;
    const openAiDocPage = vi.fn();
    window.android = {openAiDocPage};
    const oldUrl = window.location.href;
    window.history.replaceState({}, "", "?lang=en-US");
    window.ResizeObserver = class {observe() {} disconnect() {}};
    window.bibleViewDebug = {};
    const config = reactive({});
    const openLabelActions = vi.fn();
    const LabelList = defineComponent({
        setup(_, {expose}) {
            expose({openActions: openLabelActions});
            return () => h("div");
        },
    });
    const Host = defineComponent({
        setup() {
            provide(globalBookmarksKey, useGlobalBookmarks(config));
            return () => h(BookmarkModal);
        },
    });
    const wrapper = mount(Host, {
        attachTo: document.body,
        global: {
            provide: {
                [configKey]: config,
                [calculatedConfigKey]: {},
                [appSettingsKey]: reactive({topOffset: 0, bottomOffset: 0, notesContentType: "MARKDOWN", disableAnimations: true}),
                [androidKey]: {saveBookmarkNote: vi.fn()},
                [stringsKey]: new Proxy({}, {get: (_, key) => String(key)}),
                [modalKey]: {register() {}},
                [keyboardKey]: {editorMode: ref(0)},
                [customFeaturesKey]: {parse: vi.fn(), features: new Set()},
            },
            stubs: {LabelList, BookmarkText: true, BookmarkButtons: true, FontAwesomeIcon: true},
        },
    });
    return {
        wrapper,
        openAiDocPage,
        openLabelActions,
        cleanup() {
            if (wrapper.exists()) wrapper.unmount();
            document.body.innerHTML = "";
            window.ResizeObserver = oldObserver;
            window.bibleViewDebug = oldDebug;
            window.android = oldAndroid;
            window.history.replaceState({}, "", oldUrl);
        },
    };
}

function expectEditor(wrapper, id, title) {
    expect(document.querySelector(".modal-content.wide.edit")).not.toBeNull();
    expect(document.querySelector(".bookmark-title").textContent).toContain(title);
    expect(wrapper.findComponent(EditableText).props("noteEditorContext")).toEqual({
        entityType: "BOOKMARK_NOTE", entityId: id,
    });
    expect(document.querySelector("textarea.md-content")).not.toBeNull();
}

describe("bookmark modal native event ordering", () => {
    it("opens the new note editor when bookmark data arrives before the click", async () => {
        const {wrapper, cleanup} = mountModal();
        try {
            emit("add_or_update_bookmarks", [bookmark("a", "Genesis 1:1")]);
            emit("bookmark_clicked", "a", {openNotes: true});
            await flushPromises();
            expectEditor(wrapper, "a", "Genesis 1:1");
        } finally {cleanup();}
    });

    it("preserves ordinary openInfo and openLabels behavior", async () => {
        const {wrapper, openLabelActions, cleanup} = mountModal();
        try {
            emit("add_or_update_bookmarks", [{...bookmark("a", "Genesis 1:1"), notes: "Existing note"}]);
            emit("bookmark_clicked", "a", {openInfo: true, openLabels: true, locateTop: true});
            await flushPromises();
            expect(document.querySelector(".info")).not.toBeNull();
            expect(wrapper.findComponent(EditableText).exists()).toBe(false);
            expect(wrapper.findComponent({name: "ModalDialog"}).props("locateTop")).toBe(true);
            expect(openLabelActions).toHaveBeenCalledTimes(1);
        } finally {cleanup();}
    });

    it.each(["later click", "global close", "unmount"])("cancels queued label actions on %s", async cancel => {
        const {wrapper, openLabelActions, cleanup} = mountModal();
        try {
            emit("add_or_update_bookmarks", [bookmark("a", "Genesis 1:1"), bookmark("b", "Exodus 1:1")]);
            emit("bookmark_clicked", "a", {openLabels: true});
            if (cancel === "later click") emit("bookmark_clicked", "b", {openNotes: true});
            else if (cancel === "global close") emit("close_modals");
            else wrapper.unmount();
            await flushPromises();
            expect(openLabelActions).not.toHaveBeenCalled();
            if (cancel === "later click") expectEditor(wrapper, "b", "Exodus 1:1");
        } finally {cleanup();}
    });

    it("does not process a pending AI click after unmount", async () => {
        const {wrapper, openAiDocPage, cleanup} = mountModal();
        try {
            emit("bookmark_clicked", "ai");
            wrapper.unmount();
            emit("add_or_update_ai_doc_markers", [{...bookmark("ai", "Genesis 1:1"),
                type: "ai-doc-marker", documentInitials: "AiStudy", pageKey: "page-1"}]);
            await flushPromises();
            expect(openAiDocPage).not.toHaveBeenCalled();
        } finally {cleanup();}
    });

    it("retains the Note click until the real bookmark update arrives", async () => {
        const {wrapper, cleanup} = mountModal();
        try {
            emit("bookmark_clicked", "a", {openNotes: true});
            await flushPromises();
            expect(document.querySelector(".modal-content")).toBeNull();
            emit("add_or_update_bookmarks", [bookmark("a", "Genesis 1:1")]);
            await flushPromises();
            expectEditor(wrapper, "a", "Genesis 1:1");
        } finally {cleanup();}
    });

    it("cancels a pending-only Note click on global close_modals", async () => {
        const {cleanup} = mountModal();
        try {
            emit("bookmark_clicked", "a", {openNotes: true});
            await flushPromises();
            emit("close_modals");
            emit("add_or_update_bookmarks", [bookmark("a", "Genesis 1:1")]);
            await flushPromises();
            expect(document.querySelector(".modal-content")).toBeNull();
        } finally {cleanup();}
    });

    it.each([false, true])("opens an AI document rather than a note modal (delayed=%s)", async delayed => {
        const {openAiDocPage, cleanup} = mountModal();
        const marker = {...bookmark("ai", "Genesis 1:1"), type: "ai-doc-marker",
            title: "Study", documentInitials: "AiStudy", pageKey: "page-1",
            sourceBookInitials: "KJV", sourceBookKey: "Gen.1.1"};
        try {
            if (!delayed) emit("add_or_update_ai_doc_markers", [marker]);
            emit("bookmark_clicked", "ai");
            await flushPromises();
            if (delayed) {
                expect(openAiDocPage).not.toHaveBeenCalled();
                emit("add_or_update_ai_doc_markers", [marker]);
                await flushPromises();
            }
            expect(openAiDocPage).toHaveBeenCalledExactlyOnceWith("AiStudy", "page-1");
            expect(document.querySelector(".modal-content")).toBeNull();
        } finally {cleanup();}
    });

    it.each([false, true])("does not resurrect delayed A after selecting B (dismissed=%s)", async dismissed => {
        const {wrapper, cleanup} = mountModal();
        try {
            emit("bookmark_clicked", "a", {openNotes: true});
            await flushPromises();
            emit("add_or_update_bookmarks", [bookmark("b", "Exodus 1:1")]);
            emit("bookmark_clicked", "b", {openNotes: true});
            await flushPromises();
            expectEditor(wrapper, "b", "Exodus 1:1");
            if (dismissed) {
                wrapper.findComponent({name: "ModalDialog"}).vm.$emit("close");
                await flushPromises();
                expect(document.querySelector(".modal-content")).toBeNull();
            }
            emit("add_or_update_bookmarks", [bookmark("a", "Genesis 1:1")]);
            await flushPromises();
            if (dismissed) {
                expect(document.querySelector(".modal-content")).toBeNull();
            } else {
                expectEditor(wrapper, "b", "Exodus 1:1");
            }
        } finally {cleanup();}
    });
});
