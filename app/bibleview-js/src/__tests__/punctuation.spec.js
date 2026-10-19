/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {describe, expect, it} from "vitest";
import {isPunctuation, tokenizeMemorizeText, tokenizeTypedText} from "@/components/memorize/punctuation";

describe("memorize punctuation", () => {
    it.each([
        ["¶For", ["¶", "For"]],
        ["“For God”", ["“", "For", "God", "”"]],
        ["¶“For ¶God”", ["¶“", "For", "¶", "God", "”"]],
        ["Hello, world! How are you?", ["Hello", ",", "world", "!", "How", "are", "you", "?"]],
        ["¶“”", ["¶“”"]],
        ["", []],
    ])("splits markers the same way in both modes: %s", (text, expected) => {
        expect(tokenizeMemorizeText(text)).toEqual(expected);
        expect(tokenizeTypedText(text)).toEqual(expected);
    });

    it.each([
        ["Lord's", ["Lord", "'", "s"], ["Lord's"]],
        ["don't", ["don", "'", "t"], ["don't"]],
        ["Jesus'", ["Jesus", "'"], ["Jesus'"]],
        ["years'", ["years", "'"], ["years'"]],
        ["God'", ["God", "'"], ["God", "'"]],
    ])("keeps a straight apostrophe inside a typed word: %s", (text, simple, typed) => {
        expect(tokenizeMemorizeText(text)).toEqual(simple);
        expect(tokenizeTypedText(text)).toEqual(typed);
    });

    it("leaves a curly apostrophe attached in both modes", () => {
        expect(tokenizeMemorizeText("God’s")).toEqual(["God’s"]);
        expect(tokenizeTypedText("God’s")).toEqual(["God’s"]);
        expect(tokenizeMemorizeText("‘For")).toEqual(["‘For"]);
        expect(tokenizeTypedText("‘For")).toEqual(["‘For"]);
    });

    it("treats marker runs as punctuation and words as words", () => {
        for (const token of ["¶", "“”", "¶“”", ",", "!", "?"]) {
            expect(isPunctuation(token)).toBe(true);
        }
        for (const token of ["For", "Lord's", "God’s", "‘For", ""]) {
            expect(isPunctuation(token)).toBe(false);
        }
    });
});
