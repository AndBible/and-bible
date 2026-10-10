import {describe, it, expect, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {reactive, nextTick} from "vue";
import BookmarkModal from "@/components/modals/BookmarkModal.vue";
import AskBookmarkSettings from "@/components/modals/AskBookmarkSettings.vue";
import {emit} from "@/eventbus";
import {appSettingsKey, configKey, androidKey, stringsKey, modalKey, globalBookmarksKey} from "@/types/constants";

function context(experimental = false) {
    const appSettings = reactive({enabledExperimentalFeatures: experimental ? ["bookmark_edit_actions"] : [], topOffset: 0, bottomOffset: 0});
    const strings = new Proxy({}, {get: (_, key) => String(key)});
    return {[appSettingsKey]: appSettings, [configKey]: {}, [androidKey]: {saveBookmarkNote: vi.fn()},
        [stringsKey]: strings, [modalKey]: {register: vi.fn()},
        [globalBookmarksKey]: {bookmarkMap: new Map([["b", {id: "b", type: "bookmark", notes: "note", labels: [], originalOrdinalRange: [1, 1], createdAt: 0, lastUpdatedOn: 0}]]), bookmarkLabels: new Map()}};
}
function mounted(component, experimental = false) {
    document.body.innerHTML = '<div id="host"><div id="modals"></div></div>';
    const oldObserver = window.ResizeObserver;
    window.ResizeObserver = class { observe() {} disconnect() {} };
    const wrapper = mount(component, {attachTo: document.body, global: {provide: context(experimental), stubs: {
        EditableText: true, LabelList: true, BookmarkText: true, BookmarkButtons: true, FontAwesomeIcon: true,
    }}});
    return {wrapper, cleanup() {wrapper.unmount(); document.body.innerHTML = ""; window.ResizeObserver = oldObserver;}};
}

describe("actual ordinary bookmark modal states", () => {
    it("opens via live bookmark_clicked trigger with real ModalDialog header and body", async () => {
        const {wrapper, cleanup} = mounted(BookmarkModal);
        try {
            expect(document.querySelector(".modal-content")).toBeNull();
            emit("bookmark_clicked", "b", {openNotes: true});
            await flushPromises();
            expect(document.querySelector(".modal-content.wide.edit")).not.toBeNull();
            expect(document.querySelector(".modal-header")).not.toBeNull();
            expect(document.querySelector(".modal-body")).not.toBeNull();
            await wrapper.findComponent({name: "ModalDialog"}).vm.$emit("close");
            await nextTick();
            expect(document.querySelector(".modal-content")).toBeNull();
        } finally {cleanup();}
    });
    it("default Icons selection changes and OK resolves the real settings result", async () => {
        const {wrapper, cleanup} = mounted(AskBookmarkSettings);
        try {
            const result = wrapper.vm.askBookmarkSettings(null, {mode: null, content: null});
            await flushPromises();
            expect(document.querySelector(".icon-item.selected")).not.toBeNull();
            const icons = document.querySelectorAll(".icon-item");
            icons[0].click();
            await nextTick();
            expect(icons[0].classList.contains("selected")).toBe(true);
            expect(document.querySelector(".save-button").disabled).toBe(false);
            document.querySelector(".save-button").click();
            expect((await result).customIcon).not.toBeNull();
        } finally {cleanup();}
    });
    it("experimental invalid content visibly marks error and blocks OK until mode cleared", async () => {
        const {wrapper, cleanup} = mounted(AskBookmarkSettings, true);
        try {
            wrapper.vm.askBookmarkSettings(null, {mode: "append", content: "text"});
            await flushPromises();
            expect(document.querySelector(".experimental-notice")).not.toBeNull();
            const textarea = document.querySelector(".content-textarea");
            textarea.value = "<invalid>";
            textarea.dispatchEvent(new Event("input", {bubbles: true}));
            await nextTick();
            expect(document.querySelector(".content-textarea.has-error")).not.toBeNull();
            expect(document.querySelector(".validation-error")).not.toBeNull();
            expect(document.querySelector(".save-button").disabled).toBe(true);
            document.querySelector(".mode-toggle").click();
            await nextTick();
            expect(document.querySelector(".validation-error")).toBeNull();
            expect(document.querySelector(".save-button").disabled).toBe(false);
        } finally {cleanup();}
    });
});
