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

function touch(type, clientY) {
    const event = new Event(type);
    event.touches = [{clientY}];
    window.dispatchEvent(event);
}

function wheel(deltaY) {
    const event = new Event("wheel");
    event.deltaY = deltaY;
    window.dispatchEvent(event);
}

describe("infinite scroll at the top of the document", () => {
    async function setup(doc = generalBook("current")) {
        requestPreviousChapter.mockResolvedValue(generalBook("previous"));
        window.bibleViewDebug.documents.push(doc);
        await nextTick();
        emit("setup_content", {topOffset: 0, bottomOffset: 0});
        await flushPromises();
    }

    it("renders no top gap element", async () => {
        await setup();
        expect(wrapper.find(".infinite-scroll-top-gap").exists()).toBe(false);
    });

    it("loads the previous section on a mouse wheel up at the top", async () => {
        await setup();
        expect(window.scrollY).toBe(0);
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
        expect(window.bibleViewDebug.documents.map(doc => doc.id)).toEqual(["previous", "current"]);
    });

    it("does not load on wheel down, or on wheel up when not at the top", async () => {
        await setup();
        wheel(100);
        window.scrollTo(0, 300);
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).not.toHaveBeenCalled();
    });

    it("loads on a downward drag at the top, deferred until the finger is lifted", async () => {
        await setup();
        touch("touchstart", 100);
        touch("touchmove", 103); // below threshold
        expect(requestPreviousChapter).not.toHaveBeenCalled();
        touch("touchmove", 160);
        await flushPromises();
        expect(requestPreviousChapter).not.toHaveBeenCalled();
        window.dispatchEvent(new Event("touchend"));
        await flushPromises();
        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
        expect(window.bibleViewDebug.documents.map(doc => doc.id)).toEqual(["previous", "current"]);
    });

    it("ignores an upward finger drag (scrolling down) and drags away from the top", async () => {
        await setup();
        touch("touchstart", 200);
        touch("touchmove", 100);
        window.scrollTo(0, 300);
        touch("touchmove", 400);
        window.dispatchEvent(new Event("touchend"));
        await flushPromises();
        expect(requestPreviousChapter).not.toHaveBeenCalled();
    });

    it("loads for a Bible document too", async () => {
        await setup({id: "bible", type: "bible"});
        wheel(-1);
        await flushPromises();
        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
    });

    it("does not load once the start has been reached", async () => {
        requestPreviousChapter.mockResolvedValue(null);
        window.bibleViewDebug.documents.push(generalBook("current"));
        await nextTick();
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["manual navigation", generalBook("current"), false],
        ["AI content", generalBook("ai", {isAiDocument: true}), true],
        ["dictionary", generalBook("dictionary", {bookCategory: "DICTIONARY"}), true],
    ])("does not load for %s", async (_name, doc, infiniteScroll) => {
        window.bibleViewDebug.config.infiniteScroll = infiniteScroll;
        await setup(doc);
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).not.toHaveBeenCalled();
    });

    it("removes its listeners on unmount", async () => {
        await setup();
        wrapper.unmount();
        wheel(-100);
        await flushPromises();
        expect(requestPreviousChapter).not.toHaveBeenCalled();
        wrapper = mount(BibleView, {attachTo: document.body, global: {stubs: {BookmarkModal: true, DocumentBroker: true, DevelopmentMode: true, AmbiguousSelection: true}}});
    });
});
