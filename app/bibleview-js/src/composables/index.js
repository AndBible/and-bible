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

import {
    getCurrentInstance,
    inject,
    nextTick,
    onBeforeMount,
    onUnmounted, provide,
    reactive,
    ref,
    watch
} from "@vue/runtime-core";
import {sprintf as sprintfOrig} from "sprintf-js";
import {adjustedColor, Deferred, setupWindowEventListener} from "@/utils";
import {computed} from "@vue/reactivity";
import {isEqual, throttle} from "lodash";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {library} from "@fortawesome/fontawesome-svg-core";
import {bcv_parser as BcvParser} from "bible-passage-reference-parser/js/en_bcv_parser.min";
import {
    faArrowsAltV,
    faBookmark, faCheck, faChevronCircleDown, faCompressArrowsAlt,
    faEdit, faEllipsisH, faExpandArrowsAlt, faEye, faEyeSlash,
    faFileAlt, faFireAlt, faHandPointer,
    faHeadphones, faHeart, faHistory,
    faIndent, faInfoCircle, faOutdent, faPenSquare, faPlus,
    faPlusCircle, faQuestionCircle, faSave, faShareAlt, faSort,
    faTags, faTextWidth, faTimes, faTrash,
} from "@fortawesome/free-solid-svg-icons";

import {DocumentTypes} from "@/constants";

let developmentMode = false;
export let testMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}
if(process.env.NODE_ENV === "test") {
    testMode = true;
}

export function useVerseNotifier(config, calculatedConfig, mounted, {scrolledToOrdinal}, topElement, {isScrolling}) {
    const currentVerse = ref(null);
    watch(() => currentVerse.value,  value => scrolledToOrdinal(value));

    const lineHeight = computed(() => {
        config; // Update also when font settings etc are changed
        if(!mounted.value || !topElement.value) return 1;
        return parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height'));
        }
    );

    let lastDirection = "ltr";
    const step = 10;

    function *iterate(direction = "ltr") {
        if(direction === "ltr") {
            for (let x = window.innerWidth - step; x > 0; x -= step) {
                yield x;
            }
        } else {
            for (let x = step; x < window.innerWidth; x += step) {
                yield x;
            }
        }
    }

    const onScroll = throttle(() => {
        if(isScrolling.value) return;
        const y = calculatedConfig.value.topOffset + lineHeight.value*0.8;

        // Find element, starting from right
        let element;
        let directionChanged = true;
        while(directionChanged) {
            directionChanged = false;
            for(const x of iterate(lastDirection)) {
                element = document.elementFromPoint(x, y)
                if (element) {
                    element = element.closest(".ordinal");
                    if (element) {
                        const direction = window.getComputedStyle(element).getPropertyValue("direction");
                        if(direction !== lastDirection) {
                            directionChanged = true;
                            lastDirection = direction;
                            break;
                        }
                        currentVerse.value = parseInt(element.dataset.ordinal)
                        break;
                    }
                }
            }
        }
    }, 50);

    setupWindowEventListener('scroll', onScroll)
    return {currentVerse}
}

export const strongsModes = {
    off: 0,
    inline: 1,
    links: 2,
}

export let errorBox = false;
const white = -1;
const black = -16777216;

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

export function abbreviated(str, n, useWordBoundary = true) {
    if(!str) return ""
    if (str.length <= n) { return str; }
    let subString = str.substr(0, n-1); // the original check
    let splitPoint = subString.lastIndexOf(" ");
    if(splitPoint <= 0) {
        splitPoint = n-1;
    }
    return (useWordBoundary
        ? subString.substr(0, splitPoint)
        : subString) + "...";
}

export function sprintf(...args) {
    return sprintfOrig(...args);
}

export function useCommon() {
    const currentInstance = getCurrentInstance();

    const config = inject("config");
    const appSettings = inject("appSettings");
    const calculatedConfig = inject("calculatedConfig");
    const android = inject("android");

    const strings = inject("strings");

    const unusedAttrs = Object.keys(currentInstance.attrs).filter(v => !v.startsWith("__") && v !== "onClose");
    if(unusedAttrs.length > 0) {
        console.error("Unhandled attributes", currentInstance.type.name, currentInstance.attrs);
    }

    function split(string, separator, n) {
        return string.split(separator)[n]
    }

    function formatTimestamp(timestamp) {
        let options = { year:'numeric', month:'numeric', day: 'numeric', hour:'numeric', minute:'2-digit'};
        return new Date(timestamp).toLocaleString([],options)
    }

    return {config, appSettings, calculatedConfig, strings, sprintf, split,
        adjustedColor, formatTimestamp, abbreviated, emit, Events, android,
    }
}

