/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

/* eslint-disable no-undef */
import {emit} from "@/eventbus";
import {Deferred, rangeInside, setupDocumentEventListener, sleep, stubsFor} from "@/utils";
import {onMounted, reactive, Ref} from "vue";
import {calculateOffsetToVerse, ReachedRootError} from "@/dom";
import {isFunction, union} from "lodash";
import {Config, errorBox} from "@/composables/config";
import {AsyncFunc, JournalEntryType, JSONString, LogEntry} from "@/types/common";
import {Bookmark, CombinedRange, StudyPadBookmarkItem, StudyPadItem, StudyPadTextItem} from "@/types/client-objects";
import {BibleDocumentType} from "@/types/documents";

export type BibleJavascriptInterface = {
    scrolledToOrdinal: (ordinal: number) => void,
    setClientReady: () => void,
    setLimitAmbiguousModalSize: (value: boolean) => void,
    requestPreviousChapter: AsyncFunc,
    requestNextChapter: AsyncFunc,
    refChooserDialog: AsyncFunc,
    saveBookmarkNote: (bookmarkId: number, note: string | null) => void,
    removeBookmark: (bookmarkId: number) => void,
    assignLabels: (bookmarkId: number) => void,
    console: (loggerName: string, message: string) => void
    selectionCleared: () => void,
    reportInputFocus: (newValue: boolean) => void,
    openExternalLink: (link: string) => void,
    openDownloads: () => void,
    setEditing: (enabled: boolean) => void,
    createNewJournalEntry: (labelId: number, entryType?: JournalEntryType, afterEntryId?: number) => void,
    deleteJournalEntry: (journalId: number) => void,
    removeBookmarkLabel: (bookmarkId: number, labelId: number) => void,
    updateOrderNumber: (labelId: number, data: JSONString) => void,
    getActiveLanguages: () => string,
    toast: (text: string) => void,
    updateJournalTextEntry: (data: JSONString) => void,
    updateBookmarkToLabel: (data: JSONString) => void
    shareBookmarkVerse: (bookmarkId: number) => void,
    shareVerse: (bookInitials: string, startOrdinal: number, endOrdinal: number) => void,
    addBookmark: (bookInitials: string, startOrdinal: number, endOrdinal: number, addNote: boolean) => void,
    compare: (bookInitials: string, verseOrdinal: number, endOrdinal: number) => void,
    openStudyPad: (labelId: number, bookmarkId: number) => void,
    openMyNotes: (v11n: string, ordinal: number) => void,
    speak: (bookInitials: string, ordinal: number) => void,
    setAsPrimaryLabel: (bookmarkId: number, labelId: number) => void,
    toggleBookmarkLabel: (bookmarkId: number, labelId: number) => void,
    reportModalState: (value: boolean) => void,
    querySelection: (bookmarkId: number, value: boolean) => void,
    setBookmarkWholeVerse: (bookmarkId: number, value: boolean) => void,
    toggleCompareDocument: (documentId: string) => void,
    helpDialog: (content: string, title: string | null) => void,
    shareHtml: (html: string) => void,
    helpBookmarks: () => void,
    onKeyDown: (key: string) => void,
}

//type NotInJsSide =
//    "updateJournalTextEntry" |
//    "updateOrderNumber" |
//    "console"|
//    "selectionCleared"|
//    "updateBookmarkToLabel"

//export type UseAndroid = Omit<AndroidInterface, NotInJsSide> & {
//    updateOrderNumber: (labelId: number, bookmarks: StudyPadBookmarkItem[], journals: StudyPadTextItem[]) => void
//    updateJournalEntry: (entry: StudyPadItem, changes: Partial<StudyPadItem>) => void
//}

export type UseAndroid = ReturnType<typeof useAndroid>

let callId = 0;

export const logEntries = reactive<LogEntry[]>([])

const logEntriesTemp: LogEntry[] = [];

