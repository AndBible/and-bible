import {afterEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {defineComponent, h, provide, reactive} from "vue";
import InputText from "@/components/modals/InputText.vue";
import AreYouSure from "@/components/modals/AreYouSure.vue";
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {useModal} from "@/composables/modal";
import {androidKey, appSettingsKey, calculatedConfigKey, configKey, modalKey, stringsKey} from "@/types/constants";

let wrapper;

afterEach(() => {
    wrapper?.unmount();
    document.body.innerHTML = "";
    vi.unstubAllGlobals();
});

function mountPrompt(component) {
    document.body.innerHTML = '<div id="modals"></div>';
    vi.stubGlobal("ResizeObserver", class {
        observe() {}
        disconnect() {}
    });
    let modals;
    const host = defineComponent({
        setup() {
            modals = useModal({reportModalState() {}});
            provide(modalKey, modals);
            return () => h(component);
        },
    });
    wrapper = mount(host, {
        attachTo: document.body,
        global: {
            provide: {
                [appSettingsKey]: reactive({topOffset: 0, bottomOffset: 0}),
                [configKey]: {},
                [calculatedConfigKey]: {},
                [androidKey]: {},
                [stringsKey]: {cancel: "Cancel", ok: "OK", yes: "Yes", inputPlaceholder: "Text"},
            },
            stubs: {FontAwesomeIcon: true},
        },
    });
    return {prompt: wrapper.findComponent(component), modals};
}

const dismissals = [
    ["toolbar", ".modal-toolbar button"],
    ["backdrop", ".modal-backdrop"],
    ["Escape", null],
    ["footer Cancel", ".modal-footer button"],
];

const prompts = [
    {name: "InputText", component: InputText, open: vm => vm.inputText("Original text"), cancelled: null},
    {name: "AreYouSure", component: AreYouSure, open: vm => vm.areYouSure(), cancelled: undefined},
];

for (const {name, component, open, cancelled} of prompts) {
    describe(`${name} with the real ModalDialog`, () => {
        it.each(dismissals)("%s cancels once and unmounts cleanly", async (_name, selector) => {
            const {prompt, modals} = mountPrompt(component);
            const settled = vi.fn();
            const result = open(prompt.vm).then(settled);
            await flushPromises();

            expect(prompt.findComponent(ModalDialog).exists()).toBe(true);
            expect(modals.modalOpen.value).toBe(true);
            expect(document.querySelector(".modal-content")).not.toBeNull();
            expect(settled).not.toHaveBeenCalled();

            const target = selector ? document.querySelector(selector) : document;
            const dismiss = () => selector
                ? target.click()
                : document.dispatchEvent(new KeyboardEvent("keyup", {key: "Escape"}));
            // Repeated user events before Vue unmounts must not settle twice.
            dismiss();
            dismiss();
            await flushPromises();

            expect(settled).toHaveBeenCalledExactlyOnceWith(cancelled);
            await result;
            expect(prompt.findComponent(ModalDialog).exists()).toBe(false);
            expect(document.querySelector(".modal-content")).toBeNull();
            expect(document.querySelector(".modal-backdrop")).toBeNull();
            expect(modals.modalOpen.value).toBe(false);

            document.dispatchEvent(new KeyboardEvent("keyup", {key: "Escape"}));
            await flushPromises();
            expect(settled).toHaveBeenCalledTimes(1);

            // The prompt remains usable after cancellation, with a fresh result.
            const reopened = vi.fn();
            const nextResult = open(prompt.vm).then(reopened);
            await flushPromises();
            expect(modals.modalOpen.value).toBe(true);
            expect(reopened).not.toHaveBeenCalled();
            document.querySelector(".modal-footer button").click();
            await flushPromises();
            expect(reopened).toHaveBeenCalledExactlyOnceWith(cancelled);
            await nextResult;
            expect(modals.modalOpen.value).toBe(false);
            expect(settled).toHaveBeenCalledTimes(1);
        });
    });
}
