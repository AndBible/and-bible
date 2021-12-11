/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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


import {nextTick, reactive} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";
import {DocumentTypes} from "@/constants";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {isEqual} from "lodash";
import {Deferred} from "@/utils";

export const strongsModes = {
    off: 0,
    inline: 1,
    links: 2,
}

export let errorBox = false;
const white = -1;
const black = -16777216;

let developmentMode = false;
export let testMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}
if(process.env.NODE_ENV === "test") {
    testMode = true;
}


export function useConfig(documentType) {
    // text display settings only here. TODO: rename
    const config = reactive({
        developmentMode,
        testMode,

        showAnnotations: true,
        showChapterNumbers: true,
        showVerseNumbers: true,
        strongsMode: strongsModes.off,
        showMorphology: false,
        showRedLetters: false,
        showVersePerLine: false,
        showNonCanonical: true,
        makeNonCanonicalItalic: true,
        showSectionTitles: true,
        showStrongsSeparately: false,
        showFootNotes: true,
        fontFamily: "sans-serif",
        fontSize: 16,

        disableBookmarking: false,

        showBookmarks: true,
        showMyNotes: true,
        bookmarksHideLabels: [],
        bookmarksAssignLabels: [],

        colors: {
            dayBackground: white,
            dayNoise: 0,
            dayTextColor: black,
            nightBackground: black,
            nightNoise: 0,
            nightTextColor: white,
        },

        hyphenation: true,
        lineSpacing: 10,
        justifyText: false,
        marginSize: {
            marginLeft: 0,
            marginRight: 0,
            maxWidth: 300,
        },
        topMargin: 0,
    });
    const rtl = new URLSearchParams(window.location.search).get("rtl") === "true";
    const nightMode = new URLSearchParams(window.location.search).get("night") === "true";
    const appSettings = reactive({
        topOffset: 0,
        bottomOffset: 100,
        nightMode: nightMode,
        errorBox: false,
        favouriteLabels: [],
        recentLabels: [],
        frequentLabels: [],
        hideCompareDocuments: [],
        rightToLeft: rtl,
        activeWindow: false,
        actionMode: false,
        hasActiveIndicator: false,
        activeSince: 0,
        limitAmbiguousModalSize: false,
    });

    function calcMmInPx() {
        const el = document.createElement('div');
        el.style = "width: 1mm;"
        document.body.appendChild(el);
        const pixels = el.offsetWidth;
        document.body.removeChild(el);
        return pixels
    }
    const mmInPx = calcMmInPx();

    const isBible = computed(() => documentType.value === DocumentTypes.BIBLE_DOCUMENT);

    const calculatedConfig = computed(() => {
        let topOffset = appSettings.topOffset;
        let topMargin = 0;
        if(isBible.value) {
            topMargin = config.topMargin * mmInPx;
            topOffset += topMargin;
        }
        return {topOffset, topMargin};
    });

    window.bibleViewDebug.config = config;
    window.bibleViewDebug.appSettings = appSettings;

    setupEventBusListener(Events.SET_ACTION_MODE, value => {
        appSettings.actionMode = value;
    });

    setupEventBusListener(Events.SET_ACTIVE, ({hasActiveIndicator, isActive}) => {
        appSettings.activeWindow = isActive;
        appSettings.hasActiveIndicator = hasActiveIndicator;
        if(isActive) {
            appSettings.activeSince = performance.now();
        }
    });

    function compareConfig(newConfig, checkedKeys) {
        for(const key of checkedKeys) {
            if(newConfig[key] === undefined) continue;
            if(!isEqual(config[key], newConfig[key])) return true;
        }
        return false;
    }

    function getNeedBookmarkRefresh(newConfig) {
        // Anything that changes DOM in a significant way needs bookmark refresh
        const keys = [
            "showAnnotations", "showChapterNumbers", "showVerseNumbers", "strongsMode", "showMorphology",
            "showRedLetters", "showVersePerLine", "showNonCanonical", "makeNonCanonicalItalic", "showSectionTitles",
            "showStrongsSeparately", "showFootNotes", "showBookmarks", "showMyNotes", "bookmarksHideLabels"
        ];
        return compareConfig(newConfig, keys);
    }

    function getNeedRefreshLocation(newConfig) {
        // Anything that changes location of text in a significant way, needs location refresh
        const keys = [
            "showAnnotations", "showChapterNumbers", "showVerseNumbers", "strongsMode", "showMorphology",
            "showRedLetters", "showVersePerLine", "showNonCanonical", "showSectionTitles",
            "showStrongsSeparately", "showFootNotes", "showBookmarks", "showMyNotes",
            "fontSize", "fontFamily", "hyphenation", "justifyText", "marginSize", "topMargin"
        ];
        return compareConfig(newConfig, keys);
    }

    setupEventBusListener(Events.SET_CONFIG, async function setConfig({config: newConfig, appSettings: newAppSettings, initial = false} = {}) {
        const defer = new Deferred();
        const isBible = documentType.value === DocumentTypes.BIBLE_DOCUMENT
        const needsRefreshLocation = !initial && (isBible || documentType.value === DocumentTypes.OSIS_DOCUMENT) && getNeedRefreshLocation(newConfig);
        const needBookmarkRefresh = getNeedBookmarkRefresh(newConfig);

        if (needsRefreshLocation) emit(Events.CONFIG_CHANGED, defer)

        if(isBible && needBookmarkRefresh) {
            config.disableBookmarking = true;
            await nextTick();
        }
        for (const i in newConfig) {
            if (config[i] !== undefined) {
                config[i] = newConfig[i];
            } else if(!i.startsWith("deprecated")) {
                console.error("Unknown setting", i, newConfig[i]);
            }
        }
        // eslint-disable-next-line require-atomic-updates
        config.showChapterNumbers = config.showVerseNumbers;

        for (const i in newAppSettings) {
            if (appSettings[i] !== undefined) {
                appSettings[i] = newAppSettings[i];
            } else if(!i.startsWith("deprecated")) {
                console.error("Unknown setting", i, appSettings[i]);
            }
        }

        errorBox = appSettings.errorBox;
        if(isBible && needBookmarkRefresh) {
            // eslint-disable-next-line require-atomic-updates
            config.disableBookmarking = false
        }

        if (needsRefreshLocation) {
            await nextTick();
            defer.resolve()
        }
    })

    return {config, appSettings, calculatedConfig};
}
