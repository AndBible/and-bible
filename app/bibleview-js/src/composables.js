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

import {getCurrentInstance, inject, onMounted, reactive, ref} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {getVerseInfo} from "@/utils";

export function useConfig() {
    const config = reactive({
        chapterNumbers: true,
        verseNumbers: true,
        showStrongs: true,
        showMorph: true,
        showRedLetters: false,
        versePerLine: false,
        showNonCanonical: true,
        makeNonCanonicalItalic: true,
        showTitles: true,
        showStrongsSeparately: false,
        showCrossReferences: true,
        showFootnotes: true,

        maxWidth: 170,
        textColor: "black",
        hyphenation: true,
        noiseOpacity: 50,
        lineSpacing: 16,
        justifyText: false,
        marginLeft: 5,
        marginRight: null,

        osisFragments: []
    })
    // Expose configuration to java side to be manipulated.
    window.bibleView.config = config;
    return config;
}

export function useAndroid() {
    return {
        // eslint-disable-next-line no-undef
        ...window.jsInterface
    }
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
        console.error("Unhandled attributes", currentInstance.type.name, unusedAttrs);
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
