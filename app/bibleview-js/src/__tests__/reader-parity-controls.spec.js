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

import { describe, it, expect } from 'vitest';
import { reactive } from 'vue';
import { useInfiniteScroll } from "@/composables/infinite-scroll";

// Minimal stubs for the dependencies the composable needs. The chapter-navigation
// capability computeds only read documents[0] and config.infiniteScroll, so the
// android collaborator can be inert.
const android = {
    requestPreviousChapter: () => Promise.resolve(null),
    requestNextChapter: () => Promise.resolve(null),
};

function osisDoc({ bookCategory, isAiDocument }) {
    return { type: "osis", bookCategory, isAiDocument };
}

function setup(documents, infiniteScroll = true) {
    const config = reactive({ infiniteScroll });
    const docs = reactive(documents);
    return useInfiniteScroll(android, docs, config);
}

describe("reader chapter-navigation capability (shared Vue reader path)", () => {
    it("AI OSIS documents are not chapter-navigable", () => {
        const { documentSupportsChapterNavigation, infiniteScrollIsEnabled } =
            setup([osisDoc({ bookCategory: "BIBLE", isAiDocument: true })]);

        // The capability layer itself must reject AI documents, so both the manual
        // chapter controls and infinite scroll share the same contract.
        expect(documentSupportsChapterNavigation.value).toBe(false);
        expect(infiniteScrollIsEnabled.value).toBe(false);
    });

    it("regular Bible OSIS documents support chapter navigation and infinite scroll", () => {
        const { documentSupportsChapterNavigation, infiniteScrollIsEnabled } =
            setup([osisDoc({ bookCategory: "BIBLE", isAiDocument: false })]);

        expect(documentSupportsChapterNavigation.value).toBe(true);
        expect(infiniteScrollIsEnabled.value).toBe(true);
    });

    it("general-book OSIS documents support chapter navigation when their category is enabled", () => {
        const { documentSupportsChapterNavigation } =
            setup([osisDoc({ bookCategory: "GENERAL_BOOK", isAiDocument: false })]);

        expect(documentSupportsChapterNavigation.value).toBe(true);
    });

    it("OSIS documents with a non-navigable category are not chapter-navigable", () => {
        const { documentSupportsChapterNavigation } =
            setup([osisDoc({ bookCategory: "DICTIONARY", isAiDocument: false })]);

        expect(documentSupportsChapterNavigation.value).toBe(false);
    });

    it("infinite scroll respects the user setting on a navigable Bible document", () => {
        const { documentSupportsChapterNavigation, infiniteScrollIsEnabled } =
            setup([osisDoc({ bookCategory: "BIBLE", isAiDocument: false })], false);

        expect(documentSupportsChapterNavigation.value).toBe(true);
        expect(infiniteScrollIsEnabled.value).toBe(false);
    });
});
