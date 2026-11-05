import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {h, nextTick, ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";

// Keep the config, scroll and verse-notifier production path; replace native services only.
vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({useAndroid: () => ({scrolledToOrdinal: () => {}}), clearLog: () => {}}));
vi.mock("@/composables/keyboard", () => ({useKeyboard: () => ({})}));
vi.mock("@/composables/use-reading-progress", () => ({useReadingProgress: () => ({progressText: ref("")})}));
vi.mock("@/composables/infinite-scroll", () => ({useInfiniteScroll: () => ({documentSupportsChapterNavigation: ref(false), infiniteScrollIsEnabled: ref(false), documentsCleared: () => {}})}));
vi.mock("@/composables/sharing", () => ({useSharing: () => {}}));
vi.mock("@/composables/features", () => ({useCustomFeatures: () => ({})}));
vi.mock("@/composables/custom-css", () => ({useCustomCss: () => ({customCssPromises: []})}));
vi.mock("@/composables/ordinal-highlight", () => ({useOrdinalHighlight: () => ({resetHighlights: () => {}})}));
vi.mock("@/composables/modal", () => ({useModal: () => ({closeModals: () => {}})}));
vi.mock("@/composables/memorization", () => ({useMemorization: () => ({})}));

let wrapper;
let anchor;
beforeEach(() => {
    window.bibleViewDebug = {};
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
    vi.stubGlobal("scrollY", 0);
    vi.spyOn(window, "scrollTo").mockImplementation((x, y) => {
        window.scrollY = Number.isFinite(y) ? Math.max(0, y) : 0;
        window.dispatchEvent(new Event("scroll"));
    });
    // jsdom has no layout engine. Model the same verse moving from 1000 to 1600px on reflow.
    document.elementFromPoint = () => anchor;
    wrapper = mount(BibleView, {attachTo: document.body, global: {stubs: {
        BookmarkModal: true, DevelopmentMode: true, AmbiguousSelection: true,
        DocumentBroker: {render: () => h("span", {id: "o-26683", class: "ordinal", "data-ordinal": "26683", style: "font-size:16px;line-height:20px"}, "For God so loved the world")},
    }}});
    window.bibleViewDebug.documents.push({id: "KJV:John.3", type: "bible"});
});
afterEach(() => {
    wrapper?.unmount();
    delete document.elementFromPoint;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

async function loadAtVerse() {
    await flushPromises();
    anchor = document.getElementById("o-26683");
    anchor.innerText = anchor.textContent;
    vi.spyOn(anchor, "getBoundingClientRect").mockImplementation(() => ({
        top: (window.bibleViewDebug.config.fontSize === 16
            && window.bibleViewDebug.config.lineSpacing === 10
            && !window.bibleViewDebug.config.expandXrefs
            && !window.bibleViewDebug.config.showPageNumber
            && window.bibleViewDebug.appSettings.fontSizeMultiplier === 1 ? 1000 : 1600) - window.scrollY,
    }));
    emit("setup_content", {jumpToOrdinal: 26683, jumpToAnchor: null, jumpToId: null, topOffset: 0, bottomOffset: 0});
    await flushPromises();
}

async function growFont() {
    emit("set_config", {config: {fontSize: 20}, appSettings: {}, initial: false});
    await flushPromises();
}

describe("display-setting reading position (#3971)", () => {
    it.each([false, true])("preserves the freshly navigated verse with restored document = %s", async restored => {
        await loadAtVerse();
        if (restored) {
            document.getElementById("top").innerText = "";
            emit("clear_document");
            window.bibleViewDebug.documents.splice(0);
            window.bibleViewDebug.documents.push({id: "restored-KJV:John.3", type: "bible"});
            await loadAtVerse();
        }
        expect(anchor.getBoundingClientRect().top).toBe(2);
        await growFont();
        expect(anchor.getBoundingClientRect().top).toBe(2);
    });

    it.each([
        {nightMode: false},
        {nightMode: true},
        {monochromeMode: true, einkMode: true},
        {disableAnimations: true},
    ])("retains the verse across display modes: %j", async appSettings => {
        await loadAtVerse();
        emit("set_config", {config: {fontSize: 20}, appSettings, initial: false});
        await flushPromises();
        expect(anchor.getBoundingClientRect().top).toBe(2);
    });

    it("uses the visible verse rather than a previous scroll notification", async () => {
        await flushPromises();
        const previous = document.createElement("span");
        previous.className = "ordinal";
        previous.dataset.ordinal = "26676";
        anchor = previous;
        window.dispatchEvent(new Event("scroll"));
        await flushPromises();
        // The subsequent programmatic navigation suppresses the notifier's scroll sample.
        await loadAtVerse();
        await growFont();
        expect(anchor.getBoundingClientRect().top).toBe(2);
    });

    it.each([
        {config: {lineSpacing: 15}, appSettings: {}},
        {config: {}, appSettings: {fontSizeMultiplier: 1.5}},
        {config: {expandXrefs: true}, appSettings: {}},
        {config: {showPageNumber: true}, appSettings: {}},
    ])("preserves position for other text reflow settings: %j", async settings => {
        await loadAtVerse();
        emit("set_config", {...settings, initial: false});
        await flushPromises();
        expect(anchor.getBoundingClientRect().top).toBe(2);
    });

    it("does not scroll a replacement document when a config update is still pending", async () => {
        await loadAtVerse();
        emit("set_config", {config: {fontSize: 20}, appSettings: {}, initial: false});
        // The document is replaced before the config listener resumes; the new one must not be moved.
        document.getElementById("top").innerText = "";
        emit("clear_document");
        window.bibleViewDebug.documents.push({id: "replacement-KJV:John.3", type: "bible"});
        await nextTick();
        window.scrollTo.mockClear();
        await flushPromises();
        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("does not scroll when the setting does not move the verse", async () => {
        await loadAtVerse();
        window.scrollTo.mockClear();
        emit("set_config", {config: {}, appSettings: {nightMode: true}, initial: false});
        await flushPromises();
        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("leaves an empty document alone when no verse can be measured", async () => {
        await flushPromises();
        anchor = null;
        window.scrollTo.mockClear();
        await growFont();
        expect(window.scrollTo).not.toHaveBeenCalled();
    });

    it("preserves a partially scrolled verse's top offset instead of snapping to its start", async () => {
        await loadAtVerse();
        // The reader can stop partway through a long verse, not only at a verse boundary.
        window.scrollY = 1040;
        await growFont();
        expect(anchor.getBoundingClientRect().top).toBe(-40);
    });
});