export function useFontAwesome() {
    const customWholeVerseFalse = {
        prefix: 'fas',
        iconName: 'custom-whole-verse-false',
        icon: [100, 100, [], null, "m19.6 33.7a4.3 4.3 0 0 0-4.3 4.3 4.3 4.3 0 0 0 4.3 4.3H63.2A4.3 4.3 0 0 0 67.5 38 4.3 4.3 0 0 0 63.2 33.7Zm-0.2-16a4.3 4.3 0 0 0-4.2 4.2 4.3 4.3 0 0 0 4.2 4.2h60.9a4.3 4.3 0 0 0 4.2-4.2 4.3 4.3 0 0 0-4.2-4.2zM0 0V100H100V0ZM8 8H92.2V91.9H8ZM72.7 52.8 66.3 57.5 71 65.9H29.2l4.6-8.4-6.5-4.7-10.6 17 11 17 6.3-4.9-4.6-8.1h41.5l-4.6 8.1 6.6 4.8 10.6-16.9z"]
    };
    const customWholeVerseTrue = {
        prefix: 'fas',
        iconName: 'custom-whole-verse-true',
        icon: [100, 100, [], null, "M0 0V100H100V0ZM19.4 17.7h60.9a4.3 4.3 0 0 1 4.2 4.2 4.3 4.3 0 0 1-4.2 4.2H19.4a4.3 4.3 0 0 1-4.2-4.2 4.3 4.3 0 0 1 4.2-4.2zm0.2 16h43.6a4.3 4.3 0 0 1 4.3 4.3 4.3 4.3 0 0 1-4.3 4.3H19.6A4.3 4.3 0 0 1 15.3 38 4.3 4.3 0 0 1 19.6 33.7Zm7.7 19.1 6.5 4.7-4.6 8.4H71l-4.7-8.4 6.4-4.7 10.8 17-10.6 16.9-6.6-4.8 4.6-8.1H29.4l4.6 8.1-6.3 4.9-11-17z"]
    };

    const customStrongs = {
        prefix: 'fas',
        iconName: 'custom-morph',
        icon: [100, 100, [], null, "M91 82.4H88.1V67.6c0-1.6-1.4-3-2.9-3H53v-7.4c2.5-1.3 8.8-4.5 14.6-4.5 7.8 0 15.8 5.3 16 5.3 0.8 0.8 1.9 0.8 2.9 0.2 1-0.4 1.6-1.4 1.6-2.5V8.9C88.1 7.9 87.5 7 86.7 6.4 86.3 6.2 77.2 0.1 67.6 0.1 60.4 0.1 53.2 3.7 50.1 5.4 46.9 3.7 39.7 0.1 32.5 0.1 23 0.1 13.7 6.2 13.3 6.4 12.5 7 12 7.9 12 8.9V55.7c0 1.1 0.6 2.1 1.5 2.5 1 0.6 2.1 0.6 3.1-0.2 0.1 0 8.1-5.3 15.9-5.3 5.9 0 12.1 3.2 14.6 4.5v7.4H14.9c-1.6 0-2.9 1.4-2.9 3V82.4H9.1c-1.6 0-2.9 1.4-2.9 2.9V97c0 1.6 1.3 2.9 2.9 2.9H20.8c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9H17.9V70.5h29.2v11.9h-2.9c-1.5 0-2.9 1.4-2.9 2.9V97c0 1.6 1.4 2.9 2.9 2.9h11.7c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9H53V70.5h29.2v11.9h-2.9c-1.6 0-2.9 1.4-2.9 2.9V97c0 1.6 1.3 2.9 2.9 2.9H91c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9zM82.2 10.5v40.3c-3.7-2-9.1-3.9-14.6-3.9-5.4 0-10.9 1.9-14.6 3.9V10.5c2.5-1.5 8.8-4.5 14.6-4.5 5.9 0 12.1 3.1 14.6 4.6zM32.5 46.9c-5.4 0-10.9 1.9-14.6 3.9V10.5c2.5-1.5 8.8-4.5 14.6-4.5 5.9 0 12.1 3.1 14.6 4.6V50.8C43.4 48.8 38 46.9 32.5 46.9ZM17.9 94.1H12v-5.9h5.9zm35.1 0H47.1V88.2H53Zm35.1 0h-5.9v-5.9h5.9zM26.7 18.2c0.2 0 0.4 0 0.5-0.1 1.8-0.3 3.6-0.4 5.3-0.4 1.8 0 3.5 0.1 5.3 0.4 1.7 0.3 3.1-0.8 3.5-2.4 0.2-1.5-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.8-2.4 3.3 0.2 1.5 1.6 2.5 3 2.5zm35.1 0c0.2 0 0.4 0 0.5-0.1 1.8-0.3 3.6-0.4 5.3-0.4 1.8 0 3.5 0.1 5.3 0.4 1.7 0.3 3.1-0.8 3.5-2.4 0.2-1.5-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.8-2.4 3.3 0.2 1.5 1.6 2.5 3 2.5zm-22.9 5.9c-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5 0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3 0.2-1.6-0.8-3.1-2.4-3.3zm22.9 5.8c0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3 0.2-1.6-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5zM74 35.8c-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5 0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3C76.6 37.5 75.6 36 74 35.8Zm-35.1 0c-2.3-0.4-4.4-0.6-6.4-0.6-2.1 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.4 1.6 1.8 2.7 3.5 2.3 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 0.2 0.2 0.4 0.2 0.6 0.2 1.3 0 2.7-0.9 2.9-2.5 0.2-1.6-0.8-3.1-2.4-3.3z"]
    };

    const customCompare = {
        prefix: 'fas',
        iconName: 'custom-compare',
        icon: [14, 10, [], null, "m 8.5098921,7.1756885 c -1.462855,-1.252626 -2.9612036,-2.591747 -4.2862386,-3.632718 -0.0329,0.811234 -0.07,1.4481 -0.07,2.292376 -1.38699,0.0086 -2.69238,0.01232 -4.10399001,0.02463 9.3e-4,0.925599 -0.004,1.780274 -0.009,2.7237908 1.34541001,-0.0077 2.70264001,-0.03906 4.08349001,-0.05859 0.0279,0.829355 0.009,1.7061237 0.0181,2.3690807 1.63103,-1.3167557 2.8096476,-2.3030007 4.3674666,-3.7185685 z M 6.0973105,3.6666205 c 1.4628546,-1.252626 2.9612036,-2.591747 4.2862425,-3.63271804 0.0329,0.81123404 0.06999,1.44810004 0.06999,2.29237604 1.386984,0.0086 2.692374,0.01232 4.103984,0.02463 -9.2e-4,0.925599 0.004,1.780274 0.009,2.723791 -1.34541,-0.0077 -2.70264,-0.03906 -4.083484,-0.05859 -0.02785,0.829355 -0.0089,1.706124 -0.01813,2.369081 C 8.8338891,6.0684345 7.6552711,5.0821895 6.0974525,3.6666215 Z"]
    }

    library.add(customCompare)
    library.add(customStrongs)
    library.add(customWholeVerseFalse)
    library.add(customWholeVerseTrue)
    library.add(faPenSquare);
    library.add(faCompressArrowsAlt)
    library.add(faExpandArrowsAlt)
    library.add(faArrowsAltV)
    library.add(faTextWidth)
    library.add(faHeadphones)
    library.add(faEdit)
    library.add(faTags)
    library.add(faBookmark)
    library.add(faPlusCircle)
    library.add(faTrash)
    library.add(faFileAlt)
    library.add(faInfoCircle)
    library.add(faTimes)
    library.add(faPlus)
    library.add(faEllipsisH)
    library.add(faChevronCircleDown)
    library.add(faSort)
    library.add(faIndent)
    library.add(faOutdent)
    library.add(faHeart)
    library.add(faHistory)
    library.add(faFireAlt)
    library.add(faEyeSlash);
    library.add(faEye);
    library.add(faShareAlt);
    library.add(faQuestionCircle)
    library.add(faHandPointer)
    library.add(faSave)
    library.add(faCheck)
}

