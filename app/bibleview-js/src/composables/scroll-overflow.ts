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

import {ref, onMounted, onBeforeUnmount, watch} from "vue";
import type {Ref} from "vue";

/**
 * Tracks horizontal scroll overflow state for an element.
 * Returns reactive booleans indicating whether content is hidden on the left/right.
 */
export function useScrollOverflow(elementRef: Ref<HTMLElement | null>) {
    const canScrollLeft = ref(false);
    const canScrollRight = ref(false);

    let resizeObserver: ResizeObserver | null = null;

    function update() {
        const el = elementRef.value;
        if (!el) {
            canScrollLeft.value = false;
            canScrollRight.value = false;
            return;
        }
        canScrollLeft.value = el.scrollLeft > 1;
        canScrollRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 1;
    }

    function setup() {
        const el = elementRef.value;
        if (!el) return;
        el.addEventListener("scroll", update, {passive: true});
        resizeObserver = new ResizeObserver(update);
        resizeObserver.observe(el);
        update();
    }

    function teardown() {
        const el = elementRef.value;
        if (el) {
            el.removeEventListener("scroll", update);
        }
        resizeObserver?.disconnect();
        resizeObserver = null;
    }

    onMounted(() => setup());
    onBeforeUnmount(() => teardown());

    watch(elementRef, (newEl, oldEl) => {
        if (oldEl) teardown();
        if (newEl) setup();
    });

    return {canScrollLeft, canScrollRight};
}
