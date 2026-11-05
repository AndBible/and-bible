import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {nextTick, reactive} from "vue";
import AskBookmarkSettings from "@/components/modals/AskBookmarkSettings.vue";
import {androidKey, appSettingsKey, configKey, modalKey, stringsKey} from "@/types/constants";
import {EditActionMode} from "@/types/client-objects";

// Use the real dialog and tabs so mode changes, input, OK and header close share validation.
describe("bookmark settings edit action validation", () => {
    let wrapper, oldObserver;
    const strings = new Proxy({}, {get: (_, key) => String(key)});
    const button = () => document.querySelector(".save-button");
    const modeButton = mode => [...document.querySelectorAll(".mode-toggle")].find(b =>
        b.title === strings[mode === null ? "editActionModeNone" :
            mode === EditActionMode.PREPEND ? "editActionModePrepend" : "editActionModeAppend"]);

    beforeEach(() => {
        document.body.innerHTML = '<div id="modals"></div>';
        oldObserver = window.ResizeObserver;
        window.ResizeObserver = class {observe() {} disconnect() {}};
        wrapper = mount(AskBookmarkSettings, {attachTo: document.body, global: {provide: {
            [appSettingsKey]: reactive({enabledExperimentalFeatures: ["bookmark_edit_actions"], topOffset: 0, bottomOffset: 0}),
            [configKey]: {}, [androidKey]: {}, [stringsKey]: strings,
            [modalKey]: {register: vi.fn()},
        }}});
    });
    afterEach(() => {
        wrapper.unmount();
        document.body.innerHTML = "";
        window.ResizeObserver = oldObserver;
    });

    it.each([EditActionMode.PREPEND, EditActionMode.APPEND].flatMap(mode =>
        [null, "", " \n "].map(content => [mode, content])))("rejects saved %s mode with empty content %j, including header close", async (mode, content) => {
        const result = wrapper.vm.askBookmarkSettings(null, {mode, content});
        let resolved = false;
        result.then(() => {resolved = true;});
        await flushPromises();
        // Header close must not bypass validation even before any text input.
        wrapper.findComponent({name: "ModalDialog"}).vm.$emit("close");
        await flushPromises();
        expect(resolved).toBe(false);
        expect(document.querySelector(".validation-error").textContent).toContain("editActionContentRequired");
        expect(button().disabled).toBe(true);
        const textarea = document.querySelector("textarea");
        textarea.value = "Valid text";
        textarea.dispatchEvent(new Event("input", {bubbles: true}));
        await nextTick();
        expect(button().disabled).toBe(false);
        button().click();
        expect(await result).toEqual({customIcon: null, editAction: {mode, content: "Valid text"}});
    });

    it.each([EditActionMode.PREPEND, EditActionMode.APPEND])("validates immediately when selecting %s and allows clearing the mode", async mode => {
        const result = wrapper.vm.askBookmarkSettings(null, {mode: null, content: null});
        await flushPromises();
        // Select the edit action tab, then choose an insertion mode without typing.
        const tab = [...document.querySelectorAll("button")].find(b => b.textContent.includes("editActionLabel"));
        tab.click();
        await nextTick();
        modeButton(mode).click();
        await nextTick();
        expect(button().disabled).toBe(true);
        expect(document.querySelector(".validation-error")).not.toBeNull();
        modeButton(null).click();
        await nextTick();
        expect(button().disabled).toBe(false);
        button().click();
        expect(await result).toEqual({customIcon: null, editAction: {mode: null, content: null}});
    });

    it("enables saving when the formatting button fills empty content", async () => {
        const result = wrapper.vm.askBookmarkSettings(null, {mode: EditActionMode.APPEND, content: null});
        await flushPromises();
        expect(button().disabled).toBe(true);
        document.querySelector(".format-button").click();
        await flushPromises();
        expect(button().disabled).toBe(false);
        button().click();
        expect((await result).editAction).toEqual({mode: EditActionMode.APPEND, content: "<br/>"});
    });

    it("allows a formatting-only paragraph break", async () => {
        const result = wrapper.vm.askBookmarkSettings(null, {mode: EditActionMode.APPEND, content: "<br/>"});
        await flushPromises();
        expect(button().disabled).toBe(false);
        button().click();
        expect((await result).editAction).toEqual({mode: EditActionMode.APPEND, content: "<br/>"});
    });
});
