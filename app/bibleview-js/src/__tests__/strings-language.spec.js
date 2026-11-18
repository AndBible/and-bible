import {afterEach, describe, expect, it, vi} from "vitest";

const originalUrl = window.location.href;
const originalLang = document.documentElement.getAttribute("lang");

afterEach(() => {
    window.history.replaceState({}, "", originalUrl);
    if (originalLang === null) document.documentElement.removeAttribute("lang");
    else document.documentElement.lang = originalLang;
    vi.resetModules();
});

describe("UI language for automatic hyphenation of UI labels (#4146)", () => {
    it.each([
        ["?lang=fi", "fi", "Sumennus"],
        ["?lang=de", "de", "Unschärfe"],
        ["?lang=fi-FI", "fi-FI", "Sumennus"],
        ["?lang=en-US", "en-US", "Blur"],
        ["?lang=missing-language", "en", "Blur"],
        ["", "en", "Blur"],
    ])("reports the UI language and leaves the document root untouched when loading %s", async (query, lang, blur) => {
        vi.resetModules();
        window.history.replaceState({}, "", `/${query}`);
        document.documentElement.removeAttribute("lang");
        const {useStrings, uiLanguage} = await import("@/composables/strings");
        const strings = useStrings();
        await vi.waitFor(() => {
            expect(uiLanguage.value).toBe(lang);
            expect(strings.wordBlur).toBe(blur);
        });
        // Bible text inherits the root language; the UI language must not leak into it.
        expect(document.documentElement.hasAttribute("lang")).toBe(false);
    });
});
