import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {nextTick, ref} from "vue";
import BibleView from "@/components/BibleView.vue";
import {emit} from "@/eventbus";

const {requestPreviousChapter, requestNextChapter} = vi.hoisted(() => ({
    requestPreviousChapter: vi.fn(),
    requestNextChapter: vi.fn(),
}));

vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({
    useAndroid: () => ({requestPreviousChapter, requestNextChapter}), clearLog: () => {},
}));
vi.mock("@/composables/keyboard", () => ({useKeyboard: () => ({})}));
vi.mock("@/composables/verse-notifier", () => ({useVerseNotifier: () => ({currentVerse: ref(null), currentKey: ref(null)})}));
vi.mock("@/composables/use-reading-progress", () => ({useReadingProgress: () => ({progressText: ref("")})}));
vi.mock("@/composables/sharing", () => ({useSharing: () => {}}));
vi.mock("@/composables/features", () => ({useCustomFeatures: () => ({})}));
vi.mock("@/composables/custom-css", () => ({useCustomCss: () => ({customCssPromises: []})}));
vi.mock("@/composables/ordinal-highlight", () => ({useOrdinalHighlight: () => ({resetHighlights: () => {}})}));
vi.mock("@/composables/modal", () => ({useModal: () => ({})}));
vi.mock("@/composables/memorization", () => ({useMemorization: () => ({})}));

function generalBook(id, extra = {}) {
    return {id, type: "osis", bookCategory: "GENERAL_BOOK", isAiDocument: false, ...extra};
}

let wrapper;

beforeEach(() => {
    window.bibleViewDebug = {};
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
    vi.stubGlobal("scrollY", 0);
    vi.stubGlobal("pageYOffset", 0);
    vi.spyOn(window, "scrollTo").mockImplementation((x, y) => {
        window.scrollY = window.pageYOffset = Math.max(0, y);
    });
    requestPreviousChapter.mockReset();
    requestNextChapter.mockReset().mockResolvedValue(null);
    wrapper = mount(BibleView, {attachTo: document.body, global: {stubs: {
        BookmarkModal: true, DocumentBroker: true, DevelopmentMode: true, AmbiguousSelection: true,
    }}});
    // jsdom does not lay out elements. Model the start anchor's position from the
    // real spacers rendered before it, leaving the reader/scroll composables real.
    const top = wrapper.get("#top").element;
    top.innerText = "";
    top.style.lineHeight = "20px";
    top.style.fontSize = "16px";
    vi.spyOn(top, "getBoundingClientRect").mockImplementation(() => {
        let y = 0;
        for (let sibling = top.previousElementSibling; sibling; sibling = sibling.previousElementSibling) {
            y += parseFloat(sibling.style.height) || 0;
        }
        return {top: y - window.scrollY};
    });
    Object.defineProperty(wrapper.get("#bottom").element, "offsetTop", {value: 3000});
});

afterEach(() => {
    wrapper.unmount();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("general-book infinite scroll", () => {
    it("leaves room above the initial section and loads the previous section on an upward touch scroll", async () => {
        const previous = generalBook("previous");
        requestPreviousChapter.mockResolvedValue(previous);
        window.bibleViewDebug.documents.push(generalBook("current"));
        await nextTick();

        for (const nightMode of [false, true]) for (const monochromeMode of [false, true]) for (const disableAnimations of [false, true]) {
            Object.assign(window.bibleViewDebug.appSettings, {nightMode, monochromeMode, disableAnimations});
            await nextTick();
            const gap = wrapper.get(".infinite-scroll-top-gap").element;
            expect(parseFloat(window.getComputedStyle(gap).height)).toBeGreaterThan(2);
            expect(gap.compareDocumentPosition(wrapper.get("#top").element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        }

        emit("setup_content", {topOffset: 0, bottomOffset: 0});
        await flushPromises();
        expect(window.scrollY).toBeGreaterThan(0);
        window.dispatchEvent(new Event("scroll"));
        expect(requestPreviousChapter).not.toHaveBeenCalled();

        window.dispatchEvent(new Event("touchstart"));
        window.scrollTo(0, 0);
        window.dispatchEvent(new Event("scroll"));
        expect(requestPreviousChapter).not.toHaveBeenCalled();
        window.dispatchEvent(new Event("touchend"));
        await flushPromises();

        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
        expect(window.bibleViewDebug.documents.map(doc => doc.id)).toEqual(["previous", "current"]);
    });

    it.each([
        ["manual navigation", generalBook("current"), false],
        ["AI content", generalBook("ai", {isAiDocument: true}), true],
        ["dictionary", generalBook("dictionary", {bookCategory: "DICTIONARY"}), true],
        ["Bible", {id: "bible", type: "bible"}, true],
    ])("does not add a general-book gap for %s", async (_name, doc, infiniteScroll) => {
        window.bibleViewDebug.config.infiniteScroll = infiniteScroll;
        window.bibleViewDebug.documents.push(doc);
        await nextTick();
        expect(wrapper.find(".infinite-scroll-top-gap").exists()).toBe(false);
    });

    it("removes the gap once the start of the book is reached, keeping the scroll position", async () => {
        let resolvePrevious;
        requestPreviousChapter.mockReturnValue(new Promise(resolve => { resolvePrevious = resolve; }));
        window.bibleViewDebug.documents.push(generalBook("current"));
        await nextTick();
        emit("setup_content", {topOffset: 0, bottomOffset: 0});
        await flushPromises();
        expect(window.scrollY).toBe(46);
        window.dispatchEvent(new Event("scroll"));

        window.scrollTo(0, 0);
        window.dispatchEvent(new Event("scroll"));
        await vi.waitFor(() => expect(requestPreviousChapter).toHaveBeenCalledTimes(1));
        // The user keeps scrolling down while the (empty) answer is on its way.
        window.scrollTo(0, 100);
        resolvePrevious(null);
        await flushPromises();

        expect(wrapper.find(".infinite-scroll-top-gap").exists()).toBe(false);
        expect(window.scrollY).toBe(52);
    });

    it("keeps the gap in page-scroll (e-ink) mode, where the first page starts at the start anchor", async () => {
        Object.assign(window.bibleViewDebug.appSettings, {einkMode: true});
        window.bibleViewDebug.config.pageScrollAmount = 50;
        window.bibleViewDebug.documents.push(generalBook("current"));
        await nextTick();
        emit("setup_content", {topOffset: 0, bottomOffset: 0});
        await flushPromises();
        expect(wrapper.find(".infinite-scroll-top-gap").exists()).toBe(true);
        // The initial position is the anchor, just below the gap, so pages are aligned to the text.
        expect(window.scrollY).toBe(46);
    });
});
