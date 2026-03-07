/*
 * Copyright (c) 2020-2024 Tuomas Airaksinen and the AndBible contributors.
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

import {onMounted, ref, watch} from "vue";

export function unescapeXmlEntities(text: string): string {
    return text
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&amp;/g, "&");
}

export function useSlotHtmlContent() {
    const slotContent = ref<HTMLElement | null>(null);
    const rawContent = ref("");

    onMounted(() => {
        if (slotContent.value) {
            rawContent.value = slotContent.value.innerText;
        }
    });

    watch(() => slotContent.value?.innerText, (newVal) => {
        if (newVal) {
            rawContent.value = newVal;
        }
    });

    function handleClick(event: MouseEvent) {
        const target = event.target as HTMLElement;
        const link = target.closest("a") as HTMLAnchorElement | null;
        if (link) {
            event.preventDefault();
            const href = link.getAttribute("href");
            if (href) {
                window.location.assign(href);
            }
        }
    }

    return {slotContent, rawContent, handleClick};
}
