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

import {getCurrentInstance, inject, nextTick, onMounted, reactive, ref, watch} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {Deferred, setupWindowEventListener} from "@/utils";
import {computed} from "@vue/reactivity";
import {throttle} from "lodash";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {library} from "@fortawesome/fontawesome-svg-core";
import {faEdit} from "@fortawesome/free-solid-svg-icons";

let developmentMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}

export function useVerseNotifier(config, {scrolledToVerse}, topElement) {
    const currentVerse = ref(null);
    watch(() => currentVerse.value,  value => scrolledToVerse(value));

    const lineHeight = computed(() =>
        parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height'))
    );

    const onScroll = throttle(() => {
        const y = config.toolbarOffset + lineHeight.value*0.8;

        // Find element, starting from right
        const step = 10;
        let element;
        for(let x = window.innerWidth - step; x > 0; x-=step) {
            element = document.elementFromPoint(x, y)
            if(element) {
                element = element.closest(".verse");
                if(element) {
                    currentVerse.value = parseInt(element.id.slice(2))
                    break;
                }
            }
        }
    }, 50);

    setupWindowEventListener('scroll', onScroll)
    return {currentVerse}
}

export function useConfig() {
    const config = reactive({
        chapterNumbers: true,
        showVerseNumbers: true,
        strongsMode: 0,
        showMorphology: false,
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
        showBookmarks: true,
        showMyNotes: false,

        colors: {
            dayBackground: null,
            dayNoise: 0,
            dayTextColor: null,
            nightBackground: null,
            nightNoise: 0,
            nightTextColor: null,
        },
        bookmarks: {
            showAll: true,
            showLabels: []
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
        infiniteScroll: true,

        developmentMode: developmentMode,
    })

    if(developmentMode) {
        window.bibleViewDebug.config = config;
    }

    setupEventBusListener(Events.SET_CONFIG, async ({config: c, initial}) => {
        const defer = new Deferred();
        if(!initial) emit(Events.CONFIG_CHANGED, defer)
        const oldValue = config.showBookmarks;
        config.showBookmarks = false
        await nextTick();
        for (const i in c) {
            if (config[i] !== undefined) {
                config[i] = c[i];
            } else {
                console.error("Unknown setting", i, c[i]);
            }
        }
        if(c.showBookmarks === undefined) {
            // eslint-disable-next-line require-atomic-updates
            config.showBookmarks = oldValue;
        }
        if(!initial) {
            await nextTick();
        }
        defer.resolve()
    })

    return {config};
}

export function useStrings() {
    return {
        chapterNum: "Chapter %d. ",
        verseNum: "%d ",
        noteText: "Notes",
        crossReferenceText: "Crossreferences",
        findAllOccurrences: "Find all occurrences",
        reportError: "Report an error",
        bookmarkNote: "Note on bookmark",
        editNote: "Edit",
        editNotePlaceholder: "Edit a note for bookmark",
        removeBookmark: "Remove bookmark",
        assignLabels: "Assign labels",
        bookmarkAccurate: "Bookmark was create in %s",
        ok: "Ok",
        cancel: "Cancel",
        removeBookmarkConfirmation: "Are you sure you want to remove bookmark?",
    }
}

export function useCommon() {
    const currentInstance = getCurrentInstance();

    const config = inject("config");
    const strings = inject("strings");

    const unusedAttrs = Object.keys(currentInstance.attrs).filter(v => !v.startsWith("__") && v !== "onClose");
    if(unusedAttrs.length > 0) {
        console.error("Unhandled attributes", currentInstance.type.name, currentInstance.attrs);
    }

    function split(string, separator, n) {
        return string.split(separator)[n]
    }

    return {config, strings, sprintf, split}
}

export function usePoetic(fragmentReady, fragElement) {
    /* TODO: implement.
        Need to find elements within fragElement with poetic class in them.
        Then make ranges of matching sID & eID.
        Then use dom-highlight-range to alter text-indent.
     */
}

export function useFontAwesome() {
    library.add(faEdit)
}