export function checkUnsupportedProps(props, attributeName, values = []) {
    const value = props[attributeName];
    const appSettings = inject("appSettings", {});
    if(!appSettings.errorBox) return;
    if(value && !values.includes(value)) {
        const tagName = getCurrentInstance().type.name
        const origin = inject("verseInfo", {}).osisID;
        console.warn(`${tagName}: Unsupported (ignored) attribute "${attributeName}" value "${value}", origin: ${origin}`)
    }
}

export function useJournal(label) {
    const journalTextEntries = reactive(new Map());
    const bookmarkToLabels = reactive(new Map());

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARKS, bookmarks => {
        for(const b of bookmarks) {
            if(b.bookmarkToLabels) {
                updateBookmarkToLabels(...b.bookmarkToLabels);
            }
        }
    });

    function updateJournalTextEntries(...entries) {
        for(const e of entries)
            if(e.labelId === label.id)
                journalTextEntries.set(e.id, e);
    }

    function updateBookmarkToLabels(...entries) {
        for(const e of entries)
            if(e.labelId === label.id)
                bookmarkToLabels.set(e.bookmarkId, e);
    }

    function updateJournalOrdering(...entries) {
        for(const e of entries) {
            journalTextEntries.get(e.id).orderNumber = e.orderNumber;
        }
    }
    function deleteJournal(journalId) {
        journalTextEntries.delete(journalId)
    }
    return {
        journalTextEntries,
        updateJournalTextEntries,
        updateJournalOrdering,
        updateBookmarkToLabels,
        bookmarkToLabels,
        deleteJournal
    };
}

