import {afterEach, describe, expect, it, vi} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import {computed, defineComponent, h, reactive, ref} from "vue";
import DocumentBroker from "@/components/documents/DocumentBroker.vue";
import {useVerseNotifier} from "@/composables/verse-notifier";
import {useScroll} from "@/composables/scroll";
import {emit} from "@/eventbus";
import {
    androidKey, appSettingsKey, calculatedConfigKey, configKey, customCssKey,
    ordinalHighlightKey, stringsKey, bibleDocumentInfoKey, verseInfoKey, osisDocumentInfoKey,
} from "@/types/constants";

const wrappers = [];

function fragment(ref, ordinal, category = "BIBLE") {
    return {
        key: `KJV:${ref}`, keyName: ref, osisRef: ref, bookInitials: "KJV", bookAbbreviation: "KJV",
        bookCategory: category, language: "en", direction: "ltr", v11n: "KJVA",
        xml: `<div><verse osisID="${ref}" verseOrdinal="${ordinal}">First line</verse>` +
            `<verse osisID="${ref}" verseOrdinal="${ordinal + 1}">Second line</verse></div>`,
    };
}

function mountReader(doc) {
    doc = reactive(doc);
    const scrolledToOrdinal = vi.fn();
    const config = ref({fontFamily: "sans-serif"});
    const appSettings = {hideCompareDocuments: [], disableAnimations: true, topOffset: 0};
    const calculatedConfig = ref({topOffset: 0, marginLeft: 0, marginRight: 0});
    const highlight = {isHighlighted: () => false, highlightOrdinal: () => {}, resetHighlights: () => {}};
    let scroll;
    const wrapper = mount(defineComponent({
        setup() {
            scroll = useScroll(config, appSettings, calculatedConfig, highlight, ref(Promise.resolve()));
            useVerseNotifier(calculatedConfig, {scrolledToOrdinal}, scroll, computed(() => 20));
            return () => h(DocumentBroker, {document: doc});
        },
    }), {
        attachTo: document.body,
        global: {
            provide: {
                [bibleDocumentInfoKey]: null, [verseInfoKey]: null, [osisDocumentInfoKey]: null,
                [androidKey]: {querySelection: () => null}, [configKey]: config,
                [calculatedConfigKey]: calculatedConfig, [appSettingsKey]: appSettings,
                [stringsKey]: {multiDocumentLink: "%s (%s)"},
                [customCssKey]: {registerBook: () => {}}, [ordinalHighlightKey]: highlight,
            },
            stubs: {FeaturesLink: true},
        },
    });
    wrappers.push(wrapper);
    return {wrapper, scrolledToOrdinal, scroll};
}

afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount());
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("multi-reference reading position", () => {
    it.each([1, 2])("reports and restores the second verse in fragment index %s", async index => {
        const doc = {id: "multi", type: "multi", compare: false, osisFragments: [
            fragment("John.6.35", 100), fragment("John.6.58", 200), fragment("John.11.26", 300),
        ]};
        const first = mountReader(doc);
        const target = first.wrapper.findAll(".verse")[index * 2 + 1].element;
        document.elementFromPoint = () => target;
        window.dispatchEvent(new Event("scroll"));
        await flushPromises();
        expect(first.scrolledToOrdinal).toHaveBeenCalledOnce();
        // Each fragment contributes its start and two verse anchors.
        expect(first.scrolledToOrdinal).toHaveBeenCalledWith("", index * 3 + 2);
        const persistedOrdinal = first.scrolledToOrdinal.mock.calls[0][1];
        first.wrapper.unmount();
        wrappers.pop();

        const restored = mountReader({...doc, id: "reloaded"});
        const restoredTarget = restored.wrapper.findAll(".verse")[index * 2 + 1].element;
        restoredTarget.innerText = "Second line";
        restoredTarget.style.lineHeight = "20px";
        restoredTarget.style.fontSize = "16px";
        vi.spyOn(restoredTarget, "getBoundingClientRect").mockReturnValue({top: 600});
        vi.stubGlobal("scrollY", 0);
        const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
        emit("setup_content", {jumpToAnchor: persistedOrdinal, topOffset: 0, bottomOffset: 0});
        await flushPromises();
        expect(scrollTo).toHaveBeenLastCalledWith(0, 598);
        delete document.elementFromPoint;
    });

    it("reports fragment starts and repeated paragraph anchors in Strong's multi documents", async () => {
        const entry = key => ({
            ...fragment(key, 0, "DICTIONARY"),
            features: {type: "greek", keyName: key},
            xml: '<div><BVA ordinal="0">Definition</BVA></div>',
        });
        const {wrapper, scrolledToOrdinal} = mountReader({
            id: "strongs", type: "multi", compare: false, contentType: "strongs",
            osisFragments: [entry("G0001"), entry("G0002")],
        });
        document.elementFromPoint = () => wrapper.findAll(".ordinal")[1].element;
        window.dispatchEvent(new Event("scroll"));
        await flushPromises();
        expect(scrolledToOrdinal).toHaveBeenCalledWith("", 3);
        delete document.elementFromPoint;
    });

    it("distinguishes repeated Bible ordinals in different translations", async () => {
        const doc = {id: "compare", type: "multi", compare: true, osisFragments: [
            fragment("John.6.35", 100), {...fragment("John.6.35", 100), bookInitials: "ESV", key: "ESV:John.6.35"},
        ]};
        const {wrapper, scrolledToOrdinal} = mountReader(doc);
        document.elementFromPoint = () => wrapper.findAll(".verse")[3].element;
        window.dispatchEvent(new Event("scroll"));
        await flushPromises();
        expect(scrolledToOrdinal).toHaveBeenCalledWith("", 5);
        delete document.elementFromPoint;
    });
});
