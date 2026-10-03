import {describe, it, expect} from "vitest";
import {supportsChapterNavigation} from "@/composables/infinite-scroll";

function osisDoc(bookCategory, extra = {}) {
    return {type: "osis", bookCategory, isAiDocument: false, ...extra};
}

describe("supportsChapterNavigation", () => {
    it("returns false for an empty document list", () => {
        expect(supportsChapterNavigation([])).toBe(false);
    });
    it("supports bible documents", () => {
        expect(supportsChapterNavigation([{type: "bible"}])).toBe(true);
    });
    it("supports commentary documents", () => {
        expect(supportsChapterNavigation([osisDoc("COMMENTARY")])).toBe(true);
    });
    it("supports general book documents", () => {
        expect(supportsChapterNavigation([osisDoc("GENERAL_BOOK")])).toBe(true);
    });
    it("does not support dictionary documents", () => {
        expect(supportsChapterNavigation([osisDoc("DICTIONARY")])).toBe(false);
    });
    it("does not support AI documents", () => {
        expect(supportsChapterNavigation([osisDoc("GENERAL_BOOK", {isAiDocument: true})])).toBe(false);
    });
});