export function useReferenceCollector() {
    const references = reactive([]);
    function collect(linkRef) {
        references.push(linkRef);
    }
    function clear() {
        references.splice(0);
    }
    return {references, collect, clear}
}

export function useVerseHighlight() {
    const highlightedVerses = reactive(new Set());
    const undoCustomHighlights = reactive([]);

    const hasHighlights = computed(() => highlightedVerses.size > 0 || undoCustomHighlights.length > 0);

    function resetHighlights(onlyVerses = false) {
        highlightedVerses.clear();
        if(!onlyVerses) {
            undoCustomHighlights.forEach(f => f())
            undoCustomHighlights.splice(0);
        }
    }

    function highlightVerse(ordinal) {
        highlightedVerses.add(ordinal);
    }

    function addCustom(f) {
        undoCustomHighlights.push(f);
    }

    return {highlightVerse, addCustom, highlightedVerses, resetHighlights, hasHighlights}
}


function useParsers(android) {
    const enParser = new BcvParser;
    const parsers = [enParser];

    let languages = null;

    function getLanguages() {
        if (!languages) {
            languages = android.getActiveLanguages()
        }
        return languages
    }

    async function loadParser(lang) {
        console.log(`Loading parser for ${lang}`)
        const url = `/features/RefParser/${lang}_bcv_parser.js`
        const content = await (await fetch(url)).text();
        const module = {}
        Function(content).call(module)
        return new module["bcv_parser"];
    }

    async function initialize() {
        //Get the active languages and create a bible reference parser for each language
        const languages = getLanguages()
        console.log(`Enabling parsers for ${languages.join(",")}`)
        await Promise.all(languages.filter(l => l !== "en").map(async (lang) => {
            try {
                parsers.push(await loadParser(lang))
            } catch (error) {
                console.log(`Could not load parser for language: ${lang} due to ${error}`)
            }
        }))
    }

    function parse(text) {
        let parsed = ""
        //Try each of the parsers until one succeeds
        parsers.some(parser => {
            parsed = parser.parse(text).osis();
            if (parsed !== "") return true
        })
        return parsed;
    }

    return {initialize, parsers, parse}
}

export function useCustomFeatures(android) {
    const features = reactive(new Set())

    const defer = new Deferred();
    const featuresLoaded = ref(false);
    const featuresLoadedPromise = ref(defer.wait());
    const {parse, initialize} = useParsers(android);

    // eslint-disable-next-line no-unused-vars
    async function reloadFeatures(featureModuleNames) {
        features.clear();
        if(featureModuleNames.includes("RefParser")) {
            await initialize();
            features.add("RefParser");
        }
    }

    setupEventBusListener(Events.RELOAD_ADDONS, ({featureModuleNames}) => {
        reloadFeatures(featureModuleNames)
    })

    onBeforeMount(() => {
        const featureModuleNames = new URLSearchParams(window.location.search).get("featureModuleNames");
        if (!featureModuleNames) return
        reloadFeatures(featureModuleNames.split(","))
            .then(() => {
                defer.resolve()
                featuresLoaded.value = true;
                console.log("Features loading finished");
            });
    })

    return {features, featuresLoadedPromise, featuresLoaded, parse};
}

