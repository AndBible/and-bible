import {describe, it, expect, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {nextTick, ref} from "vue";
import BibleView from "@/components/BibleView.vue";

vi.mock("@/composables/addon-fonts", () => ({useAddonFonts: () => {}}));
vi.mock("@/composables/fontawesome", () => ({useFontAwesome: () => {}}));
vi.mock("@/composables/android", () => ({useAndroid: () => ({}), clearLog: () => {}}));
vi.mock("@/composables/scroll", () => ({useScroll: () => ({scrollY: ref(0), scrollToId: () => {}})}));
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

describe("real BibleView root", () => {
    it("updates MONO -> BW -> NORMAL -> MONO on the mounted root using real reactive settings", async () => {
        window.bibleViewDebug = {};
        const previousObserver = window.ResizeObserver;
        window.ResizeObserver = class { observe() {} disconnect() {} };
        const wrapper = mount(BibleView, {global: {stubs: {
            BookmarkModal: true, DocumentBroker: true, DevelopmentMode: true, AmbiguousSelection: true,
        }}});
        try {
            const settings = window.bibleViewDebug.appSettings;
            for (const [mono, pure] of [[true, true], [true, false], [false, false], [true, true]]) {
                Object.assign(settings, {monochromeMode: mono, pureMonochromeMode: pure});
                await nextTick();
                expect(wrapper.classes().includes("monochrome")).toBe(mono);
                expect(wrapper.classes().includes("pureMonochrome")).toBe(pure);
            }
        } finally {
            wrapper.unmount();
            window.ResizeObserver = previousObserver;
        }
    });
});
