import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {reactive, nextTick} from "vue";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons.vue";
import {appSettingsKey, configKey, androidKey, stringsKey, modalKey, keyboardKey, locateTopKey, memorizationKey} from "@/types/constants";

// jsdom has no layout: give the button strip a fixed width and every button a fixed width.
const CONTAINER_WIDTH = 350;
const BUTTON_WIDTH = 50;

let restore = [];
function stubLayout(proto, prop, getter) {
    const original = Object.getOwnPropertyDescriptor(proto, prop);
    Object.defineProperty(proto, prop, {configurable: true, get: getter});
    restore.push(() => original ? Object.defineProperty(proto, prop, original) : delete proto[prop]);
}

beforeEach(() => {
    stubLayout(HTMLElement.prototype, "clientWidth", function () {
        return this.classList.contains("horizontal") ? CONTAINER_WIDTH : 0;
    });
    stubLayout(HTMLElement.prototype, "offsetWidth", function () {
        return this.classList.contains("large-action") ? BUTTON_WIDTH : 0;
    });
});
afterEach(() => {
    restore.forEach(r => r());
    restore = [];
    document.body.innerHTML = "";
});

function mountButtons(targets) {
    const memorization = {memorized: reactive(new Set()), targets};
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
                startOrdinal: 1000, endOrdinal: 1000,
            },
        },
        global: {
            provide: {
                [appSettingsKey]: appSettings, [configKey]: {}, [stringsKey]: strings,
                [androidKey]: {memorize: vi.fn()},
                [modalKey]: {closeModals() {}},
                [keyboardKey]: {setupKeyboardListener() {}},
                [locateTopKey]: false,
                [memorizationKey]: memorization,
            },
            stubs: {FontAwesomeIcon: true, FontAwesomeLayers: true},
        },
    });
}

const primaryCount = (w) => w.findAll(".horizontal > .large-action").length - (w.find(".horizontal > .more-action").exists() ? 1 : 0);
const hasMore = (w) => w.find(".horizontal > .more-action").exists();

describe("verse menu button strip (#4106)", () => {
    it("shows every button when they all fit", async () => {
        const w = mountButtons(reactive(new Set()));
        await flushPromises();
        // 7 Bible buttons x 50px = 350px: all fit, no More.
        expect(hasMore(w)).toBe(false);
        expect(primaryCount(w)).toBe(7);
        w.unmount();
    });

    it("re-fits the strip when a button appears while the menu is open, so More never wraps alone", async () => {
        const targets = reactive(new Set());
        const w = mountButtons(targets);
        await flushPromises();
        expect(primaryCount(w)).toBe(7);

        // The verse becomes a goal while the menu is open: "Goal -" joins the strip (8 buttons).
        targets.add(1000);
        await nextTick();
        await flushPromises();

        expect(hasMore(w)).toBe(true);
        // Primary buttons plus More (~70px) must fit in one row of 350px.
        expect(primaryCount(w) * BUTTON_WIDTH + 70).toBeLessThanOrEqual(CONTAINER_WIDTH);
        w.unmount();
    });
});
