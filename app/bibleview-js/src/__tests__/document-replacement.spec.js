import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";

vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({useAndroid: () => ({}), clearLog: () => {}}));
vi.mock("@/composables/scroll", () => ({useScroll: () => ({scrollY: ref(0), scrollToId: () => {}})}));
vi.mock("@/composables/keyboard", () => ({useKeyboard: () => ({})}));
vi.mock("@/composables/verse-notifier", () => ({useVerseNotifier: () => ({currentVerse: ref(null), currentKey: ref(null)})}));
vi.mock("@/composables/use-reading-progress", () => ({useReadingProgress: () => ({progressText: ref("")})}));
vi.mock("@/composables/sharing", () => ({useSharing: () => {}}));
vi.mock("@/composables/features", () => ({useCustomFeatures: () => ({})}));
vi.mock("@/composables/custom-css", () => ({useCustomCss: () => ({customCssPromises: []})}));
vi.mock("@/composables/ordinal-highlight", () => ({useOrdinalHighlight: () => ({resetHighlights: () => {}})}));
vi.mock("@/composables/modal", () => ({useModal: () => ({closeModals: () => {}})}));
vi.mock("@/composables/memorization", () => ({useMemorization: () => {}}));

let wrapper;
let frames;

beforeEach(() => {
    window.bibleViewDebug = {};
    frames = [];
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
    vi.spyOn(window, "requestAnimationFrame").mockImplementation(callback => {
        frames.push(callback);
        return frames.length;
    });
    vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    Object.defineProperty(document, "fonts", {configurable: true, value: {ready: Promise.resolve()}});
    wrapper = mount(BibleView, {global: {stubs: {
        BookmarkModal: true, DevelopmentMode: true, AmbiguousSelection: true,
    }}});
});

afterEach(() => {
    wrapper.unmount();
    delete document.fonts;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

async function advanceFrame() {
    await flushPromises();
    const pending = frames.splice(0);
    pending.forEach(callback => callback(0));
    await flushPromises();
}

async function finishLoading() {
    for (let i = 0; i < 5; i++) await advanceFrame();
}

function missingCommentary(verse) {
    return {id: `missing-${verse}`, type: "error", severity: "NORMAL",
        errorMessage: `Psalm 105:${verse} not found in KingComm.`};
}

// WindowSync -> Window.loadText -> DocumentLoader.replaceDocument sends this pair
// for each new verse. Keep the real root event handlers and async document insertion.
function syncTo(doc) {
    emit("clear_document");
    emit("add_documents", doc);
}

describe("document replacement during scroll synchronization", () => {
    it.each(["fonts", "first frame", "second frame"])(
        "shows only the latest missing commentary when sync overtakes %s",
        async pause => {
            let fontsReady;
            if (pause === "fonts") {
                document.fonts.ready = new Promise(resolve => { fontsReady = resolve; });
            }
            syncTo(missingCommentary(43));
            await flushPromises();
            if (pause === "second frame") await advanceFrame();
            syncTo(missingCommentary(44));
            syncTo(missingCommentary(45));
            fontsReady?.();
            await finishLoading();

            expect(window.bibleViewDebug.documents).toEqual([missingCommentary(45)]);
            expect(wrapper.findAll("h3")).toHaveLength(1);
            expect(wrapper.text()).toContain("Psalm 105:45 not found in KingComm.");
            expect(wrapper.text()).not.toContain("Psalm 105:43");
            expect(wrapper.text()).not.toContain("Psalm 105:44");
        },
    );

    it("does not resurrect a pending document after clearing without a replacement", async () => {
        emit("add_documents", missingCommentary(43));
        await advanceFrame();
        emit("clear_document");
        await finishLoading();

        expect(window.bibleViewDebug.documents).toEqual([]);
    });

    it("does not let the old load hide the new load's spinner", async () => {
        syncTo(missingCommentary(43));
        await advanceFrame();
        await advanceFrame(); // Inserted; still waiting for its final animation frame.
        expect(window.bibleViewDebug.documents).toEqual([missingCommentary(43)]);
        syncTo(missingCommentary(44));
        await advanceFrame(); // Old load finishes while the new load still waits.

        await advanceFrame(); // New document inserted, but its own load is not finished.
        expect(window.bibleViewDebug.documents).toEqual([missingCommentary(44)]);
        expect(wrapper.find(".loading").exists()).toBe(true);
        await finishLoading();
        expect(wrapper.find(".loading").exists()).toBe(false);
        expect(window.bibleViewDebug.documents).toEqual([missingCommentary(44)]);
    });

    it("keeps multiple additions belonging to the same document load", async () => {
        emit("clear_document");
        emit("add_documents", missingCommentary(43));
        emit("add_documents", missingCommentary(44), missingCommentary(45));
        await finishLoading();

        expect(window.bibleViewDebug.documents).toEqual([
            missingCommentary(43), missingCommentary(44), missingCommentary(45),
        ]);
    });

    it("replaces an already displayed missing entry on the next sync", async () => {
        syncTo(missingCommentary(43));
        await finishLoading();
        syncTo(missingCommentary(44));
        await finishLoading();

        expect(window.bibleViewDebug.documents).toEqual([missingCommentary(44)]);
    });
});
