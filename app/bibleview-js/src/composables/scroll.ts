/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {computed, nextTick, ref, Ref, watch} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {isInViewport} from "@/utils";
import {AppSettings, CalculatedConfig, Config} from "@/composables/config";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {Nullable} from "@/types/common";

export function useScroll(
    config: Config,
    appSettings: AppSettings,
    calculatedConfig: CalculatedConfig,
    highlight: ReturnType<typeof useOrdinalHighlight>,
    documentPromise: Ref<Promise<void> | null>,
) {
    const {highlightOrdinal, resetHighlights} = highlight;
    const currentScrollAnimation = ref<number | null>(null);
    const isScrolling = computed(() => currentScrollAnimation.value != null)

    watch(isScrolling, v => {
        if (v) {
            document.addEventListener("touchstart", () => stopScrolling())
        } else {
            document.removeEventListener("touchstart", () => stopScrolling())
        }
    }, {flush: 'sync'});

    function setToolbarOffset(
        topOffset: number,
        bottomOffset: number,
        {doNotScroll = false, immediate = false} = {}
    ) {
        console.log("setToolbarOffset", {topOffset, bottomOffset, doNotScroll, immediate});
        const diff = appSettings.topOffset - topOffset;
        appSettings.topOffset = topOffset;
        appSettings.bottomOffset = bottomOffset;
        const delay = immediate ? 0 : 500;

        if (diff !== 0 && !doNotScroll) {
            doScrolling(window.scrollY + diff, delay)
        }
    }

    function stopScrolling(nullify = true) {
        console.log("stopScrolling!");
        if (currentScrollAnimation.value != null) {
            window.cancelAnimationFrame(currentScrollAnimation.value);
            if (nullify) {
                currentScrollAnimation.value = null;
            } else {
                currentScrollAnimation.value = -1;
            }
            console.log("Animation ends");
        }
    }

    function doScrolling(elementY: number, duration = 1000) {
        console.log("doScrolling", elementY, duration);
        const noScrolling = duration === 0 || appSettings.disableAnimations;
        stopScrolling(!noScrolling);
        const startingY = window.scrollY;
        const diff = elementY - startingY;
        let start: number;

        if (noScrolling) {
            window.scrollTo(0, elementY);
            return;
        }

        // Bootstrap our animation - it will get called right before next frame shall be rendered.
        console.log("Animation starts");
        currentScrollAnimation.value = window.requestAnimationFrame(function step(timestamp) {
            if (!start) start = timestamp;
            // Elapsed milliseconds since start of scrolling.
            const time = timestamp - start;
            // Get percent of completion in range [0, 1].
            const percent = Math.min(time / duration, 1);

            window.scrollTo(0, startingY + diff * percent);

            // Proceed with animation as long as we wanted it to.
            if (time < duration) {
                currentScrollAnimation.value = window.requestAnimationFrame(step);
            } else {
                stopScrolling();
            }
        })
    }

    function attributesToString(elem: HTMLElement) {
        try {
            let result = "";
            for (const attr of elem.attributes) {
                result += `${attr.name}: ${attr.value}, `
            }
            return `[${elem.tagName} ${result} (${elem.innerText.slice(0, 50)}...)]`;
        } catch (e) {
            console.error("attributesToString fails", e);
            return `[${elem.tagName} (${elem.innerText.slice(0, 50)}...)]`;
        }
    }

    function scrollToId(
        toId: Nullable<string>,
        {
            onlyIfInvisible = false,
            now = false,
            highlight = false,
            ordinalStart = null,
            ordinalEnd = null,
            force = false,
            duration,
            bookInitials,
            osisRef,
        }: Partial<{
            onlyIfInvisible: boolean,
            now: boolean,
            highlight: boolean,
            ordinalStart: Nullable<number>,
            ordinalEnd: Nullable<number>,
            force: boolean,
            duration?: number,
            bookInitials?: string,
            osisRef?: string
        }> = {}) {
        console.log("scrollToId", {toId, now, highlight, force, duration, ordinalStart, ordinalEnd});
        if (appSettings.disableAnimations) {
            now = true;
        }
        stopScrolling();
        let delta = calculatedConfig.value.topOffset;
        if (highlight && ordinalStart) {
            resetHighlights();
            if (!ordinalEnd) {
                ordinalEnd = ordinalStart;
            }
            for (let ordinal = ordinalStart; ordinal <= ordinalEnd; ordinal++) {
                highlightOrdinal(ordinal, bookInitials, osisRef);
            }
        }
        let toElement = toId ? document.getElementById(toId) : null;
        if (onlyIfInvisible && toElement && isInViewport(toElement)) return;

        if (force && toElement == null) {
            toElement = document.getElementById("top")
        }

        if (toElement != null) {
            const diff = toElement.offsetTop - window.scrollY;
            if (Math.abs(diff) > 800 / window.devicePixelRatio) {
                now = true;
            }
            console.log("Scrolling to", toElement, attributesToString(toElement), toElement.offsetTop - delta);
            const style = window.getComputedStyle(toElement);
            const lineHeight = parseFloat(style.getPropertyValue('line-height'));
            const fontSize = parseFloat(style.getPropertyValue('font-size'));
            delta += 0.5 * (lineHeight - fontSize);
            if (now) {
                window.scrollTo(0, toElement.offsetTop - delta);
            } else {
                doScrolling(toElement.offsetTop - delta, duration);
            }
        }
    }

    async function setupContent(
        {
            jumpToOrdinal = null,
            jumpToAnchor = null,
            jumpToId = null,
            topOffset,
            bottomOffset
        }: {
            jumpToOrdinal: Nullable<number>,
            jumpToAnchor: Nullable<number>,
            jumpToId: Nullable<string>,
            topOffset: number,
            bottomOffset: number
        }) {
        await documentPromise.value;
        console.log(`setupContent`, jumpToOrdinal, jumpToAnchor, topOffset);

        setToolbarOffset(topOffset, bottomOffset, {immediate: true, doNotScroll: true});

        await nextTick(); // Do scrolling only after view has been settled (fonts etc)

        if (jumpToOrdinal != null) {
            scrollToId(`o-${jumpToOrdinal}`, {now: true, force: true});
        } else if (jumpToAnchor !== null) {
            scrollToId(`o-${jumpToAnchor}`, {now: true, force: true});
        } else if (jumpToId !== null) {
            scrollToId(jumpToId, {now: true, force: true});
        } else {
            console.log("scrolling to beginning of document (now)");
            scrollToId(null, {now: true, force: true});
        }

        console.log("Content is set ready!");
    }

    setupEventBusListener("set_offsets", setToolbarOffset)
    setupEventBusListener("scroll_to_verse", scrollToId)
    setupEventBusListener("setup_content", setupContent)
    return {scrollToId, isScrolling, doScrolling}
}

