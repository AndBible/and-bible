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

import {computed, ComputedRef, nextTick, onMounted, ref, Ref, watch} from "vue";
import {Config} from "@/composables/config";
import {DocumentReadingProgress} from "@/types/documents";
import {sprintf} from "@/utils";
import {
    computeCurrentPage,
    computePercent,
    computeTotalPages,
    estimateCharsPerPage,
    layoutSignature,
    resolveReadingProgress,
} from "@/composables/reading-progress";

// Minimum rendered text length before we trust a chars-per-page measurement.
const MIN_TEXT_FOR_MEASURE = 400;

type LayoutConfig = { value: { pageHeight: number; marginLeft: number; marginRight: number } };
export type ProgressDoc = { readingProgress?: DocumentReadingProgress | null; ordinalRange?: number[] };
type ProgressStrings = {
    readingProgressPercent: string;
    readingProgressPage: string;
    readingProgressChapter: string;
};

export function useReadingProgress(
    config: Config,
    documents: ProgressDoc[],
    currentVerse: Ref<number | null>,
    calculatedConfig: LayoutConfig,
    topElement: Ref<HTMLElement | null>,
    strings: ProgressStrings,
): { progressText: ComputedRef<string | null> } {
    const charsPerPageCache = new Map<string, number>();
    const charsPerPage = ref<number | null>(null);

    function signature(): string {
        const cc = calculatedConfig.value;
        return layoutSignature([config.fontSize, config.fontFamily, cc.marginLeft, cc.marginRight, cc.pageHeight, window.innerWidth]);
    }

    function remeasure(): void {
        const sig = signature();
        const cached = charsPerPageCache.get(sig);
        if (cached !== undefined) {
            charsPerPage.value = cached;
            return;
        }
        const el = topElement.value;
        if (!el) return;
        const textLength = el.textContent?.length ?? 0;
        if (textLength < MIN_TEXT_FOR_MEASURE) return;
        const cpp = estimateCharsPerPage(textLength, el.scrollHeight, calculatedConfig.value.pageHeight);
        if (cpp !== null) {
            charsPerPageCache.set(sig, cpp);
            charsPerPage.value = cpp;
        }
    }

    // Re-measure when the layout signature changes (font size, margins, viewport, rotation).
    watch(() => signature(), () => {
        charsPerPage.value = charsPerPageCache.get(signature()) ?? null;
        nextTick(remeasure);
    });
    // Re-measure as content loads (infinite scroll appends documents).
    watch(() => documents.length, () => nextTick(remeasure));
    onMounted(() => nextTick(remeasure));

    const progressText = computed<string | null>(() => {
        const rp = resolveReadingProgress(documents, currentVerse.value);
        if (!rp) return null;
        const ordinal = currentVerse.value ?? rp.unitStart;
        const percent = computePercent(ordinal, rp.unitStart, rp.unitEnd);
        if (percent === null) return null;
        const pct = Math.round(percent);

        if (rp.kind === "bible") {
            return sprintf(strings.readingProgressChapter, pct, rp.currentChapter, rp.chapterCount);
        }
        // kind === "book" (EPUB / general book)
        if (charsPerPage.value === null) {
            return sprintf(strings.readingProgressPercent, pct);
        }
        const totalPages = computeTotalPages(rp.charCount, charsPerPage.value);
        const page = computeCurrentPage(percent, totalPages);
        return sprintf(strings.readingProgressPage, pct, page, totalPages);
    });

    return {progressText};
}
