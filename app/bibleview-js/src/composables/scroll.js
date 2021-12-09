/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import {nextTick} from "@vue/runtime-core";
import {Events, setupEventBusListener} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {isInViewport} from "@/utils";

export function useScroll(config, appSettings, calculatedConfig, {highlightVerse, resetHighlights}, documentPromise) {
    const currentScrollAnimation = ref(null);
    const isScrolling = computed(() => currentScrollAnimation.value != null)

    function setToolbarOffset(topOffset, bottomOffset, {doNotScroll = false, immediate = false} = {}) {
        console.log("setToolbarOffset", {topOffset, bottomOffset, doNotScroll, immediate});
        const diff = appSettings.topOffset - topOffset;
        appSettings.topOffset = topOffset;
        appSettings.bottomOffset = bottomOffset;
        const delay = immediate ? 0 : 500;

        if(diff !== 0 && !doNotScroll) {
            doScrolling(window.pageYOffset + diff, delay)
        }
    }

    function stopScrolling(nullify = true) {
        if(currentScrollAnimation.value != null) {
            window.cancelAnimationFrame(currentScrollAnimation.value);
            if(nullify) {
                currentScrollAnimation.value = null;
            } else {
                currentScrollAnimation.value = -1;
            }
            console.log("Animation ends");
        }
    }

    function doScrolling(elementY, duration = 1000) {
        console.log("doScrolling", elementY, duration);
        const noScrolling = duration === 0;
        stopScrolling(!noScrolling);
        const startingY = window.pageYOffset;
        const diff = elementY - startingY;
        let start;

        if(noScrolling) {
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

    function attributesToString(elem) {
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

    function scrollToId(toId, {onlyIfInvisible = false, now = false, highlight = false, ordinalStart = null, ordinalEnd = null, force = false, duration = 1000} = {}) {
        console.log("scrollToId", {toId, now, highlight, force, duration, ordinalStart, ordinalEnd});
        stopScrolling();
        let delta = calculatedConfig.value.topOffset;
        if(highlight && ordinalStart) {
            resetHighlights();
            if(!ordinalEnd) {
                ordinalEnd = ordinalStart;
            }
            for(let ordinal = ordinalStart; ordinal <= ordinalEnd; ordinal ++) {
                highlightVerse(ordinal);
            }
        }
        let toElement = document.getElementById(toId);
        if(onlyIfInvisible && isInViewport(toElement)) return;

        if(force && toElement == null) {
            toElement = document.getElementById("top")
        }

        if (toElement != null) {
            const diff = toElement.offsetTop - window.pageYOffset;
            if(Math.abs(diff) > 800 / window.devicePixelRatio) {
                now = true;
            }
            console.log("Scrolling to", toElement, attributesToString(toElement), toElement.offsetTop - delta);
            const style = window.getComputedStyle(toElement);
            const lineHeight = parseFloat(style.getPropertyValue('line-height'));
            const fontSize = parseFloat(style.getPropertyValue('font-size'));
            delta += 0.5*(lineHeight - fontSize);
            if(now === true) {
                window.scrollTo(0, toElement.offsetTop - delta);
            }
            else {
                doScrolling(toElement.offsetTop - delta, duration);
            }
        }
    }

    async function setupContent({jumpToOrdinal = null, jumpToAnchor = null, jumpToId = null, topOffset, bottomOffset}  = {}) {
        await documentPromise.value;
        console.log(`setupContent`, jumpToOrdinal, jumpToAnchor, topOffset);

        setToolbarOffset(topOffset, bottomOffset, {immediate: true, doNotScroll: true});

        await nextTick(); // Do scrolling only after view has been settled (fonts etc)

        if (jumpToOrdinal != null) {
            scrollToId(`o-${jumpToOrdinal}`, {now: true, force: true});
        } else if (jumpToAnchor !== null) {
            scrollToId(`o-${jumpToAnchor}`, {now: true, force: true});
        } else if(jumpToId !== null) {
            scrollToId(jumpToId, {now: true, force: true});
        } else {
            console.log("scrolling to beginning of document (now)");
            scrollToId(null, {now: true, force: true});
        }

        console.log("Content is set ready!");
    }

    setupEventBusListener(Events.SET_OFFSETS, setToolbarOffset)
    setupEventBusListener(Events.SCROLL_TO_VERSE, scrollToId)
    setupEventBusListener(Events.SETUP_CONTENT, setupContent)
    return {scrollToId, isScrolling, doScrolling}
}

