import {describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import {androidKey, appSettingsKey, calculatedConfigKey, configKey, customCssKey, stringsKey, verseInfoKey} from "@/types/constants";

function mountFragment(direction, rightToLeft, xml) {
    return mount(OsisFragment, {
        props: {fragment: {bookInitials: "TEST", osisRef: "Luke.9.61", language: direction === "rtl" ? "he" : "en", direction, xml}},
        global: {provide: {
            [androidKey]: {},
            [appSettingsKey]: {rightToLeft},
            [calculatedConfigKey]: {},
            [configKey]: {},
            [customCssKey]: {registerBook: () => {}},
            [stringsKey]: {},
            [verseInfoKey]: null,
        }},
    });
}

describe("inline error message direction", () => {
    it.each([
        ["rtl", false, "Key Luke 9:61 not found in document SP."],
        ["ltr", true, "הפסוק לא נמצא."],
    ])("isolates localized errors from a %s module (RTL UI: %s)", (direction, rightToLeft, message) => {
        // OsisError.xml is used as a module fragment by multi-reference documents.
        const wrapper = mountFragment(direction, rightToLeft, `<div dir="auto">${message}</div>`);
        try {
            expect(wrapper.attributes("dir")).toBe(direction);
            const messageContainer = wrapper.get(".sword-TEST > div");
            expect(messageContainer.text()).toBe(message);
            expect(messageContainer.attributes("dir")).toBe("auto");
        } finally {
            wrapper.unmount();
        }
    });

    it("keeps ordinary Bible content inheriting its module direction", () => {
        const wrapper = mountFragment("rtl", false, "<div>Module content</div>");
        try {
            expect(wrapper.attributes("dir")).toBe("rtl");
            const content = wrapper.get(".sword-TEST > div");
            expect(content.text()).toBe("Module content");
            expect(content.attributes("dir")).toBeUndefined();
        } finally {
            wrapper.unmount();
        }
    });
});