export function useCustomCss() {
    const cssNodes = new Map();
    const count = new Map();
    const customCssPromises = [];
    function addCss(bookInitials) {
        console.log(`Adding style for ${bookInitials}`);
        const c = count.get(bookInitials) || 0;
        if (!c) {
            const link = document.createElement("link");
            const onLoadDefer = new Deferred();
            const promise = onLoadDefer.wait();
            customCssPromises.push(promise);
            link.href = `/module-style/${bookInitials}/style.css`;
            link.type = "text/css";
            link.rel = "stylesheet";
            const cssReady = () => {
                onLoadDefer.resolve();
                customCssPromises.splice(customCssPromises.findIndex(v => v === promise), 1);
            }
            link.onload = cssReady;
            link.onerror = cssReady;
            cssNodes.set(bookInitials, link);
            document.getElementsByTagName("head")[0].appendChild(link);
        }
        count.set(bookInitials, c + 1);
    }

    function removeCss(bookInitials) {
        console.log(`Removing style for ${bookInitials}`)
        const c = count.get(bookInitials) || 0;
        if(c > 1) {
            count.set(bookInitials, c-1);
        } else if(c === 1){
            count.delete(bookInitials);
            cssNodes.get(bookInitials).remove();
            cssNodes.delete(bookInitials);
        }
    }

    function registerBook(bookInitials) {
        addCss(bookInitials);
        onUnmounted(() => {
            removeCss(bookInitials);
        });
    }

    const customStyles = reactive([]);

    function reloadStyles(styleModuleNames) {
        customStyles.forEach(moduleName => {
            removeCss(moduleName);
        });
        customStyles.splice(0);

        styleModuleNames.forEach(moduleName => {
            customStyles.push(moduleName);
            addCss(moduleName);
        });
    }

    const styleModuleNames = new URLSearchParams(window.location.search).get("styleModuleNames");
    if (styleModuleNames) {
        reloadStyles(styleModuleNames.split(","))
    }

    setupEventBusListener(Events.RELOAD_ADDONS, ({styleModuleNames}) => {
        reloadStyles(styleModuleNames)
    })

    return {registerBook, customCssPromises}
}

export function useAddonFonts() {
    const elements = [];

    setupEventBusListener(Events.RELOAD_ADDONS, ({fontModuleNames}) => {
        reloadFonts(fontModuleNames)
    })

    function reloadFonts(fontModuleNames) {
        for(const e of elements) {
            e.remove();
        }
        elements.splice(0);
        for (const modName of fontModuleNames) {
            const link = document.createElement("link");
            link.href = `/fonts/${modName}/fonts.css`;
            link.type = "text/css";
            link.rel = "stylesheet";
            document.getElementsByTagName("head")[0].appendChild(link)
            elements.push(link);
        }
    }

    onBeforeMount(() => {
        const fontModuleNames = new URLSearchParams(window.location.search).get("fontModuleNames");
        if (!fontModuleNames) return
        reloadFonts(fontModuleNames.split(","));
    })
}

export function useModal(android) {
    const modalOptArray = reactive([]);
    const modalOpen = computed(() => modalOptArray.length > 0);

    function register(opts) {
        if(!opts.blocking) {
            closeModals();
        }

        modalOptArray.push(opts);

        onUnmounted(() => {
            const idx = modalOptArray.indexOf(opts);
            modalOptArray.splice(idx, 1);
        });
    }

    function closeModals() {
        for(const {close} of modalOptArray.filter(o => !o.blocking))
            close();
    }

    setupEventBusListener(Events.CLOSE_MODALS, closeModals)

    watch(modalOpen, v => android.reportModalState(v), {flush: "sync"})

    return {register, closeModals, modalOpen}
}

export function useSharing({topElement, android}) {
    const exportMode = ref(false);
    provide("exportMode", exportMode);

    let exportCss;
    async function shareDocument() {
        if (!exportCss) {
            exportCss = (await import("!raw-loader!sass-loader!@/export.scss")).default;
        }
        exportMode.value = true;
        await nextTick();
        const html = `
         <!DOCTYPE html>
         <html>
           <head>
             <meta charset="utf-8">
             <style>${exportCss}</style>
           </head>
           <body>${topElement.value.innerHTML}</body>
         </html>`;
        exportMode.value = false;
        android.shareHtml(html);
    }
    setupEventBusListener(Events.EXPORT_HTML, shareDocument)
}
