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

export function useScroll(config) {
    let currentScrollAnimation = null;

    function setToolbarOffset(value, {doNotScroll = false, immediate = false} = {}) {
        console.log("setToolbarOffset", value, doNotScroll, immediate);
        const diff = config.toolbarOffset - value;
        config.toolbarOffset = value;
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

    function scrollToVerse(toId, now, delta = config.toolbarOffset) {
        console.log("scrollToVerse", toId, now, delta);
        stopScrolling();
        if(delta !== config.toolbarOffset) {
            config.toolbarOffset = delta;
        }
        const toElement = document.getElementById(toId) || document.getElementById("top");

        if (toElement != null) {
            const diff = toElement.offsetTop - window.pageYOffset;
            if(Math.abs(diff) > 800 / window.devicePixelRatio) {
                now = true;
            }
            console.log("Scrolling to", toElement, attributesToString(toElement), toElement.offsetTop - delta);
            const lineHeight = parseFloat(window.getComputedStyle(toElement).getPropertyValue('line-height'));
            if(config.lineSpacing != null) {
                const extra = (config.lineSpacing - 1) * 0.5;
                console.log(`Adding extra ${extra}`);
                delta += (lineHeight/config.lineSpacing) * extra;
            }
            if(now===true) {
                window.scrollTo(0, toElement.offsetTop - delta);
            }
            else {
                doScrolling(toElement.offsetTop - delta, 1000);
            }
        }
    }

    async function setupContent({jumpToOrdinal = null, jumpToYOffsetRatio = null, toolBarOffset}  = {}) {
        console.log(`setupContent`, jumpToOrdinal, jumpToYOffsetRatio, toolBarOffset);

        const doScroll = jumpToYOffsetRatio != null && jumpToYOffsetRatio > 0;
        setToolbarOffset(toolBarOffset, {immediate: true, doNotScroll: !doScroll});

        await nextTick(); // Do scrolling only after view has been settled (fonts etc)

        if (jumpToOrdinal != null) {
            scrollToVerse(`v-${jumpToOrdinal}`, true);
            //enableVerseLongTouchSelectionMode();
        } else if (doScroll) {
            console.log("jumpToYOffsetRatio", jumpToYOffsetRatio);
            const
                contentHeight = document.documentElement.scrollHeight,
                y = contentHeight * jumpToYOffsetRatio / window.devicePixelRatio;
            doScrolling(y, 0)
        } else {
            console.log("scrolling to beginning of document (now)");
            scrollToVerse(null, true);
        }

        console.log("Content is set ready!");
    }

    setupEventBusListener(Events.SET_TOOLBAR_OFFSET, setToolbarOffset)
    setupEventBusListener(Events.SCROLL_TO_VERSE, scrollToVerse)
    setupEventBusListener(Events.SETUP_CONTENT, setupContent)
    return {scrollToVerse}
}