function addLog(logEntry: Pick<LogEntry, "type" | "msg">) {
    const previous = logEntriesTemp.find(v => v.msg === logEntry.msg && v.type === logEntry.type);
    if (previous) {
        previous.count++;
        return;
    }
    logEntriesTemp.push({...logEntry, count: 1});
}

let logSyncEnabled = false;

export async function enableLogSync(value: boolean) {
    logSyncEnabled = value;
    while (logSyncEnabled) {
        await sleep(1000)
        if (logEntriesTemp.length > logEntries.length) {
            logEntries.push(...logEntriesTemp.slice(logEntries.length, logEntriesTemp.length));
        }
    }
}

export function clearLog() {
    logEntriesTemp.splice(0);
    logEntries.splice(0);
}

export function patchAndroidConsole() {
    const origConsole = window.console;
    window.bibleViewDebug.logEntries = logEntries;
    // Override normal console, so that argument values also propagate to Android logcat
    const enableAndroidLogging = process.env.NODE_ENV !== "development";
    window.console = {
        ...origConsole,
        _msg(s, args) {
            const printableArgs = args.map(v => isFunction(v) ? v : v ? JSON.stringify(v).slice(0, 500) : v);
            return `${s} ${printableArgs}`
        },
        flog(s, ...args) {
            if (enableAndroidLogging && errorBox) window.android.console('flog', this._msg(s, args))
            origConsole.log(this._msg(s, args))
        },
        log(s, ...args) {
            if (enableAndroidLogging && errorBox) window.android.console('log', this._msg(s, args))
            origConsole.log(s, ...args)
        },
        error(s, ...args) {
            if (errorBox) {
                addLog({type: "ERROR", msg: this._msg(s, args)});
                if (enableAndroidLogging) window.android.console('error', this._msg(s, args))
            }
            origConsole.error(s, ...args)
        },
        warn(s, ...args) {
            if (errorBox) {
                addLog({type: "WARN", msg: this._msg(s, args)});
                if (enableAndroidLogging) window.android.console('warn', this._msg(s, args))
            }
            origConsole.warn(s, ...args)
        }
    }
}

export type QuerySelection = {
    bookInitials: string
    startOrdinal: number,
    startOffset: number,
    endOrdinal: number,
    endOffset: number,
    bookmarks: number[],
    text: string
}

