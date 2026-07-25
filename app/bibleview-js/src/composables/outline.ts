/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
 * If not, see http://www.gnu.org/licenses/.
 */

import {computed, onMounted, onUnmounted, shallowReactive, watch} from "vue";
import {emit, setupEventBusListener} from "@/eventbus";
import {CustomHeading, OrdinalRange} from "@/types/client-objects";

export type OutlineEntry = {
    ordinal: number
    level: number
    text: string
    isCustom: boolean
    headingId?: IdType
    titleIndex?: number
}

export function useOutline(
    documentId: string,
    bookInitials: string,
    ordinalRange: OrdinalRange,
    customHeadings: Map<IdType, CustomHeading>,
) {
    const moduleEntries = shallowReactive<OutlineEntry[]>([]);

    function collectModuleHeadings() {
        const docElem = document.querySelector(`#doc-${documentId}`);
        if (!docElem) return;
        const entries: OutlineEntry[] = [];
        const titleEls = docElem.querySelectorAll<HTMLElement>(".titleStyle[data-ordinal]");
        for (const el of titleEls) {
            const ordinal = el.dataset.ordinal;
            if (!ordinal) continue;
            entries.push({
                ordinal: parseInt(ordinal),
                level: parseInt(el.tagName.substring(1)),
                text: el.textContent?.trim() ?? "",
                isCustom: false,
                titleIndex: el.dataset.titleIndex ? parseInt(el.dataset.titleIndex) : undefined,
            });
        }
        moduleEntries.splice(0, moduleEntries.length, ...entries);
    }

    onMounted(() => collectModuleHeadings());
    onUnmounted(() => { moduleEntries.splice(0); });

    const customEntries = computed<OutlineEntry[]>(() => {
        return Array.from(customHeadings.values())
            .filter(h => h.bookInitials === bookInitials
                && h.ordinal >= ordinalRange[0] && h.ordinal <= ordinalRange[1])
            .map(h => ({
                ordinal: h.ordinal,
                level: h.level,
                text: h.text,
                isCustom: true,
                headingId: h.id,
            }));
    });

    const entries = computed<OutlineEntry[]>(() => {
        const combined = [...moduleEntries, ...customEntries.value];
        combined.sort((a, b) => a.ordinal - b.ordinal);
        return combined;
    });

    const visible = computed(() => entries.value.length > 0);

    return {entries, visible, refresh: collectModuleHeadings};
}

export function openOutline() {
    emit("open_outline");
}
