import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {reactive, nextTick} from "vue";
import AmbiguousActionButtons from "@/components/AmbiguousActionButtons.vue";
import {appSettingsKey, configKey, calculatedConfigKey, androidKey, stringsKey, modalKey, keyboardKey, locateTopKey, memorizationKey} from "@/types/constants";

// jsdom has no layout: give the button strip a fixed width and every button a fixed width.
const CONTAINER_WIDTH = 350;
const BUTTON_WIDTH = 50;
let containerWidth = CONTAINER_WIDTH;
let buttonWidth = BUTTON_WIDTH;

let restore = [];
function stubLayout(proto, prop, getter) {
    const original = Object.getOwnPropertyDescriptor(proto, prop);
    Object.defineProperty(proto, prop, {configurable: true, get: getter});
    restore.push(() => original ? Object.defineProperty(proto, prop, original) : delete proto[prop]);
}

beforeEach(() => {
    containerWidth = CONTAINER_WIDTH;
    buttonWidth = BUTTON_WIDTH;
    stubLayout(HTMLElement.prototype, "clientWidth", function () {
        return this.classList.contains("horizontal") ? containerWidth : 0;
    });
    stubLayout(HTMLElement.prototype, "offsetWidth", function () {
        return this.classList.contains("large-action") ? buttonWidth : 0;
    });
});
afterEach(() => {
    restore.forEach(r => r());
    restore = [];
    document.body.innerHTML = "";
});

function mountButtons(targets, settings = {}, selectionInfo = {
    verseInfo: {bookInitials: "KJV", v11n: "KJV"},
    startOrdinal: 1000, endOrdinal: 1000,
}) {
    const memorization = {memorized: reactive(new Set()), targets};
    const strings = new Proxy({}, {get: (_, key) => String(key)});
    const appSettings = reactive({
        enabledExperimentalFeatures: [], llmConfigured: false,
        disableBibleModalButtons: [], disableGenericModalButtons: [],
        ...settings,
    });
    return mount(AmbiguousActionButtons, {
        attachTo: document.body,
        props: {
            hasActions: false,
            selectionInfo,
        },
        global: {
            provide: {
                [appSettingsKey]: appSettings, [configKey]: {}, [calculatedConfigKey]: {}, [stringsKey]: strings,
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
const primaryLabels = (w) => w.findAll(".horizontal > .large-action:not(.more-action) .title").map(b => b.text());

describe("AI action priority (#4121)", () => {
    it.each([false, true])("keeps configured AI visible on a narrow phone (goal: %s)", async (isGoal) => {
        containerWidth = 320;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set(isGoal ? [1000] : [])), {llmConfigured: true});
        try {
            await flushPromises();
            expect(hasMore(w)).toBe(true);
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseMemorize", "llmAction"]);
            expect(primaryCount(w) * buttonWidth + 70).toBeLessThanOrEqual(containerWidth);
        } finally {
            w.unmount();
        }
    });

    it("keeps AI reachable in non-Bible menus without a Memorize action", async () => {
        containerWidth = 320;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set()), {
            llmConfigured: true, enabledExperimentalFeatures: ["add_paragraph_break"],
        }, {ordinalInfo: {bookInitials: "Dictionary", osisRef: "word"}});
        try {
            await flushPromises();
            expect(primaryLabels(w)).toContain("llmAction");
            expect(primaryLabels(w)).not.toContain("verseMemorize");
        } finally {
            w.unmount();
        }
    });

    it("does not show AI when it is unconfigured or disabled", async () => {
        for (const settings of [{llmConfigured: false}, {llmConfigured: true, disableBibleModalButtons: ["LLM_ACTION"]}]) {
            const w = mountButtons(reactive(new Set()), settings);
            try {
                await flushPromises();
                if (hasMore(w)) await w.find(".more-action").trigger("click");
                expect(w.findAll(".title").map(b => b.text())).not.toContain("llmAction");
            } finally {
                w.unmount();
            }
        }
    });
});

describe("verse menu button strip (#4106, #4116)", () => {
    it("keeps Memorize directly accessible for an existing goal on a narrow phone", async () => {
        containerWidth = 320;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set([1000])));
        try {
            await flushPromises();
            expect(hasMore(w)).toBe(true);
            expect(primaryLabels(w)).toContain("verseMemorize");
            expect(primaryLabels(w)).toContain("removeMemorizationTarget");
            expect(primaryCount(w) * buttonWidth + 70).toBeLessThanOrEqual(containerWidth);
        } finally {
            w.unmount();
        }
    });

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
        expect(primaryLabels(w)).toContain("verseMemorize");
        w.unmount();
    });
});
