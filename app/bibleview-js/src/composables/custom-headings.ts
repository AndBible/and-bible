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

import {computed, onMounted, onUnmounted, reactive, ref, watch} from "vue";
import {emit, setupEventBusListener} from "@/eventbus";
import {addEventFunction, EventPriorities} from "@/utils";
import {CustomHeading, HeadingOverride, OrdinalRange} from "@/types/client-objects";
import {BibleDocumentType} from "@/types/documents";
import {Config} from "@/composables/config";

export type HeadingMenuPayload =
    { kind: "add", bookInitials: string, v11n: string, ordinal: number } |
    { kind: "custom", headingId: IdType } |
    { kind: "module", bookInitials: string, v11n: string, ordinal: number, titleIndex: number, text: string, level: number }

export function headingOverrideKey(bookInitials: string, ordinal: number, titleIndex: number) {
    return `${bookInitials}-${ordinal}-${titleIndex}`;
}

export function useGlobalCustomHeadings() {
    const customHeadings = reactive<Map<IdType, CustomHeading>>(new Map());
    const headingOverrides = reactive<Map<string, HeadingOverride>>(new Map());

    function updateFromDocument(doc: BibleDocumentType) {
        for (const h of doc.customHeadings || []) {
            customHeadings.set(h.id, h);
        }
        for (const o of doc.headingOverrides || []) {
            headingOverrides.set(headingOverrideKey(o.bookInitials, o.ordinal, o.titleIndex), o);
        }
    }

    setupEventBusListener("custom_headings_updated",
        ({bookInitials, headings, overrides}: { bookInitials: string, headings: CustomHeading[], overrides: HeadingOverride[] }) => {
            for (const [id, h] of customHeadings) {
                if (h.bookInitials === bookInitials) customHeadings.delete(id);
            }
            for (const h of headings) {
                customHeadings.set(h.id, h);
            }
            for (const [key, o] of headingOverrides) {
                if (o.bookInitials === bookInitials) headingOverrides.delete(key);
            }
            for (const o of overrides) {
                headingOverrides.set(headingOverrideKey(o.bookInitials, o.ordinal, o.titleIndex), o);
            }
        });

    setupEventBusListener("clear_document", () => {
        customHeadings.clear();
        headingOverrides.clear();
    });

    window.bibleViewDebug.customHeadings = customHeadings;
    window.bibleViewDebug.headingOverrides = headingOverrides;

    return {
        customHeadings,
        headingOverrides,
        updateFromDocument,
    }
}

export function useCustomHeadings(
    documentId: string,
    bookInitials: string,
    ordinalRange: OrdinalRange,
    {customHeadings}: { customHeadings: Map<IdType, CustomHeading> },
    config: Config,
    openOutline?: () => void,
    outlineLabel?: string,
) {
    const isMounted = ref(0);
    const undoList: (() => void)[] = [];

    onMounted(() => isMounted.value++);
    onUnmounted(() => {
        isMounted.value--;
        undoAll();
    });

    const documentHeadings = computed(() => {
        if (!config.showSectionTitles) return [];
        return Array.from(customHeadings.values())
            .filter(h => h.bookInitials === bookInitials
                && h.ordinal >= ordinalRange[0] && h.ordinal <= ordinalRange[1])
            .sort((a, b) => a.ordinal - b.ordinal || a.id.localeCompare(b.id));
    });

    function undoAll() {
        undoList.splice(0).forEach(u => u());
    }

    function applyHeadings() {
        undoAll();
        if (!isMounted.value) return;
        for (const h of documentHeadings.value) {
            const verseElem = document.querySelector(`#doc-${documentId} #v-${h.ordinal}`);
            if (!verseElem || !verseElem.parentNode) continue;
            const wrapper = document.createElement("div");
            wrapper.classList.add("title-wrapper", "custom-heading-wrapper", "skip-offset");
            wrapper.dataset.ordinal = String(h.ordinal);
            wrapper.dataset.level = String(h.level);
            wrapper.dataset.isCustom = "true";

            if (openOutline) {
                const outlineBtn = document.createElement("button");
                outlineBtn.classList.add("title-outline-btn");
                outlineBtn.textContent = "≡";
                outlineBtn.setAttribute("aria-label", outlineLabel ?? "");
                outlineBtn.addEventListener("click", (e: Event) => {
                    e.stopPropagation();
                    openOutline();
                });
                outlineBtn.addEventListener("keydown", (e: KeyboardEvent) => {
                    if (e.key === "Enter" || e.key === " ") e.stopPropagation();
                });
                wrapper.appendChild(outlineBtn);
            }

            const headingElem = document.createElement(`h${h.level}`);
            headingElem.classList.add("titleStyle", "custom-heading");
            headingElem.textContent = h.text;
            headingElem.tabIndex = 0;
            headingElem.addEventListener("click", (event: MouseEvent) => {
                addEventFunction(event, () => {
                    const payload: HeadingMenuPayload = {kind: "custom", headingId: h.id};
                    emit("open_heading_menu", payload);
                }, {priority: EventPriorities.HEADING, title: h.text});
            });
            headingElem.addEventListener("keydown", (event: KeyboardEvent) => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    addEventFunction(event, () => {
                        const payload: HeadingMenuPayload = {kind: "custom", headingId: h.id};
                        emit("open_heading_menu", payload);
                    }, {priority: EventPriorities.HEADING, title: h.text});
                }
            });
            wrapper.appendChild(headingElem);
            verseElem.parentNode.insertBefore(wrapper, verseElem);
            undoList.push(() => wrapper.remove());
        }
    }

    watch([documentHeadings, isMounted], applyHeadings);
}
