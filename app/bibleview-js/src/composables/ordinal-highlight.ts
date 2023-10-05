/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {computed, reactive} from "vue";

export function useOrdinalHighlight() {
    const highlightedOrdinals: Set<number> = reactive(new Set());
    const undoCustomHighlights: (() => void)[] = reactive([]);

    const hasHighlights = computed(() => highlightedOrdinals.size > 0 || undoCustomHighlights.length > 0);

    function resetHighlights(onlyVerses = false) {
        highlightedOrdinals.clear();
        if (!onlyVerses) {
            undoCustomHighlights.forEach(f => f())
            undoCustomHighlights.splice(0);
        }
    }

    function highlightOrdinal(ordinal: number) {
        highlightedOrdinals.add(ordinal);
    }

    function addCustom(func: () => void) {
        undoCustomHighlights.push(func);
    }

    return {highlightOrdinal, addCustom, highlightedOrdinals, resetHighlights, hasHighlights}
}
