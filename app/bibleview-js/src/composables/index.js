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
    onUnmounted,
    reactive,
    ref,
    watch
} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {adjustedColor, Deferred, setupWindowEventListener} from "@/utils";
import {computed} from "@vue/reactivity";
import {isEqual, throttle} from "lodash";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {library} from "@fortawesome/fontawesome-svg-core";
import {bcv_parser as BcvParser} from "bible-passage-reference-parser/js/en_bcv_parser.min";
import {
    faBookmark, faChevronCircleDown, faCompressArrowsAlt,
    faEdit, faEllipsisH, faEye, faEyeSlash,
    faFileAlt, faFireAlt, faHandPointer,
    faHeadphones, faHeart, faHistory,
    faIndent, faInfoCircle, faOutdent, faPlus,
    faPlusCircle, faQuestionCircle, faShareAlt, faSort,
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

export const bookmarkingModes = {
    verticalColorBars: 0,
    blend: 1,
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
        bookmarkingMode: bookmarkingModes.verticalColorBars,
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
        hasActiveIndicator: false,
        activeSince: 0,
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
        const oldShowBookmarks = config.showBookmarks;
        const oldMyNotes = config.showMyNotes;
        const isBible = documentType.value === DocumentTypes.BIBLE_DOCUMENT
        const needsRefreshLocation = !initial && (isBible || documentType.value === DocumentTypes.OSIS_DOCUMENT) && getNeedRefreshLocation(newConfig);
        const needBookmarkRefresh = getNeedBookmarkRefresh(newConfig);

        if (needsRefreshLocation) emit(Events.CONFIG_CHANGED, defer)

        if(isBible && needBookmarkRefresh) {
            config.showBookmarks = false
            config.showMyNotes = false
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
            if (newConfig.showBookmarks === undefined) {
                // eslint-disable-next-line require-atomic-updates
                config.showBookmarks = oldShowBookmarks;
            }
            if (newConfig.showMyNotes === undefined) {
                // eslint-disable-next-line require-atomic-updates
                config.showMyNotes = oldMyNotes;
            }
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
    library.add(faCompressArrowsAlt)
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

export function useVerseMap() {
    const verses = new Map();
    function register(ordinal, obj) {
        let array = verses.get(ordinal);
        if(array === undefined) {
            array = [];
            verses.set(ordinal, array);
        }
        array.push(obj);
    }
    function getVerses(ordinal) {
        return verses.get(ordinal) || []
    }

    const highlights = reactive([]);
    const hasHighlights = computed(() => highlights.length > 0);

    function registerEndHighlight(fn) {
        highlights.push(fn);
    }

    function resetHighlights() {
        highlights.forEach(f => f())
        highlights.splice(0);
    }

    return {register, getVerses, registerEndHighlight, resetHighlights, hasHighlights}
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
        const c = count.get(bookInitials);
        if(c > 1) {
            count.set(bookInitials, c-1);
        } else {
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

    watch(modalOpen, v => android.reportModalState(v), {flush: "sync"})

    return {register, closeModals, modalOpen}
}
