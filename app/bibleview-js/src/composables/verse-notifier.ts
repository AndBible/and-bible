/*
 * Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import {ComputedRef, ref, watch} from "vue";
import {setupWindowEventListener} from "@/utils";
import {throttle} from "lodash";
import {CalculatedConfig} from "@/composables/config";
import {UseAndroid} from "@/composables/android";
import {useScroll} from "@/composables/scroll";
import {Nullable} from "@/types/common";

export function useVerseNotifier(
    calculatedConfig: CalculatedConfig,
    {scrolledToOrdinal}: UseAndroid,
    {isScrolling}: ReturnType<typeof useScroll>,
    lineHeight: ComputedRef<number>,
) {
    const currentVerse = ref<number | null>(null);
    const currentKey = ref<string>("")
    // Watch both values - for GenBooks/MyDocuments, the key (osisRef) changes even when ordinal stays 0
    watch(
        [() => currentVerse.value, () => currentKey.value],
        ([verse, key]) => scrolledToOrdinal(key, verse)
    );

    let lastDirection = "ltr";
    // scrollY at the point the currently displayed verse was last confirmed. Anchoring
    // direction detection here (instead of to the previous throttled sample) survives the
    // sample-to-sample jitter of real touch/momentum scrolling - see the guard below.
    let lastAcceptedScrollY = window.scrollY;
    // How far above the anchor the guard stays active. The superscript bleed it protects
    // against only occurs within a line or so of a verse boundary - which is exactly where
    // the anchor sits, since the anchor is only moved when a verse is confirmed. Bounding it
    // keeps an anchor that has gone stale (a bare window.scrollTo from the resize handler, an
    // infinite-scroll insert at the top) from blocking forward movement indefinitely.
    const jitterWindow = () => 2 * lineHeight.value;
    const step = 10;

    function* iterate(direction = "ltr") {
        if (direction === "ltr") {
            for (let x = window.innerWidth - Math.max(step, calculatedConfig.value.marginRight); x > 0; x -= step) {
                yield x;
            }
        } else {
            for (let x = Math.max(step, calculatedConfig.value.marginLeft); x < window.innerWidth; x += step) {
                yield x;
            }
        }
    }

    // Throttle is preferred over debounce because do not want that bible ref display is
    // totally frozen during scrolling
    const onScroll = throttle(() => {
        if (isScrolling.value) {
            // Programmatic scrolling (scrollToId, toolbar offset compensation) moves scrollY
            // without the user scrolling, and a config change reflows the document underneath
            // it. Re-anchor rather than leaving the anchor in the pre-jump coordinate system.
            lastAcceptedScrollY = window.scrollY;
            return;
        }
        const distanceUp = lastAcceptedScrollY - window.scrollY;
        const scrollingUp = distanceUp > 0 && distanceUp < jitterWindow();
        let y = calculatedConfig.value.topOffset + lineHeight.value * 0.3;

        // Find element, starting from right
        let element: Nullable<HTMLElement>;
        while (y < window.innerHeight) {
            let directionChanged = true;
            while (directionChanged) {
                directionChanged = false;
                for (const x of iterate(lastDirection)) {
                    element = document.elementFromPoint(x, y) as Nullable<HTMLElement>
                    if (element) {
                        element = element.closest(".ordinal") as Nullable<HTMLElement>;
                        if (element) {
                            const direction = window.getComputedStyle(element).getPropertyValue("direction");
                            if (direction !== lastDirection) {
                                directionChanged = true;
                                lastDirection = direction;
                                break;
                            }
                            const newVerse = parseInt(element.dataset.ordinal!)
                            const doc = element.closest(".document") as Nullable<HTMLElement>
                            const newKey = doc?.dataset.osisRef || ""
                            // While scrolling up, the detected verse must never advance past the
                            // previously confirmed one within the same document - a superscript verse
                            // number can visually bleed into the line above (negative top offset) and
                            // make the elementFromPoint probe hit the next verse too early (#3865).
                            if (scrollingUp && currentVerse.value !== null && newKey === currentKey.value
                                && newVerse > currentVerse.value) {
                                return;
                            }
                            // Only move the anchor on a real change - re-confirming the same verse
                            // (a no-op pass through here) must not let the anchor drift with every
                            // sample, or it loses its resistance to jitter (see the guard above).
                            if (newVerse !== currentVerse.value || newKey !== currentKey.value) {
                                lastAcceptedScrollY = window.scrollY;
                            }
                            currentVerse.value = newVerse
                            currentKey.value = newKey
                            return;
                        }
                    }
                }
                y += lineHeight.value * 0.6;
            }
        }
    }, 50);

    setupWindowEventListener('scroll', onScroll)
    return {currentVerse, currentKey}
}
