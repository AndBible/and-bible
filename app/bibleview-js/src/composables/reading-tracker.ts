/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import {inject, onMounted, onUnmounted, ref, Ref, watch} from "vue";
import {OrdinalRange} from "@/types/client-objects";
import {androidKey, configKey} from "@/types/constants";
import {setupEventBusListener} from "@/eventbus";

const COVERAGE_THRESHOLD = 0.9;

export function useReadingTracker(
    containerRef: Ref<HTMLElement | null>,
    bookInitials: string,
    ordinalRange: OrdinalRange,
    chapterNumber: number,
    initialReadCount: number,
) {
    const config = inject(configKey)!;
    const android = inject(androidKey)!;

    const chapterReadCount = ref(initialReadCount);
    const seenOrdinals = new Set<number>();
    let observer: IntersectionObserver | null = null;
    let autoTrackDone = chapterReadCount.value > 0;

    const totalVerses = ordinalRange[1] - ordinalRange[0] + 1;

    function checkCoverage() {
        if (autoTrackDone || totalVerses <= 0) return;
        const coverage = seenOrdinals.size / totalVerses;
        if (coverage >= COVERAGE_THRESHOLD) {
            autoTrackDone = true;
            android.recordChapterRead(bookInitials, ordinalRange[0], chapterNumber, "AUTO_SCROLL");
            cleanup();
        }
    }

    function setupObserver() {
        if (!containerRef.value || autoTrackDone) return;

        observer = new IntersectionObserver(
            (entries) => {
                for (const entry of entries) {
                    if (entry.isIntersecting) {
                        const ordinal = parseInt((entry.target as HTMLElement).dataset.ordinal!);
                        if (!isNaN(ordinal)) {
                            seenOrdinals.add(ordinal);
                        }
                    }
                }
                checkCoverage();
            },
            {threshold: 0.5}
        );

        const verseElements = containerRef.value.querySelectorAll(".verse.ordinal[data-ordinal]");
        for (const el of verseElements) {
            observer.observe(el);
        }
    }

    function cleanup() {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
    }

    function toggleChapterRead() {
        // Each tap records a new history entry. To remove reads the user opens the
        // history dialog via long-press (handled in BibleDocument.vue).
        android.recordChapterRead(bookInitials, ordinalRange[0], chapterNumber, "MANUAL");
        chapterReadCount.value++;
    }

    function openChapterReadHistory() {
        android.openChapterReadHistory(bookInitials, ordinalRange[0], chapterNumber);
    }

    setupEventBusListener("update_chapter_read_status", (data: {chapter: number, count: number}) => {
        if (data.chapter === chapterNumber) {
            chapterReadCount.value = data.count;
        }
    });

    watch(() => config.autoTrackReading, (enabled) => {
        if (enabled && !autoTrackDone) {
            setupObserver();
        } else {
            cleanup();
        }
    });

    onMounted(() => {
        if (config.autoTrackReading && !autoTrackDone) {
            setupObserver();
        }
    });

    onUnmounted(() => {
        cleanup();
    });

    return {chapterReadCount, toggleChapterRead, openChapterReadHistory};
}
