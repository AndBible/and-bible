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
const secondaryLabels = (w) => w.findAll(".dropdown-menu .title").map(b => b.text());

// Five 50px actions plus the estimated 70px More button fit at these phone widths.
describe("base order and dynamic Goal - slot (#4116, #4121)", () => {
    it.each([320, 350, 360])("puts Goal - last in the primary row at %s px and moves Share to the front of More", async (width) => {
        containerWidth = width;
        const w = mountButtons(reactive(new Set([1000])), {llmConfigured: true});
        try {
            await flushPromises();
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseMemorize", "removeMemorizationTarget"]);
            expect(primaryCount(w) * buttonWidth + 70).toBeLessThanOrEqual(containerWidth);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(["verseShare", "verseCompare", "verseMyNotes", "verseSpeak"]);
        } finally {
            w.unmount();
        }
    });

    it.each([320, 350, 360])("uses the base order without a goal at %s px", async (width) => {
        containerWidth = width;
        const w = mountButtons(reactive(new Set()), {llmConfigured: true});
        try {
            await flushPromises();
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseMemorize", "verseShare"]);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(["verseCompare", "verseMyNotes", "verseSpeak"]);
        } finally {
            w.unmount();
        }
    });

    it("appends Goal - after all base actions when everything fits", async () => {
        containerWidth = 600;
        const w = mountButtons(reactive(new Set([1000])), {
            llmConfigured: true, enabledExperimentalFeatures: ["add_paragraph_break"],
        });
        try {
            await flushPromises();
            expect(hasMore(w)).toBe(false);
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseMemorize", "verseShare", "verseCompare", "verseMyNotes", "verseSpeak", "verseParagraphBreak", "removeMemorizationTarget"]);
        } finally {
            w.unmount();
        }
    });

    it("keeps the non-Bible base order without Bible-only actions", async () => {
        const w = mountButtons(reactive(new Set([1000])), {
            llmConfigured: true, enabledExperimentalFeatures: ["add_paragraph_break"],
        }, {ordinalInfo: {bookInitials: "Dictionary", osisRef: "word"}, startOrdinal: 1000});
        try {
            await flushPromises();
            expect(hasMore(w)).toBe(false);
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseSpeak", "verseParagraphBreak"]);
        } finally {
            w.unmount();
        }
    });

    it.each([
        {width: 210, primary: ["verseMemorize", "removeMemorizationTarget"], secondary: ["addBookmark", "verseNote", "llmAction", "verseShare", "verseCompare", "verseMyNotes", "verseSpeak"]},
        {width: 140, primary: ["removeMemorizationTarget"], secondary: ["addBookmark", "verseNote", "llmAction", "verseMemorize", "verseShare", "verseCompare", "verseMyNotes", "verseSpeak"]},
    ])("handles the minimum measured capacity at $width px", async ({width, primary, secondary}) => {
        containerWidth = width;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set([1000])), {llmConfigured: true});
        try {
            await flushPromises();
            expect(primaryLabels(w)).toEqual(primary);
            expect(primaryCount(w) * buttonWidth + 70).toBeLessThanOrEqual(containerWidth);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(secondary);
        } finally {
            w.unmount();
        }
    });

    it("displaces AI rather than Memorize when the last ordinary slot is Memorize", async () => {
        containerWidth = 350;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set([1000])), {llmConfigured: true});
        try {
            await flushPromises();
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "verseMemorize", "removeMemorizationTarget"]);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(["llmAction", "verseShare", "verseCompare", "verseMyNotes", "verseSpeak"]);
        } finally {
            w.unmount();
        }
    });

    it.each([
        {targets: [1000], settings: {}},
        {targets: [1000, 1001], settings: {disableBibleModalButtons: ["REMOVE_MEMORIZATION_TARGET"]}},
    ])("does not reserve a goal slot for a partial selection or a disabled goal action: %j", async ({targets, settings}) => {
        const w = mountButtons(reactive(new Set(targets)), {llmConfigured: true, ...settings}, {
            verseInfo: {bookInitials: "KJV", v11n: "KJV"}, startOrdinal: 1000, endOrdinal: 1001,
        });
        try {
            await flushPromises();
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseMemorize", "verseShare"]);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(["verseCompare", "verseMyNotes", "verseSpeak"]);
        } finally {
            w.unmount();
        }
    });

    it("repositions Goal - when the measured capacity changes", async () => {
        containerWidth = 320;
        const w = mountButtons(reactive(new Set([1000])), {llmConfigured: true});
        try {
            await flushPromises();
            containerWidth = 420;
            window.dispatchEvent(new Event("resize"));
            await flushPromises();
            expect(primaryLabels(w)).toEqual(["addBookmark", "verseNote", "llmAction", "verseMemorize", "verseShare", "verseCompare", "removeMemorizationTarget"]);
            await w.find(".more-action").trigger("click");
            expect(secondaryLabels(w)).toEqual(["verseMyNotes", "verseSpeak"]);
        } finally {
            w.unmount();
        }
    });
});

describe("AI action priority (#4121)", () => {
    it.each([false, true])("handles three measured slots without displacing Memorize for a goal (goal: %s)", async (isGoal) => {
        containerWidth = 320;
        buttonWidth = 70;
        const w = mountButtons(reactive(new Set(isGoal ? [1000] : [])), {llmConfigured: true});
        try {
            await flushPromises();
            expect(hasMore(w)).toBe(true);
            expect(primaryLabels(w)).toEqual(isGoal
                ? ["addBookmark", "verseMemorize", "removeMemorizationTarget"]
                : ["addBookmark", "verseNote", "llmAction"]);
            if (isGoal) {
                await w.find(".more-action").trigger("click");
                expect(secondaryLabels(w)).toEqual(["verseNote", "llmAction", "verseShare", "verseCompare", "verseMyNotes", "verseSpeak"]);
            }
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
