import {afterEach, describe, expect, it, vi} from "vitest";
import indonesian from "@/lang/in.yaml";
import serbianCyrillic from "@/lang/b+sr+RS.yaml";
import serbianLatin from "@/lang/b+sr+Latn.yaml";
import brazilianPortuguese from "@/lang/pt-BR.yaml";
import portuguese from "@/lang/pt.yaml";
import simplifiedChinese from "@/lang/zh-CN.yaml";
import traditionalChinese from "@/lang/zh.yaml";
import hebrew from "@/lang/he.yaml";
import finnish from "@/lang/fi.yaml";
import english from "@/lang/default.yaml";

const originalUrl = window.location.href;

afterEach(() => {
    window.history.replaceState({}, "", originalUrl);
    vi.restoreAllMocks();
});

async function stringsFor(tag) {
    vi.resetModules();
    window.history.replaceState({}, "", `?${new URLSearchParams({lang: tag})}`);
    const {useStrings} = await import("@/composables/strings");
    return useStrings();
}

describe("BibleView translation lookup", () => {
    // Actual bundles make these tests catch aliases pointing at missing files or the wrong script.
    it.each([
        ["id", indonesian],
        ["id-ID", indonesian],
        ["in-ID", indonesian],
        ["sr", serbianCyrillic],
        ["sr-RS", serbianCyrillic],
        ["sr-Cyrl-RS", serbianCyrillic],
        ["sr-Latn", serbianLatin],
        ["sr-Latn-RS", serbianLatin],
        ["he-IL", hebrew],
        ["iw-IL", hebrew],
        ["zh-CN", simplifiedChinese],
        ["zh-Hans", simplifiedChinese],
        ["zh-Hans-CN", simplifiedChinese],
        ["zh-SG", simplifiedChinese],
        ["zh-Hant-TW", traditionalChinese],
        ["zh-Hant-CN", traditionalChinese],
        ["zh-TW", traditionalChinese],
        ["fi-FI", finnish],
        ["pt-PT", portuguese],
    ])("loads the matching bundle for %s", async (tag, translation) => {
        const strings = await stringsFor(tag);
        await vi.waitFor(() => expect(strings.bookmarksAndNotes).toBe(translation.bookmarksAndNotes));
    });

    it("prefers an exact regional bundle over the primary language", async () => {
        expect(brazilianPortuguese.bookmarksAndNotes).not.toBe(portuguese.bookmarksAndNotes);
        const strings = await stringsFor("pt-BR");
        await vi.waitFor(() => expect(strings.bookmarksAndNotes).toBe(brazilianPortuguese.bookmarksAndNotes));
    });

    it("keeps English when neither the tag nor its primary language is available", async () => {
        const error = vi.spyOn(console, "error").mockImplementation(() => {});
        const strings = await stringsFor("xx-YY");
        await vi.waitFor(() => expect(error).toHaveBeenCalledWith(
            "Language xx-YY not found, falling back to English!",
        ));
        expect(strings.bookmarksAndNotes).toBe(english.bookmarksAndNotes);
    });

    it("keeps English defaults for untranslated entries in a matching bundle", async () => {
        const strings = await stringsFor("en");
        await vi.waitFor(() => expect(strings.bookmarkAccurate).toBe("Bookmark created in %s"));
        expect(strings.bookmarksAndNotes).toBe(english.bookmarksAndNotes);
    });
});
