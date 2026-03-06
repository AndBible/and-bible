/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {onMounted, onUnmounted, Ref, watch} from "vue";
import {UseAndroid} from "@/composables/android";
import {AppSettings} from "@/composables/config";
import {OrdinalRange} from "@/types/client-objects";

const COVERAGE_THRESHOLD = 0.9;

export function useReadingTracker(
    containerRef: Ref<HTMLElement | null>,
    appSettings: AppSettings,
    android: UseAndroid,
    bookInitials: string,
    ordinalRange: OrdinalRange,
    chapterNumber: number,
) {
    const seenOrdinals = new Set<number>();
    let observer: IntersectionObserver | null = null;
    let markedAsRead = false;

    const totalVerses = ordinalRange[1] - ordinalRange[0] + 1;

    function checkCoverage() {
        if (markedAsRead || totalVerses <= 0 || chapterNumber <= 0) return;
        const coverage = seenOrdinals.size / totalVerses;
        if (coverage >= COVERAGE_THRESHOLD) {
            markedAsRead = true;
            android.markChapterRead(bookInitials, ordinalRange[0], chapterNumber, "AUTO_SCROLL");
            cleanup();
        }
    }

    function setupObserver() {
        if (!containerRef.value || markedAsRead) return;

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

    watch(() => appSettings.autoTrackReading, (enabled) => {
        if (enabled && !markedAsRead) {
            setupObserver();
        } else {
            cleanup();
        }
    });

    onMounted(() => {
        if (appSettings.autoTrackReading) {
            setupObserver();
        }
    });

    onUnmounted(() => {
        cleanup();
    });
}
