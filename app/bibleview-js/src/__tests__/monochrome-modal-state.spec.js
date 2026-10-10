import {describe, it, expect, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {reactive, nextTick} from "vue";
import BookmarkModal from "@/components/modals/BookmarkModal.vue";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import {addEventFunction, addEventOrdinalInfo} from "@/utils";
import BookmarkButtons from "@/components/BookmarkButtons.vue";
import AskBookmarkSettings from "@/components/modals/AskBookmarkSettings.vue";
import {emit} from "@/eventbus";
import {appSettingsKey, configKey, androidKey, stringsKey, modalKey, globalBookmarksKey, keyboardKey, ordinalHighlightKey} from "@/types/constants";

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

describe("actual nested bookmark paints", () => {
    it("real bookmark_clicked info removes only pure inline label tint and includes missing-book default link", async () => {
        const provide = context();
        const settings = provide[appSettingsKey];
        Object.assign(settings, {pureMonochromeMode: false, monochromeMode: false, nightMode: false});
        provide[stringsKey] = {bookmarkInaccurate: "Missing book %s", defaultBook: "Default Bible", openStudyPad: "StudyPad %s"};
        const b = provide[globalBookmarksKey].bookmarkMap.get("b");
        Object.assign(b, {labels: ["red"], bookInitials: "Missing", osisRef: "Gen.1.1", v11n: "KJV", editAction: {}, customIcon: null});
        provide[globalBookmarksKey].bookmarkLabels.set("red", {id: "red", name: "Red", color: 0xFFFF0000, isRealLabel: true});
        document.body.innerHTML = '<div id="modals"></div>';
        const observer = window.ResizeObserver;
        window.ResizeObserver = class {observe() {} disconnect() {}};
        const wrapper = mount(BookmarkModal, {attachTo: document.body, global: {provide,
            stubs: {EditableText: true, LabelList: true, BookmarkText: true}}});
        try {
            emit("bookmark_clicked", "b", {openInfo: true});
            await flushPromises();
            expect(wrapper.findComponent(BookmarkButtons).exists()).toBe(true);
            const labelIcon = () => document.querySelector('.links a[href^="journal:"]').previousElementSibling;
            const legacyColor = labelIcon().style.color;
            expect(legacyColor).not.toBe("");
            expect(document.querySelector('.info-text a[href^="osis:"]').textContent).toBe("Default Bible");
            for (const nightMode of [false, true]) {
                Object.assign(settings, {pureMonochromeMode: true, monochromeMode: true, nightMode});
                await nextTick();
                expect(labelIcon().style.color).toBe("");
                expect(document.querySelectorAll(".info a")).toHaveLength(3);
                settings.pureMonochromeMode = false;
                await nextTick();
                expect(labelIcon().style.color).not.toBe("");
            }
            Object.assign(settings, {monochromeMode: false, nightMode: false});
            await nextTick();
            expect(labelIcon().style.color).toBe(legacyColor);
        } finally {wrapper.unmount(); document.body.innerHTML = ""; window.ResizeObserver = observer;}
    });
    it("ambiguous bookmark button renders real BookmarkButtons strip", async () => {
        const provide = context();
        provide[keyboardKey] = {setupKeyboardListener() {}};
        provide[ordinalHighlightKey] = {resetHighlights() {}, highlightOrdinal() {}, hasHighlights: {value: false}};
        provide[modalKey] = {register() {}, closeModals() {}, modalOpen: {value: false}};
        provide[configKey].showBookmarks = true;
        provide[globalBookmarksKey].bookmarkIdsByOrdinal = new Map();
        Object.assign(provide[globalBookmarksKey].bookmarkMap.get("b"), {labels: ["red"], text: "verse", editAction: {}, customIcon: null});
        provide[globalBookmarksKey].bookmarkLabels.set("red", {id: "red", name: "Red", color: 0xFFFF0000, isRealLabel: true});
        document.body.innerHTML = '<div id="modals"></div>';
        const observer = window.ResizeObserver;
        window.ResizeObserver = class {observe() {} disconnect() {}};
        const wrapper = mount(AmbiguousSelection, {attachTo: document.body,
            global: {provide, stubs: {LabelList: true, AmbiguousActionButtons: true}}});
        try {
            const event = new MouseEvent("click");
            addEventOrdinalInfo(event, {ordinal: 1, osisRef: "BIBLE"});
            addEventFunction(event, null, {bookmarkId: "b", priority: 0});
            const handled = wrapper.vm.handle(event);
            await flushPromises();
            expect(wrapper.findComponent(BookmarkButtons).exists()).toBe(true);
            expect(document.querySelector(".ambiguous .bookmark-button")).not.toBeNull();
            wrapper.findComponent({name: "ModalDialog"}).vm.$emit("close");
            await handled;
        } finally {wrapper.unmount(); document.body.innerHTML = ""; window.ResizeObserver = observer;}
    });
});

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
