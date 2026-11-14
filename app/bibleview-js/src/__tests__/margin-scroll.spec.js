import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";

const scroll = vi.hoisted(() => ({positions: [], y: 0}));
vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({useAndroid: () => ({}), clearLog: () => {}}));
vi.mock("@/composables/scroll", () => ({useScroll: () => ({scrollY: ref(0), scrollToId: () => {}, doScrolling: y => { scroll.positions.push(y); scroll.y = y; }})}));
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
    scroll.y = 0;
    vi.spyOn(window, "scrollY", "get").mockImplementation(() => scroll.y);
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
});
afterEach(() => {
    wrapper?.unmount();
    document.documentElement.classList.remove("desktop-host");
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
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

// #4083: a double tap is the native full screen toggle. Both of its taps also reach the page as clicks, so a double tap
// in a margin (or on the e-ink page buttons) turned the page as well: Psalm 23 became Psalm 22:23 on every toggle. Native
// reports a double tap it acted on with `double_tap`; the page turns of that gesture are undone and no new one starts.
describe("margin page turns of a double tap (#4083)", () => {
    const start = 2000;

    it("a single margin tap turns the page at once", async () => {
        mountView(false);
        scroll.y = start;
        await wrapper.get(".prev-page-button").trigger("click");
        expect(scroll.y).toBeLessThan(start);
    });

    it("a double tap in the margin leaves the page where it was (Android order: double_tap before the second click)", async () => {
        mountView(false);
        scroll.y = start;
        await wrapper.get(".prev-page-button").trigger("click");
        emit("double_tap");
        await wrapper.get(".prev-page-button").trigger("click");
        expect(scroll.y).toBe(start);
    });

    it("a double tap in the margin leaves the page where it was (double_tap after both clicks)", async () => {
        mountView(false);
        scroll.y = start;
        await wrapper.get(".next-page-button").trigger("click");
        await wrapper.get(".next-page-button").trigger("click");
        emit("double_tap");
        expect(scroll.y).toBe(start);
    });

    it("a chapter inserted above between the tap and double_tap keeps the undo relative (infinite scroll)", async () => {
        mountView(false);
        scroll.y = start;
        await wrapper.get(".prev-page-button").trigger("click");
        const turned = scroll.y;
        const chapterHeight = 5000;
        scroll.y += chapterHeight; // insertThisTextAtTop keeps the same text on screen
        emit("double_tap");
        expect(scroll.y).toBe(start + chapterHeight);
        expect(turned).toBeLessThan(start);
    });

    it("a top offset change between the taps and double_tap keeps the undo relative (set_offsets)", async () => {
        mountView(false);
        scroll.y = start;
        await wrapper.get(".next-page-button").trigger("click");
        await wrapper.get(".next-page-button").trigger("click");
        const offsetDiff = -56; // the toolbar hides: setToolbarOffset scrolls by the offset difference
        scroll.y += offsetDiff;
        emit("double_tap");
        expect(scroll.y).toBe(start + offsetDiff);
    });

    it("a double tap after a page turn of an earlier tap does not undo that turn", async () => {
        vi.useFakeTimers();
        mountView(false);
        scroll.y = start;
        await wrapper.get(".prev-page-button").trigger("click");
        const turned = scroll.y;
        vi.advanceTimersByTime(2000);
        emit("double_tap");
        expect(scroll.y).toBe(turned);
    });

    it("margin taps turn the page again once the double tap is over", async () => {
        vi.useFakeTimers();
        mountView(false);
        scroll.y = start;
        emit("double_tap");
        vi.advanceTimersByTime(2000);
        await wrapper.get(".next-page-button").trigger("click");
        expect(scroll.y).toBeGreaterThan(start);
    });

    it("key and volume page scrolling is not undone by a double tap", async () => {
        mountView(false);
        scroll.y = start;
        emit("scroll_down");
        const turned = scroll.y;
        emit("double_tap");
        expect(scroll.y).toBe(turned);
    });
});
