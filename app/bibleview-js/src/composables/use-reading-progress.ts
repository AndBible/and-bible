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
import {sprintf} from "@/utils";
import {
    computeCurrentPage,
    computePercent,
    computeTotalPages,
    estimateCharsPerPage,
    layoutSignature,
    ProgressDoc,
    resolveReadingProgress,
} from "@/composables/reading-progress";

export type {ProgressDoc};

// Minimum rendered text length, and at least one full screen of content, before we
// trust a chars-per-page measurement — avoids locking onto an unrepresentative first
// fragment (e.g. a short title page).
const MIN_TEXT_FOR_MEASURE = 2000;

type LayoutConfig = { value: { pageHeight: number; marginLeft: number; marginRight: number } };
type ProgressStrings = {
    readingProgressPercent: string;
    readingProgressPage: string;
    readingProgressChapter: string;
};

export function useReadingProgress(
    config: Config,
    documents: ProgressDoc[],
    currentVerse: Ref<number | null>,
    currentKey: Ref<string>,
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
        const pageHeight = calculatedConfig.value.pageHeight;
        if (textLength < MIN_TEXT_FOR_MEASURE || el.scrollHeight < pageHeight) return;
        const cpp = estimateCharsPerPage(textLength, el.scrollHeight, pageHeight);
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
        const doc = resolveReadingProgress(documents, currentVerse.value, currentKey.value);
        const rp = doc?.readingProgress;
        if (!rp) return null;

        if (rp.kind === "bible") {
            const percent = computePercent(currentVerse.value ?? rp.unitStart, rp.unitStart, rp.unitEnd);
            if (percent === null) return null;
            return sprintf(strings.readingProgressChapter, Math.round(percent), rp.currentChapter, rp.chapterCount);
        }

        // kind === "book" (EPUB / general book). Anchor ordinals restart per spine item,
        // so derive the whole-book ordinal from the current fragment's cumulative offset
        // plus the in-fragment offset (currentVerse relative to the fragment's local start).
        const fragStart = doc!.ordinalRange?.[0] ?? 0;
        const fragEnd = doc!.ordinalRange?.[1] ?? fragStart;
        const within = Math.min(Math.max(0, (currentVerse.value ?? fragStart) - fragStart), Math.max(0, fragEnd - fragStart));
        const globalOrdinal = rp.fragmentOffset + within;
        const percent = computePercent(globalOrdinal, 0, rp.bookOrdinalSpan);
        if (percent === null) return null;
        const pct = Math.round(percent);

        if (charsPerPage.value === null) {
            return sprintf(strings.readingProgressPercent, pct);
        }
        const totalPages = computeTotalPages(rp.charCount, charsPerPage.value);
        const page = computeCurrentPage(percent, totalPages);
        return sprintf(strings.readingProgressPage, pct, page, totalPages);
    });

    return {progressText};
}
