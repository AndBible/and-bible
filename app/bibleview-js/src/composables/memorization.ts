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

import {nextTick, onMounted, reactive, Ref} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {Config} from "@/composables/config";

type MemorizationDelta = {
    addedMemorized: number[],
    removedMemorized: number[],
    addedTargets: number[],
    removedTargets: number[],
}

type IndicatorType = "memorized" | "target";

const INDICATOR_CLASS = "memorization-indicator";

/**
 * Groups consecutive ordinals into [start, end] ranges.
 */
export function groupConsecutive(ordinals: number[]): [number, number][] {
    if (ordinals.length === 0) return [];
    const sorted = [...new Set(ordinals)].sort((a, b) => a - b);
    const ranges: [number, number][] = [];
    let start = sorted[0], end = sorted[0];
    for (let i = 1; i < sorted.length; i++) {
        if (sorted[i] === end + 1) {
            end = sorted[i];
        } else {
            ranges.push([start, end]);
            start = sorted[i];
            end = sorted[i];
        }
    }
    ranges.push([start, end]);
    return ranges;
}

function getFirstLineTop(elem: Element): number {
    const rects = elem.getClientRects();
    return rects.length > 0 ? rects[0].top : elem.getBoundingClientRect().top;
}

/**
 * Gets the effective bottom for the indicator line of the last verse in a range.
 * If the last verse's final line is shared with the next (non-range) verse,
 * stops at the previous line — unless the verse fits on a single line.
 */
function getEffectiveBottom(lastElem: Element, nextOrdinal: number, container: HTMLElement, documentId: string): number {
    const rects = lastElem.getClientRects();
    if (rects.length === 0) return lastElem.getBoundingClientRect().bottom;
    if (rects.length === 1) return rects[0].bottom;

    const nextElem = container.querySelector(`#doc-${documentId} #o-${nextOrdinal}`);
    if (nextElem) {
        const nextRects = nextElem.getClientRects();
        if (nextRects.length > 0) {
            const lastLineTop = rects[rects.length - 1].top;
            const nextFirstTop = nextRects[0].top;
            if (Math.abs(lastLineTop - nextFirstTop) < 3) {
                return rects[rects.length - 2].bottom;
            }
        }
    }
    return rects[rects.length - 1].bottom;
}

function createIndicatorElement(
    container: HTMLElement,
    firstOrdinal: number,
    lastOrdinal: number,
    type: IndicatorType,
    documentId: string,
): HTMLElement | null {
    const firstElem = container.querySelector(`#doc-${documentId} #o-${firstOrdinal}`) as HTMLElement | null;
    const lastElem = container.querySelector(`#doc-${documentId} #o-${lastOrdinal}`) as HTMLElement | null;
    if (!firstElem || !lastElem) return null;

    const containerRect = container.getBoundingClientRect();
    const firstTop = getFirstLineTop(firstElem);
    const lastBottom = getEffectiveBottom(lastElem, lastOrdinal + 1, container, documentId);

    const top = firstTop - containerRect.top;
    const height = lastBottom - firstTop;
    if (height <= 0) return null;

    const line = document.createElement("div");
    line.className = `${INDICATOR_CLASS} ${INDICATOR_CLASS}--${type}`;
    line.style.position = "absolute";
    line.style.left = "-6px";
    line.style.width = "3px";
    line.style.top = `${top}px`;
    line.style.height = `${height}px`;
    line.style.pointerEvents = "none";
    line.dataset.startOrdinal = String(firstOrdinal);
    line.dataset.endOrdinal = String(lastOrdinal);
    line.dataset.type = type;
    return line;
}

export function useMemorization(config: Config) {
    const memorized = reactive(new Set<number>());
    const targets = reactive(new Set<number>());

    /** Merge data from a newly loaded document (infinite scroll adds chapters incrementally). */
    function mergeData(newMemorized: number[], newTargets: number[]) {
        for (const o of newMemorized) memorized.add(o);
        for (const o of newTargets) targets.add(o);
    }

    /** Apply incremental delta from MemorizationDataChangedEvent. */
    function applyDelta(delta: MemorizationDelta) {
        for (const o of delta.addedMemorized) memorized.add(o);
        for (const o of delta.removedMemorized) memorized.delete(o);
        for (const o of delta.addedTargets) targets.add(o);
        for (const o of delta.removedTargets) targets.delete(o);
    }

    setupEventBusListener("update_memorization_data",
        (delta: MemorizationDelta) => applyDelta(delta)
    );

    // --- Indicator overlay rendering ---

    function renderIndicators(container: HTMLElement, documentId: string) {
        container.querySelectorAll(`.${INDICATOR_CLASS}`).forEach(el => el.remove());

        if (!config.showMemorizationIndicators) {
            return;
        }

        // Target indicators (render first so memorized overlaps on top)
        const targetOnlyOrdinals = [...targets].filter(o => !memorized.has(o));
        for (const [start, end] of groupConsecutive(targetOnlyOrdinals)) {
            const el = createIndicatorElement(container, start, end, "target", documentId);
            if (el) container.appendChild(el);
        }

        // Memorized indicators
        for (const [start, end] of groupConsecutive([...memorized])) {
            const el = createIndicatorElement(container, start, end, "memorized", documentId);
            if (el) container.appendChild(el);
        }
    }

    /**
     * Sets up indicator rendering for a document container.
     * Renders on mount and re-renders on config/data changes.
     */
    function setupIndicatorRendering(containerRef: Ref<HTMLElement | null>, documentId: string) {
        const render = () => {
            if (containerRef.value) {
                renderIndicators(containerRef.value, documentId);
            }
        };
        onMounted(() => nextTick(render));
        setupEventBusListener("set_config", () => nextTick(render));
        setupEventBusListener("update_memorization_data", () => nextTick(render));
    }

    return {memorized, targets, mergeData, setupIndicatorRendering};
}
