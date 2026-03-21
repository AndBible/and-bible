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

import {reactive} from "vue";
import {setupEventBusListener} from "@/eventbus";

type MemorizationDelta = {
    addedMemorized: number[],
    removedMemorized: number[],
    addedTargets: number[],
    removedTargets: number[],
}

type MemorizationData = {
    memorized: Set<number>,
    targets: Set<number>,
}

export function useMemorization() {
    const data: MemorizationData = reactive({
        memorized: new Set<number>(),
        targets: new Set<number>(),
    });

    /** Merge data from a newly loaded document (infinite scroll adds chapters incrementally). */
    function mergeData(memorized: number[], targets: number[]) {
        for (const o of memorized) data.memorized.add(o);
        for (const o of targets) data.targets.add(o);
    }

    /** Apply incremental delta from MemorizationDataChangedEvent. */
    function applyDelta(delta: MemorizationDelta) {
        for (const o of delta.addedMemorized) data.memorized.add(o);
        for (const o of delta.removedMemorized) data.memorized.delete(o);
        for (const o of delta.addedTargets) data.targets.add(o);
        for (const o of delta.removedTargets) data.targets.delete(o);
    }

    setupEventBusListener("update_memorization_data",
        (delta: MemorizationDelta) => applyDelta(delta)
    );

    function isMemorized(ordinal: number): boolean {
        return data.memorized.has(ordinal);
    }

    function isTarget(ordinal: number): boolean {
        return data.targets.has(ordinal);
    }

    return {isMemorized, isTarget, mergeData};
}
