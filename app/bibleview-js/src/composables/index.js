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

import {getCurrentInstance, inject, nextTick, reactive, ref, watch} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {Deferred, setupWindowEventListener} from "@/utils";
import {computed} from "@vue/reactivity";
import {throttle} from "lodash";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {library} from "@fortawesome/fontawesome-svg-core";
import {
    faBookmark,
    faEdit,
    faEllipsisH,
    faHeadphones, faIndent, faOutdent,
    faPlusCircle, faSort,
    faTrash
} from "@fortawesome/free-solid-svg-icons";
import Color from "color";

let developmentMode = false;
let testMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}
if(process.env.NODE_ENV === "test") {
    testMode = true;
}

export function useVerseNotifier(config, {scrolledToVerse}, topElement) {
    const currentVerse = ref(null);
    watch(() => currentVerse.value,  value => scrolledToVerse(value));

    const lineHeight = computed(() =>
        parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height'))
    );

    const onScroll = throttle(() => {
        const y = config.topOffset + lineHeight.value*0.8;

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

export const bookmarkingModes = {
    verticalColorBars: 0,
    blend: 1,
}

export const strongsModes = {
    off: 0,
    inline: 1,
    links: 2,
}

export function useConfig() {
    const config = reactive({
        bookmarkingMode: bookmarkingModes.verticalColorBars,
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
        showCrossReferences: true,
        showFootNotes: true,
        font: {
            fontFamily: "sans-serif",
            fontSize: 16,
        },
        showBookmarks: true,
        showMyNotes: true,

        colors: {
            dayBackground: -1,
            dayNoise: 0,
            dayTextColor: null,
            nightBackground: null,
            nightNoise: 0,
            nightTextColor: -16777216,
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

        topOffset: 100,
        bottomOffset: 100,
        infiniteScroll: true,
        nightMode: false,

        developmentMode,
        testMode,
    })

    if(developmentMode) {
        window.bibleViewDebug.config = config;
    }

    setupEventBusListener(Events.SET_CONFIG, async ({config: c, initial = false, nightMode = false} = {}) => {
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
        config.nightMode = nightMode;
        if(c.showBookmarks === undefined) {
            // eslint-disable-next-line require-atomic-updates
            config.showBookmarks = oldValue;
        }
        config.showChapterNumbers = config.showVerseNumbers;
        if(!initial) {
            await nextTick();
        }
        defer.resolve()
    })

    return {config};
}

export function useStrings() {
    return {
        openMyNotes: "Open in My Notes window",
        chapterNum: "— %d —",
        verseNum: "%d ",
        noteText: "Footnotes (%s)",
        crossReferenceText: "Cross references",
        findAllOccurrences: "Find all occurrences",
        reportError: "Report an error",
        errorTitle: "Error occurred",
        footnoteTypeUndefined: "Undefined type",
        footnoteTypeStudy: "Study",
        footnoteTypeExplanation: "Explanation",
        footnoteTypeVariant: "Variant",
        footnoteTypeAlternative: "Alternative",
        footnoteTypeTranslation: "Translation",
        clearLog: "Clear error log",
        bookmarkInfo: "Info",
        editNote: "Edit",
        editNotePlaceholder: "Edit a note for bookmark",
        editTextPlaceholder: "Tap to edit text",
        inputPlaceholder: "Write here",
        inputReference: "Give bible reference, in OSIS format",
        removeBookmark: "Remove",
        assignLabels: "Labels",
        bookmarkAccurate: "Bookmark was created in %s",
        ok: "Ok",
        ambiguousSelection: "Which one of these do you wish to do?",
        cancel: "Cancel",
        removeBookmarkConfirmationTitle: "Remove bookmark?",
        removeBookmarkConfirmation: "Are you sure you want to remove bookmark?",
        closeModal: "Close",
        createdAt: "Created: %s",
        lastUpdatedOn: "Last updated: %s",
        strongsLink: "Open strongs",
        journalLink: "Open journal",
        externalLink: "Open external link",
        referenceLink: "Open reference link",
        openFootnote: "Open footnote",
        openBookmark: "Open bookmark (%s)",
        noNotes: "No My Notes for this chapter",
        emptyJournal: "Empty journal",
        journalModalTitle: "Edit journal entry",
        doYouWantToDeleteEntry: "Are you sure you want to delete this journal entry?",
        removeJournalConfirmationTitle: "Remove journal entry?",
        dragHelp: "You can drag & drop item to re-order journal",
        saved: "saved",
        openAll: "Show all references",
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

    function formatTimestamp(timestamp) {
        return new Date(timestamp).toLocaleString()
    }

    function adjustedColor(color) {
        let col = Color(color);
        if(config.nightMode) {
            col = col.darken(0.2);
        } else {
            col = col.darken(0.2);
        }
        return col.hsl();
    }

    function abbreviated(str, n, useWordBoundary = true) {
        if(!str) return ""
        if (str.length <= n) { return str; }
        const subString = str.substr(0, n-1); // the original check
        return (useWordBoundary
            ? subString.substr(0, subString.lastIndexOf(" "))
            : subString) + "...";
    }

    return {config, strings, sprintf, split, adjustedColor, formatTimestamp, abbreviated}
}

export function useFontAwesome() {
    library.add(faHeadphones)
    library.add(faEdit)
    library.add(faBookmark)
    library.add(faPlusCircle)
    library.add(faTrash)
    library.add(faEllipsisH)
    library.add(faSort)
    library.add(faIndent)
    library.add(faOutdent)
}

export function checkUnsupportedProps(props, attributeName, values = []) {
    const value = props[attributeName];
    if(value && !values.includes(value)) {
        const tagName = getCurrentInstance().type.name
        const origin = inject("verseInfo", {}).osisID;
        console.warn(`${tagName}: Unsupported (ignored) attribute "${attributeName}" value "${value}", origin: ${origin}`)
    }
}

export function useJournal(label) {
    const journalTextEntries = reactive(new Map());
    const bookmarkToLabels = reactive(new Map());

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
    return {references, collect}
}