export function useAndroid({bookmarks}: { bookmarks: Ref<Bookmark[]> }, config: Config) {
    const responsePromises = new Map();

    function response(callId: number, returnValue: any) {
        const val = responsePromises.get(callId);
        if (val) {
            const {promise, func} = val;
            responsePromises.delete(callId);
            console.log("Returning response from async android function: ", func, callId, returnValue);
            promise.resolve(returnValue);
        } else {
            console.error("Promise not found for callId", callId)
        }
    }

    function querySelection(): QuerySelection | string | null {
        const selection = window.getSelection()!;
        if (selection.rangeCount < 1 || selection.isCollapsed) return null;
        const selectionOnly = selection.toString();
        const range = selection.getRangeAt(0)!;
        const documentElem: HTMLElement = range.startContainer.parentElement!.closest(".bible-document")!;
        if (!documentElem) return selectionOnly

        const bookInitials = documentElem.dataset.bookInitials!;
        let startOrdinal: number, startOffset: number, endOrdinal: number, endOffset: number;

        try {
            ({ordinal: startOrdinal, offset: startOffset} =
                calculateOffsetToVerse(range.startContainer, range.startOffset));

            ({ordinal: endOrdinal, offset: endOffset} =
                calculateOffsetToVerse(range.endContainer, range.endOffset));

        } catch (e) {
            if (e instanceof ReachedRootError) {
                return selectionOnly
            } else {
                throw e;
            }
        }

        function bookmarkRange(b: Bookmark): CombinedRange {
            const offsetRange = b.offsetRange || [0, null]
            if (b.bookInitials !== bookInitials) {
                offsetRange[0] = 0;
                offsetRange[1] = null;
            }
            return [[b.ordinalRange[0], offsetRange[0]], [b.ordinalRange[1], offsetRange[1]]]
        }

        const filteredBookmarks = bookmarks.value.filter(b => rangeInside(
            bookmarkRange(b), [[startOrdinal, startOffset], [endOrdinal, endOffset]])
        );

        const deleteBookmarks = union(filteredBookmarks.map(b => b.id));

        return {
            bookInitials,
            startOrdinal,
            startOffset,
            endOrdinal,
            endOffset,
            bookmarks: deleteBookmarks,
            text: selection.toString()
        };
    }

    window.bibleView.response = response;
    window.bibleView.emit = emit;
    window.bibleView.querySelection = querySelection

    async function deferredCall(func: AsyncFunc): Promise<any> {
        const promise = new Deferred();
        const thisCall = callId++;
        responsePromises.set(thisCall, {func, promise});
        console.log("Calling async android function: ", func, thisCall);
        func(thisCall);
        const returnValue = await promise.wait();
        console.log("Response from async android function: ", thisCall, returnValue);
        return returnValue
    }

    async function requestPreviousChapter(): Promise<BibleDocumentType> {
        return deferredCall((callId) => window.android.requestPreviousChapter(callId));
    }

    async function requestNextChapter(): Promise<BibleDocumentType> {
        return deferredCall((callId) => window.android.requestNextChapter(callId));
    }

    async function refChooserDialog(): Promise<string> {
        return deferredCall((callId) => window.android.refChooserDialog(callId));
    }

    function scrolledToOrdinal(ordinal: number | null) {
        if (ordinal == null || ordinal < 0) return;
        window.android.scrolledToOrdinal(ordinal)
    }

    function saveBookmarkNote(bookmarkId: number, noteText: string | null) {
        window.android.saveBookmarkNote(bookmarkId, noteText);
    }

    function removeBookmark(bookmarkId: number) {
        window.android.removeBookmark(bookmarkId);
    }

    function assignLabels(bookmarkId: number) {
        window.android.assignLabels(bookmarkId);
    }

    function toggleBookmarkLabel(bookmarkId: number, labelId: number) {
        window.android.toggleBookmarkLabel(bookmarkId, labelId);
    }

    function setClientReady() {
        window.android.setClientReady();
    }

    function reportInputFocus(value: boolean) {
        window.android.reportInputFocus(value);
    }

    function openExternalLink(link: string) {
        window.android.openExternalLink(link);
    }

    function setEditing(value: boolean) {
        window.android.setEditing(value);
    }

    function createNewJournalEntry(labelId: number, afterEntryType: JournalEntryType = "none", afterEntryId: number = 0) {
        window.android.createNewJournalEntry(labelId, afterEntryType, afterEntryId);
    }

    function deleteJournalEntry(journalId: number) {
        window.android.deleteJournalEntry(journalId);
    }

    function getActiveLanguages(): string[] {
        return JSON.parse(window.android.getActiveLanguages());
    }

    function removeBookmarkLabel(bookmarkId: number, labelId: number) {
        window.android.removeBookmarkLabel(bookmarkId, labelId);
    }

    function shareBookmarkVerse(bookmarkId: number) {
        window.android.shareBookmarkVerse(bookmarkId);
    }

    function shareVerse(bookInitials: string, startOrdinal: number, endOrdinal?: number) {
        window.android.shareVerse(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function addBookmark(bookInitials: string, startOrdinal: number, endOrdinal?: number, addNote: boolean = false) {
        window.android.addBookmark(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1, addNote);
    }

    function compare(bookInitials: string, startOrdinal: number, endOrdinal?: number) {
        window.android.compare(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function openStudyPad(labelId: number, bookmarkId: number) {
        window.android.openStudyPad(labelId, bookmarkId);
    }

    function openMyNotes(v11n: string, ordinal: number) {
        window.android.openMyNotes(v11n, ordinal);
    }

    function speak(bookInitials: string, ordinal: number) {
        window.android.speak(bookInitials, ordinal);
    }

    function openDownloads() {
        window.android.openDownloads();
    }

    function updateOrderNumber(labelId: number, bookmarks: StudyPadBookmarkItem[], journals: StudyPadTextItem[]) {
        const orderNumberPairs: (l: StudyPadItem[]) => [number, number][] =
            l => l.map((v: StudyPadItem) => [v.id, v.orderNumber])
        window.android.updateOrderNumber(labelId, JSON.stringify(
            {
                bookmarks: orderNumberPairs(bookmarks),
                journals: orderNumberPairs(journals)
            })
        );
    }

    function toast(text: string) {
        window.android.toast(text);
    }

    function updateJournalEntry(entry: StudyPadItem, changes: Partial<StudyPadItem>) {
        const changedEntry = {...entry, ...changes}
        if (entry.type === "journal") {
            window.android.updateJournalTextEntry(JSON.stringify(changedEntry as StudyPadTextItem));
        } else if (entry.type === "bookmark") {
            const changedBookmarkItem = changedEntry as StudyPadBookmarkItem
            const entry = {
                bookmarkId: changedBookmarkItem.id,
                labelId: changedBookmarkItem.bookmarkToLabel.labelId,
                indentLevel: changedBookmarkItem.indentLevel,
                orderNumber: changedBookmarkItem.orderNumber,
                expandContent: changedBookmarkItem.expandContent,
            }
            window.android.updateBookmarkToLabel(JSON.stringify(entry));
        }
    }

    function setAsPrimaryLabel(bookmarkId: number, labelId: number) {
        window.android.setAsPrimaryLabel(bookmarkId, labelId);
    }

    function setBookmarkWholeVerse(bookmarkId: number, value: boolean) {
        window.android.setBookmarkWholeVerse(bookmarkId, value);
    }

    function reportModalState(value: boolean) {
        window.android.reportModalState(value)
    }

    function toggleCompareDocument(docId: string) {
        window.android.toggleCompareDocument(docId);
    }

    function helpDialog(content: string, title: string | null = null) {
        window.android.helpDialog(content, title);
    }

    function helpBookmarks() {
        window.android.helpBookmarks();
    }

    function setLimitAmbiguousModalSize(value: boolean) {
        window.android.setLimitAmbiguousModalSize(value);
    }

    function shareHtml(value: string) {
        window.android.shareHtml(value);
    }

    function onKeyDown(key: string) {
        window.android.onKeyDown(key);
    }

    const exposed = {
        shareHtml,
        helpBookmarks,
        setLimitAmbiguousModalSize,
        setEditing,
        reportInputFocus,
        saveBookmarkNote,
        requestPreviousChapter,
        requestNextChapter,
        scrolledToOrdinal,
        setClientReady,
        querySelection,
        removeBookmark,
        assignLabels,
        openExternalLink,
        createNewJournalEntry,
        deleteJournalEntry,
        removeBookmarkLabel,
        updateOrderNumber,
        updateJournalEntry,
        getActiveLanguages,
        toast,
        shareBookmarkVerse,
        openStudyPad,
        setAsPrimaryLabel,
        toggleBookmarkLabel,
        reportModalState,
        setBookmarkWholeVerse,
        toggleCompareDocument,
        openMyNotes,
        openDownloads,
        refChooserDialog,
        shareVerse,
        addBookmark,
        compare,
        speak,
        helpDialog,
        onKeyDown,
    }

    if (config.developmentMode) return {
        ...stubsFor(exposed, {
            getActiveLanguages: ['he', 'nl', 'en'],
        }),
        querySelection
    } as typeof exposed

    setupDocumentEventListener("selectionchange", () => {
        const sel = window.getSelection()!;
        if (sel.rangeCount > 0 && sel.getRangeAt(0).collapsed) {
            window.android.selectionCleared();
        }
    });

    onMounted(() => {
        setClientReady();
    });

    return exposed;
}
