import {afterEach, describe, expect, it, vi} from "vitest";

const originalUrl = window.location.href;
const originalLang = document.documentElement.getAttribute("lang");

afterEach(() => {
    window.history.replaceState({}, "", originalUrl);
    if (originalLang === null) document.documentElement.removeAttribute("lang");
    else document.documentElement.lang = originalLang;
    vi.resetModules();
});

describe("UI language for automatic hyphenation (#4146)", () => {
    it.each([
        ["?lang=fi", "fi", "Sumennus"],
        ["?lang=de", "de", "Unschärfe"],
        ["?lang=fi-FI", "fi-FI", "Sumennus"],
        ["?lang=en-US", "en-US", "Blur"],
        ["?lang=missing-language", "en", "Blur"],
        ["", "en", "Blur"],
    ])("sets the document language when loading %s", async (query, lang, blur) => {
        vi.resetModules();
        window.history.replaceState({}, "", `/${query}`);
        document.documentElement.removeAttribute("lang");
        const {useStrings} = await import("@/composables/strings");
        const strings = useStrings();
        await vi.waitFor(() => {
            expect(document.documentElement.lang).toBe(lang);
            expect(strings.wordBlur).toBe(blur);
        });
    });
});
