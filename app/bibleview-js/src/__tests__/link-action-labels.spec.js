import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {load} from "js-yaml";
import {mount} from "@vue/test-utils";
import {describe, expect, it, vi} from "vitest";
import EpubRef from "@/components/ePub/EpubRef.vue";
import A from "@/components/OSIS/A.vue";
import {
    androidKey, appSettingsKey, calculatedConfigKey, configKey, osisDocumentInfoKey, stringsKey,
} from "@/types/constants";
import {getAllEventFunctions} from "@/utils";

const strings = load(readFileSync(resolve(__dirname, "../lang/default.yaml"), "utf8"));

function linkAction(component, props, check) {
    const android = {openEpubLink: vi.fn(), openExternalLink: vi.fn()};
    const wrapper = mount(component, {
        props,
        global: {
            provide: {
                [androidKey]: android,
                [osisDocumentInfoKey]: {bookInitials: "TestEpub"},
                [stringsKey]: strings,
                [configKey]: {},
                [appSettingsKey]: {},
                [calculatedConfigKey]: {},
            },
        },
    });
    try {
        const event = new MouseEvent("click", {bubbles: true, cancelable: true});
        wrapper.get("a").element.dispatchEvent(event);
        const actions = getAllEventFunctions(event);
        expect(actions).toHaveLength(1);
        check(actions[0], android);
    } finally {
        wrapper.unmount();
    }
}

describe("link action labels", () => {
    it("labels an internal EPUB reference without calling it external", () => {
        linkAction(EpubRef, {href: "chapter2.xhtml#note1", toKey: "chapter2", toId: "note1"}, (action, android) => {
            expect(action.options.title).toBe("Open internal link");
            action.callback();
            expect(android.openEpubLink).toHaveBeenCalledExactlyOnceWith("TestEpub", "chapter2", "note1");
            expect(android.openExternalLink).not.toHaveBeenCalled();
        });
    });

    it.each([
        "osis://?osis=Gen.1.1&v11n=KJV",
        "osis:Gen.1.1",
        "sword://KJV/Gen.1.1",
        "strongs://G2316",
        "morphology://robinson/V-PAI-3S",
        "B:10 1:1",
        "S:G2316",
        "#b1.1.1",
        "#sG2316",
        "#dG2316",
    ])("labels the in-app target %s as internal", href => {
        linkAction(A, {href}, action => {
            expect(action.options.title).toBe("Open internal link");
        });
    });

    it.each([
        "http://example.org/chapter2.xhtml",
        "https://example.org/osis:Gen.1.1",
        "mailto:reader@example.org",
        "https://example.org/#b1.1.1",
    ])("keeps the external label and target for %s", href => {
        linkAction(A, {href}, (action, android) => {
            expect(action.options.title).toBe("Open external link");
            action.callback();
            expect(android.openExternalLink).toHaveBeenCalledExactlyOnceWith(href);
            expect(android.openEpubLink).not.toHaveBeenCalled();
        });
    });
});
