import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {reactive} from "vue";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons.vue";
import ActionButton from "@/components/ActionButton.vue";
import {appSettingsKey, configKey, androidKey, stringsKey, modalKey, keyboardKey, locateTopKey, memorizationKey} from "@/types/constants";

// jsdom has no layout: make the strip wide enough that every button is a primary button.
let restore = [];
function stubLayout(proto, prop, getter) {
    const original = Object.getOwnPropertyDescriptor(proto, prop);
    Object.defineProperty(proto, prop, {configurable: true, get: getter});
    restore.push(() => original ? Object.defineProperty(proto, prop, original) : delete proto[prop]);
}

beforeEach(() => {
    stubLayout(HTMLElement.prototype, "clientWidth", function () {
        return this.classList.contains("horizontal") ? 2000 : 0;
    });
    stubLayout(HTMLElement.prototype, "offsetWidth", function () {
        return this.classList.contains("large-action") ? 50 : 0;
    });
});
afterEach(() => {
    restore.forEach(r => r());
    restore = [];
    document.body.innerHTML = "";
});

function mountButtons(android) {
    const strings = new Proxy({}, {get: (_, key) => String(key)});
    const appSettings = reactive({
        enabledExperimentalFeatures: [], llmConfigured: false,
        disableBibleModalButtons: [], disableGenericModalButtons: [],
    });
    return mount(AmbiguousActionButtons, {
        attachTo: document.body,
        props: {
            selectionInfo: {
                verseInfo: {bookInitials: "KJV", v11n: "KJV"},
                startOrdinal: 1000, endOrdinal: 1002,
            },
        },
        global: {
            provide: {
                [appSettingsKey]: appSettings, [configKey]: {}, [stringsKey]: strings,
                [androidKey]: android,
                [modalKey]: {closeModals() {}},
                [keyboardKey]: {setupKeyboardListener() {}},
                [locateTopKey]: false,
                [memorizationKey]: {memorized: reactive(new Set()), targets: reactive(new Set())},
            },
            stubs: {FontAwesomeIcon: true, FontAwesomeLayers: true},
        },
    });
}

async function clickButton(wrapper, id) {
    const button = wrapper.findAllComponents(ActionButton).find(b => b.props("button") === id);
    expect(button, `${id} button is shown`).toBeTruthy();
    await button.trigger("click");
}

// #4123: an action that opens another view (often in the links window, so this page is not cleared) must close the
// verse menu, as Bookmark and Note do. Otherwise the menu stays open above the newly opened view.
describe("verse menu actions that open another view close the menu (#4123)", () => {
    const cases = [
        ["MEMORIZE", "memorize", ["KJV", 1000, 1002]],
        ["COMPARE", "compare", ["KJV", 1000, 1002]],
        ["MY_NOTES", "openMyNotes", ["KJV", 1000]],
    ];
    for (const [id, method, args] of cases) {
        it(`${id} opens its view and closes the menu`, async () => {
            const android = {memorize: vi.fn(), compare: vi.fn(), openMyNotes: vi.fn()};
            const w = mountButtons(android);
            await flushPromises();
            await clickButton(w, id);
            expect(android[method]).toHaveBeenCalledWith(...args);
            expect(w.emitted("close")).toHaveLength(1);
            w.unmount();
        });
    }
});
