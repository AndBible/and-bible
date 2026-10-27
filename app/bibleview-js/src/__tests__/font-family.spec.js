import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import {nextTick, reactive} from "vue";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import {androidKey, appSettingsKey, calculatedConfigKey, configKey, customCssKey, stringsKey} from "@/types/constants";

describe("Font Family with language-specific module CSS (#3818)", () => {
    let moduleStyle;
    let wrapper;

    beforeEach(() => {
        moduleStyle = document.createElement("style");
        moduleStyle.textContent = "[lang=grc] { font-family: ModuleGreek; } [lang=he] { font-family: ModuleHebrew; }";
        document.head.appendChild(moduleStyle);
    });

    afterEach(() => {
        wrapper?.unmount();
        moduleStyle.remove();
    });

    function render(language, fontFamily) {
        const config = reactive({fontFamily, nonStrongsWordItalic: false});
        wrapper = mount(OsisFragment, {
            attachTo: document.body,
            props: {fragment: {bookInitials: "TestModule", osisRef: "Gen.1", language, direction: "ltr", xml: ""}},
            global: {
                provide: {
                    [configKey]: config,
                    [appSettingsKey]: {},
                    [calculatedConfigKey]: {},
                    [androidKey]: {},
                    [stringsKey]: {},
                    [customCssKey]: {registerBook: () => {}},
                },
                stubs: {OsisSegment: true},
            },
        });
        return config;
    }

    for (const [language, moduleFont] of [["grc", "ModuleGreek"], ["he", "ModuleHebrew"]]) {
        it(`keeps the ${language} module font with the default Font Family`, () => {
            render(language, "sans-serif");
            expect(getComputedStyle(wrapper.element).fontFamily).toBe(moduleFont);
        });

        it(`uses the selected font instead of the ${language} module font and restores the default`, async () => {
            const config = render(language, "sans-serif");
            config.fontFamily = "UserFont";
            await nextTick();
            expect(getComputedStyle(wrapper.element).fontFamily).toBe("UserFont");

            config.fontFamily = "serif";
            await nextTick();
            expect(getComputedStyle(wrapper.element).fontFamily).toBe("serif");

            config.fontFamily = "sans-serif";
            await nextTick();
            expect(getComputedStyle(wrapper.element).fontFamily).toBe(moduleFont);
        });

        it(`uses the selected font for ${language} when the module CSS loads later`, () => {
            moduleStyle.remove();
            render(language, "UserFont");
            document.head.appendChild(moduleStyle);
            expect(getComputedStyle(wrapper.element).fontFamily).toBe("UserFont");
        });
    }
});
