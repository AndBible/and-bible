import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";

const scroll = vi.hoisted(() => ({positions: []}));
vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({useAndroid: () => ({}), clearLog: () => {}}));
vi.mock("@/composables/scroll", () => ({useScroll: () => ({scrollY: ref(0), scrollToId: () => {}, doScrolling: y => scroll.positions.push(y)})}));
vi.mock("@/composables/keyboard", () => ({useKeyboard: () => ({})}));
vi.mock("@/composables/verse-notifier", () => ({useVerseNotifier: () => ({currentVerse: ref(null), currentKey: ref(null)})}));
vi.mock("@/composables/use-reading-progress", () => ({useReadingProgress: () => ({progressText: ref("")})}));
vi.mock("@/composables/infinite-scroll", () => ({useInfiniteScroll: () => ({documentSupportsChapterNavigation: ref(false), infiniteScrollIsEnabled: ref(false)})}));
vi.mock("@/composables/sharing", () => ({useSharing: () => {}}));
vi.mock("@/composables/features", () => ({useCustomFeatures: () => ({})}));
vi.mock("@/composables/custom-css", () => ({useCustomCss: () => ({customCssPromises: []})}));
vi.mock("@/composables/ordinal-highlight", () => ({useOrdinalHighlight: () => ({resetHighlights: () => {}})}));
vi.mock("@/composables/modal", () => ({useModal: () => ({})}));
vi.mock("@/composables/memorization", () => ({useMemorization: () => ({})}));

let wrapper;
beforeEach(() => {
    window.bibleViewDebug = {};
    scroll.positions = [];
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
});
afterEach(() => {
    wrapper?.unmount();
    document.documentElement.classList.remove("desktop-host");
    vi.unstubAllGlobals();
});
function mountView(desktop) {
    document.documentElement.classList.toggle("desktop-host", desktop);
    wrapper = mount(BibleView, {global: {stubs: {
        BookmarkModal: true, DocumentBroker: true, DevelopmentMode: true, AmbiguousSelection: true,
    }}});
}

describe("margin page scrolling", () => {
    it("scrolls when either mobile margin is tapped", async () => {
        mountView(false);
        await wrapper.get(".prev-page-button").trigger("click");
        await wrapper.get(".next-page-button").trigger("click");
        expect(scroll.positions).toHaveLength(2);
    });
    it("does not scroll on desktop margin clicks but still handles page-scroll events", async () => {
        mountView(true);
        for (const selector of [".prev-page-button", ".next-page-button"]) {
            const margin = wrapper.find(selector);
            if (margin.exists()) await margin.trigger("click");
        }
        expect(scroll.positions).toHaveLength(0);
        emit("scroll_down");
        emit("scroll_up");
        expect(scroll.positions).toHaveLength(2);
    });
});
