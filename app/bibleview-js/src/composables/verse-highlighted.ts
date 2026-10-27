/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {computed, inject} from "vue";
import {ordinalHighlightKey, verseInfoKey} from "@/types/constants";

/**
 * Whether the verse this component is rendered inside is currently highlighted (#3785). The verse's inline
 * background does not paint inside block-level elements (list items, paragraphs, table cells), so those
 * wrap their content in a `highlight-transition` span with the `isHighlighted` class from this.
 * False outside a verse.
 */
export function useVerseHighlighted() {
    const verseInfo = inject(verseInfoKey, null);
    const ordinalHighlight = inject(ordinalHighlightKey, null);
    return computed(() => !!verseInfo && !!ordinalHighlight?.isHighlighted(verseInfo.ordinal));
}
