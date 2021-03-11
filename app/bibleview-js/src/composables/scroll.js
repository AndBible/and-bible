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

export function useScroll(config, {getVerses}) {
    let currentScrollAnimation = null;

    function setToolbarOffset(topOffset, bottomOffset, {doNotScroll = false, immediate = false} = {}) {
        console.log("setToolbarOffset", topOffset, bottomOffset, doNotScroll, immediate);
        const diff = config.topOffset - topOffset;
        config.topOffset = topOffset;
        config.bottomOffset = bottomOffset;
        const delay = immediate ? 0 : 500;

        if(diff !== 0 && !doNotScroll) {
            doScrolling(window.pageYOffset + diff, delay)
        }
    }

    function stopScrolling() {
        if(currentScrollAnimation != null) {
            window.cancelAnimationFrame(currentScrollAnimation);
            currentScrollAnimation = null;
            console.log("Animation ends");
        }
    }

    function doScrolling(elementY, duration) {
        console.log("doScrolling", elementY, duration);
        stopScrolling();
        const startingY = window.pageYOffset;
        const diff = elementY - startingY;
        let start;

        if(duration === 0) {
            window.scrollTo(0, elementY);
            return;
        }

        // Bootstrap our animation - it will get called right before next frame shall be rendered.
        console.log("Animation starts");
        currentScrollAnimation = window.requestAnimationFrame(function step(timestamp) {
            if (!start) start = timestamp;
            // Elapsed milliseconds since start of scrolling.
            const time = timestamp - start;
            // Get percent of completion in range [0, 1].
            const percent = Math.min(time / duration, 1);

            window.scrollTo(0, startingY + diff * percent);

            // Proceed with animation as long as we wanted it to.
            if (time < duration) {
                currentScrollAnimation = window.requestAnimationFrame(step);
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

    function scrollToId(toId, {now = false, highlight = false, ordinal = null, delta = config.topOffset, force = false, duration = 1000} = {}) {
        console.log("scrollToId", toId, now, delta);
        stopScrolling();
        if(delta !== config.topOffset) {
            config.topOffset = delta;
        }
        if(highlight) {
            getVerses(ordinal).forEach(o => o.highlight())
        }
        let toElement = document.getElementById(toId)
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
            if(now===true) {
                window.scrollTo(0, toElement.offsetTop - delta);
            }
            else {
                doScrolling(toElement.offsetTop - delta, duration);
            }
        }
    }

    async function setupContent({jumpToOrdinal = null, jumpToYOffsetRatio = null, topOffset, bottomOffset}  = {}) {
        console.log(`setupContent`, jumpToOrdinal, jumpToYOffsetRatio, topOffset);

        const doScroll = jumpToYOffsetRatio != null && jumpToYOffsetRatio > 0;
        setToolbarOffset(topOffset, bottomOffset, {immediate: true, doNotScroll: !doScroll});

        await nextTick(); // Do scrolling only after view has been settled (fonts etc)
        await nextTick(); // One more nextTick() due to 2-tick behavior of replaceDocument

        if (jumpToOrdinal != null) {
            scrollToId(`v-${jumpToOrdinal}`, {now: true, force: true});
        } else if (doScroll) {
            console.log("jumpToYOffsetRatio", jumpToYOffsetRatio);
            const
                contentHeight = document.documentElement.scrollHeight,
                y = contentHeight * jumpToYOffsetRatio / window.devicePixelRatio;
            doScrolling(y, 0)
        } else {
            console.log("scrolling to beginning of document (now)");
            scrollToId(null, {now: true, force: true});
        }

        console.log("Content is set ready!");
    }

    setupEventBusListener(Events.SET_OFFSETS, setToolbarOffset)
    setupEventBusListener(Events.SCROLL_TO_VERSE, scrollToId)
    setupEventBusListener(Events.SETUP_CONTENT, setupContent)
    return {scrollToId}
}

