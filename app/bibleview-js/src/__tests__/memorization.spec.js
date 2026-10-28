import {describe, expect, it} from "vitest";
import {groupConsecutive} from "@/composables/memorization";
import {isMemorizePunctuation, tokenizeMemorizeText} from "@/utils/memorize-text";

describe("groupConsecutive", () => {
    it("returns empty array for empty input", () => {
        expect(groupConsecutive([])).toEqual([]);
    });

    it("groups a single ordinal", () => {
        expect(groupConsecutive([5])).toEqual([[5, 5]]);
    });

    it("groups consecutive ordinals into one range", () => {
        expect(groupConsecutive([3, 4, 5, 6])).toEqual([[3, 6]]);
    });

    it("splits non-consecutive ordinals into separate ranges", () => {
        expect(groupConsecutive([1, 2, 5, 6, 7, 10])).toEqual([
            [1, 2],
            [5, 7],
            [10, 10],
        ]);
    });

    it("handles unsorted input", () => {
        expect(groupConsecutive([7, 3, 5, 4, 6])).toEqual([[3, 7]]);
    });

    it("handles duplicates", () => {
        expect(groupConsecutive([3, 3, 4, 4, 5])).toEqual([[3, 5]]);
    });

    it("handles single-element gaps", () => {
        expect(groupConsecutive([1, 3, 5])).toEqual([
            [1, 1],
            [3, 3],
            [5, 5],
        ]);
    });

    it("handles large consecutive range", () => {
        const ordinals = Array.from({length: 100}, (_, i) => i + 1);
        expect(groupConsecutive(ordinals)).toEqual([[1, 100]]);
    });

    it("handles multiple separate ranges with unsorted input", () => {
        expect(groupConsecutive([20, 10, 11, 12, 21, 30, 31])).toEqual([
            [10, 12],
            [20, 21],
            [30, 31],
        ]);
    });
});

describe("tokenizeMemorizeText", () => {
    it("splits words from punctuation runs", () => {
        expect(tokenizeMemorizeText("Hello, world! How?")).toEqual(["Hello", ",", "world", "!", "How", "?"]);
    });

    it.each([
        ["¶In the", ["¶", "In", "the"]],
        ["“In the”", ["“", "In", "the", "”"]],
        ["‘In the’", ["‘", "In", "the", "’"]],
        ["¶“In", ["¶“", "In"]],
        ["*In* the", ["*", "In", "*", "the"]],
        ["and/or", ["and", "/", "or"]],
        ["«In» „the“", ["«", "In", "»", "„", "the", "“"]],
        ["word—word…", ["word", "—", "word", "…"]],
    ])("treats markers in %s as separate punctuation tokens", (text, expected) => {
        expect(tokenizeMemorizeText(text)).toEqual(expected);
    });

    it.each(["don't", "don’t", "Lord's", "Lord’s", "o'er"])("keeps the contraction/possessive %s as one word", (word) => {
        expect(tokenizeMemorizeText(`${word} fear`)).toEqual([word, "fear"]);
    });

    it.each(["Jesus'", "Jesus’", "years'"])("keeps the trailing possessive apostrophe of %s", (word) => {
        expect(tokenizeMemorizeText(`${word} disciples`)).toEqual([word, "disciples"]);
    });

    it("treats a trailing apostrophe after a non-s letter as a closing quote", () => {
        expect(tokenizeMemorizeText("said 'Peace' loudly")).toEqual(["said", "'", "Peace", "'", "loudly"]);
    });

    it("splits punctuation outside the old hard-coded list (Unicode \\p{P})", () => {
        expect(tokenizeMemorizeText("God· word¿")).toEqual(["God", "·", "word", "¿"]);
    });

    it("returns an empty list for blank text", () => {
        expect(tokenizeMemorizeText("   ")).toEqual([]);
    });
});

describe("isMemorizePunctuation", () => {
    it.each(["¶", "“", "”", "‘", "’", "*", "/", "¶“", ",", "...", "—", "(", "]"])("recognises %s", (token) => {
        expect(isMemorizePunctuation(token)).toBe(true);
    });

    it.each(["God", "don't", "Jesus'", "a,b", "3"])("rejects %s", (token) => {
        expect(isMemorizePunctuation(token)).toBe(false);
    });
});
