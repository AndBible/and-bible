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
import {computed, ref, Ref, watch} from "vue";
import {setupWindowEventListener} from "@/utils";
import {throttle} from "lodash";
import {CalculatedConfig, Config} from "@/composables/config";
import {UseAndroid} from "@/composables/android";
import {useScroll} from "@/composables/scroll";
import {Nullable} from "@/types/common";

export function useVerseNotifier(
    config: Config,
    calculatedConfig: CalculatedConfig,
    mounted: Ref<boolean>,
    {scrolledToOrdinal}: UseAndroid,
    topElement: Ref<HTMLElement | null>,
    {isScrolling}: ReturnType<typeof useScroll>
) {
    const currentVerse = ref<number | null>(null);
    watch(() => currentVerse.value, value => scrolledToOrdinal(value));

    const lineHeight = computed(() => {
        // Update also when font settings etc are changed
        config.fontSize; config.fontFamily; config.lineSpacing;
        if (!mounted.value || !topElement.value) return 1;
        return parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height'));
    });

    let lastDirection = "ltr";
    const step = 10;

    function* iterate(direction = "ltr") {
        if (direction === "ltr") {
            for (let x = window.innerWidth - Math.max(step, config.marginSize.marginRight); x > 0; x -= step) {
                yield x;
            }
        } else {
            for (let x = Math.max(step, config.marginSize.marginLeft); x < window.innerWidth; x += step) {
                yield x;
            }
        }
    }

    // Throttle is preferred over debounce because do not want that bible ref display is
    // totally frozen during scrolling
    const onScroll = throttle(() => {
        if (isScrolling.value) return;
        const y = calculatedConfig.value.topOffset + lineHeight.value * 0.8;

        // Find element, starting from right
        let element: Nullable<HTMLElement>;
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
                        currentVerse.value = parseInt(element.dataset.ordinal!)
                        break;
                    }
                }
            }
        }
    }, 50);

    setupWindowEventListener('scroll', onScroll)
    return {currentVerse}
}
