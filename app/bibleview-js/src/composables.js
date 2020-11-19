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

import {getCurrentInstance, inject, onMounted, reactive, ref, nextTick} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {getVerseInfo} from "@/utils";
let developmentMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}

function bibleViewFunctions(config) {
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
            else {
                //updateLocation();
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

        //$("#content").css('visibility', 'visible');

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

        //await nextTick(); // set contentReady only after scrolling has been done

        //registerVersePositions(true);

        //contentReady = true;
        console.log("Content is set ready!");
        //jsInterface.setContentReady();
        //startVue();
    }

    return {
        setToolbarOffset,
        scrollToVerse,
        setupContent,
    }
}


export function useConfig() {
    const config = reactive({
        chapterNumbers: true,
        showVerseNumbers: true,
        showStrongs: true,
        showMorphology: true,
        showRedLetters: false,
        showVersePerLine: false,
        showNonCanonical: true,
        makeNonCanonicalItalic: true,
        showSectionTitles: true,
        showStrongsSeparately: false,
        showCrossReferences: true,
        showFootNotes: true,

        font: {
            fontFamily: "sans-serif",
            fontSize: 16,
        },
        showBookmarks: false,
        showMyNotes: false,

        colors: {
            dayBackground: null,
            dayNoise: 0,
            dayTextColor: null,
            nightBackground: null,
            nightNoise: 0,
            nightTextColor: null,
        },

        maxWidth: 170,
        textColor: "black",
        hyphenation: true,
        noiseOpacity: 50,
        lineSpacing: 16,
        justifyText: false,
        marginSize: {
            marginLeft: 0,
            marginRight: 0,
            marginWidth: 170,
        },

        toolbarOffset: 100,
        developmentMode: developmentMode,
    })

    // Expose configuration to java side to be manipulated.
    window.bibleView = {
        ...window.bibleView,
        config,
        setConfig(c) {
            for (const i in c) {
                if (config[i] !== undefined) {
                    config[i] = c[i];
                } else {
                    console.error("Unknown setting", i, c[i]);
                }
            }
        },
        ...bibleViewFunctions(config),
    }
    return {config};
}

export function useAndroid() {
    if(process.env.NODE_ENV === 'development') return {
        setClientReady() {},
        scrolledToVerse() {},
    };

    onMounted(() => {
        android.setClientReady();
    });

    return android;
}

export function useStrings() {
    return {
        chapterNum: "Chapter %d. ",
        verseNum: "%d ",
        noteText: "Notes",
        crossReferenceText: "Crossreferences",
    }
}

export function useBookmarks() {
    const bookmarks = reactive([
            {
                range: [30839, 30842],
                labels: [1, 2]
            },
            {
                range: [30842, 30842],
                labels: [3]
            },
            {
                range: [30842, 30846],
                labels: [3]
            }
    ]);
    return {bookmarks}
}

export function useBookmarkLabels() {
    const inputData = [
        {
            id: 1,
            style: {
                color: [255, 0, 0]
            }
        },
        {
            id: 2,
            style: {
                color: [0, 255, 0],
            }
        },
        {
            id: 3,
            style: {
                color: [0, 0, 255],
            }
        },
    ];
    const data = new Map();
    for(const v of inputData) {
        data.set(v.id, v.style);
    }
    const labels = reactive(data);
    return {labels}
}

export function useCommon() {
    const currentInstance = getCurrentInstance();

    const config = inject("config");
    const strings = inject("strings");
    const verseInfo = inject("verseInfo", getVerseInfo(currentInstance.props.osisID));
    const elementCount = inject("elementCount");
    const contentTag = ref(null);
    const thisCount = ref(-1);

    const unusedAttrs = Object.keys(currentInstance.attrs).filter(v => !v.startsWith("__") && v !== "onClose");
    if(unusedAttrs.length > 0) {
        console.error("Unhandled attributes", currentInstance.attrs);
    }

    onMounted(() => {
        if(!currentInstance.type.noContentTag && contentTag.value === null) {
            console.error(`${currentInstance.type.name}: contentTag does not exist`);
        }

        thisCount.value = elementCount.value;
        elementCount.value ++;
        if(contentTag.value) {
            contentTag.value.dataset.elementCount = thisCount.value.toString();
            contentTag.value.dataset.osisID = verseInfo ? JSON.stringify(verseInfo.osisID) : null;
        }
    });

    function split(string, separator, n) {
        return string.split(separator)[n]
    }

    return {config, strings, contentTag, elementCount, sprintf, split}
}
