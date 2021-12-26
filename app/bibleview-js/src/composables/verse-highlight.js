/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {reactive} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";

export function useVerseHighlight() {
    const highlightedVerses = reactive(new Set());
    const undoCustomHighlights = reactive([]);

    const hasHighlights = computed(() => highlightedVerses.size > 0 || undoCustomHighlights.length > 0);

    function resetHighlights(onlyVerses = false) {
        highlightedVerses.clear();
        if(!onlyVerses) {
            undoCustomHighlights.forEach(f => f())
            undoCustomHighlights.splice(0);
        }
    }

    function highlightVerse(ordinal) {
        highlightedVerses.add(ordinal);
    }

    function addCustom(f) {
        undoCustomHighlights.push(f);
    }

    return {highlightVerse, addCustom, highlightedVerses, resetHighlights, hasHighlights}
}
