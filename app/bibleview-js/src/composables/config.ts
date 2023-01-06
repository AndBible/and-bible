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


import {computed, nextTick, reactive, Ref} from "vue";
import {emit, setupEventBusListener} from "@/eventbus";
import {isEqual} from "lodash";
import {Deferred} from "@/utils";
import {BibleViewDocumentType} from "@/types/documents";

export type StrongsMode = 0 | 1 | 2
export const strongsModes: Record<string, StrongsMode> = {off: 0, inline: 1, links: 2}

export let errorBox = false;
const white = -1;
const black = -16777216;

let developmentMode: boolean = false;
export let testMode: boolean = false;

if (process.env.NODE_ENV === "development") {
    developmentMode = true;
}
if (process.env.NODE_ENV === "test") {
    testMode = true;
}

export type Config = {
    developmentMode: boolean,
    testMode: boolean,

    showAnnotations: boolean,
    showChapterNumbers: boolean,
    showVerseNumbers: boolean,
    strongsMode: StrongsMode,
    showMorphology: boolean,
    showRedLetters: boolean,
    showVersePerLine: boolean,
    showNonCanonical: boolean,
    makeNonCanonicalItalic: boolean,
    showSectionTitles: boolean,
    showStrongsSeparately: boolean,
    showFootNotes: boolean,
    fontFamily: string,
    fontSize: number,

    disableBookmarking: boolean,

    showBookmarks: boolean,
    showMyNotes: boolean,
    bookmarksHideLabels: number[],
    bookmarksAssignLabels: number[],

    colors: {
        dayBackground: number,
        dayNoise: number,
        dayTextColor: number,
        nightBackground: number,
        nightNoise: number,
        nightTextColor: number,
    },

    hyphenation: boolean,
    lineSpacing: number,
    justifyText: boolean,
    marginSize: {
        marginLeft: number,
        marginRight: number,
        maxWidth: number,
    },
    topMargin: number,
}

export type AppSettings = {
    topOffset: number,
    bottomOffset: number,
    nightMode: boolean,
    errorBox: boolean,
    favouriteLabels: number[],
    recentLabels: number[],
    frequentLabels: number[],
    hideCompareDocuments: string[],
    rightToLeft: boolean,
    activeWindow: boolean,
    actionMode: boolean,
    hasActiveIndicator: boolean,
    activeSince: number,
    limitAmbiguousModalSize: boolean,
}

export type CalculatedConfig = Ref<{
    topOffset: number
    topMargin: number
}>

export function useConfig(documentType: Ref<BibleViewDocumentType>) {
    // text display settings only here. TODO: rename
    const config: Config = reactive({
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
    const appSettings: AppSettings = reactive({
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
        const el = document.createElement('div') as HTMLElement;
        el.style.width = "1mm"
        document.body.appendChild(el);
        const pixels = el.offsetWidth;
        document.body.removeChild(el);
        return pixels
    }

    const mmInPx = calcMmInPx();

    const isBible = computed(() => documentType.value === "bible");

    const calculatedConfig = computed(() => {
        let topOffset = appSettings.topOffset;
        let topMargin = 0;
        if (isBible.value) {
            topMargin = config.topMargin * mmInPx;
            topOffset += topMargin;
        }
        return {topOffset, topMargin};
    });

    window.bibleViewDebug.config = config;
    window.bibleViewDebug.appSettings = appSettings;

    setupEventBusListener("set_action_mode", (value: boolean) => {
        appSettings.actionMode = value;
    });

    setupEventBusListener("set_active",
        ({hasActiveIndicator, isActive}: { hasActiveIndicator: boolean, isActive: boolean }) => {
            appSettings.activeWindow = isActive;
            appSettings.hasActiveIndicator = hasActiveIndicator;
            if (isActive) {
                appSettings.activeSince = performance.now();
            }
        }
    );

    function compareConfig(newConfig: Config, checkedKeys: (keyof Config)[]) {
        for (const key of checkedKeys) {
            if (newConfig[key] === undefined) continue;
            if (!isEqual(config[key], newConfig[key])) return true;
        }
        return false;
    }

    function getNeedBookmarkRefresh(newConfig: Config) {
        // Anything that changes DOM in a significant way needs bookmark refresh
        const keys: (keyof Config)[] = [
            "showAnnotations", "showChapterNumbers", "showVerseNumbers", "strongsMode", "showMorphology",
            "showRedLetters", "showVersePerLine", "showNonCanonical", "makeNonCanonicalItalic", "showSectionTitles",
            "showStrongsSeparately", "showFootNotes", "showBookmarks", "showMyNotes", "bookmarksHideLabels"
        ];
        return compareConfig(newConfig, keys);
    }

    function getNeedRefreshLocation(newConfig: Config) {
        // Anything that changes location of text in a significant way, needs location refresh
        const keys: (keyof Config)[] = [
            "showAnnotations", "showChapterNumbers", "showVerseNumbers", "strongsMode", "showMorphology",
            "showRedLetters", "showVersePerLine", "showNonCanonical", "showSectionTitles",
            "showStrongsSeparately", "showFootNotes", "showBookmarks", "showMyNotes",
            "fontSize", "fontFamily", "hyphenation", "justifyText", "marginSize", "topMargin"
        ];
        return compareConfig(newConfig, keys);
    }

    setupEventBusListener("set_config",
        async function setConfig(
            {
                config: newConfig,
                appSettings: newAppSettings,
                initial = false
            }: {
                config: Config,
                appSettings: AppSettings,
                initial: boolean
            }
        ) {
            const defer = new Deferred();
            const isBible = documentType.value === "bible"
            const needsRefreshLocation = !initial && (isBible || documentType.value === "osis") && getNeedRefreshLocation(newConfig);
            const needBookmarkRefresh = getNeedBookmarkRefresh(newConfig);

            if (needsRefreshLocation) emit("config_changed", defer)

            if (isBible && needBookmarkRefresh) {
                config.disableBookmarking = true;
                await nextTick();
            }

            for (const i in newConfig) {
                // @ts-ignore
                if (config[i] !== undefined) {
                    // @ts-ignore
                    config[i] = newConfig[i];
                } else if (!i.startsWith("deprecated")) {
                    // @ts-ignore
                    console.error("Unknown setting", i, newConfig[i]);
                }
            }
            config.showChapterNumbers = config.showVerseNumbers;
            for (const j in newAppSettings) {
                // @ts-ignore
                if (appSettings[j] !== undefined) {
                    // @ts-ignore
                    appSettings[j] = newAppSettings[j];
                } else if (!j.startsWith("deprecated")) {
                    // @ts-ignore
                    console.error("Unknown setting", j, appSettings[j]);
                }
            }

            errorBox = appSettings.errorBox;
            if (isBible && needBookmarkRefresh) {
                config.disableBookmarking = false
            }

            if (needsRefreshLocation) {
                await nextTick();
                defer.resolve()
            }
        })

    return {config, appSettings, calculatedConfig};
}
