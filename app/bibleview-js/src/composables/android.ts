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
import {AsyncFunc, StudyPadEntryType, JSONString, LogEntry, Nullable} from "@/types/common";
import {
    BaseBookmark,
    CombinedRange,
    StudyPadBibleBookmarkItem,
    StudyPadGenericBookmarkItem,
    StudyPadItem,
    StudyPadTextItem
} from "@/types/client-objects";
import {BibleDocumentType} from "@/types/documents";
import {isBibleBookmark, isGenericBookmark} from "@/composables/bookmarks";

export type BibleJavascriptInterface = {
    scrolledToOrdinal: (ordinal: number) => void,
    setClientReady: () => void,
    setLimitAmbiguousModalSize: (value: boolean) => void,
    requestPreviousChapter: AsyncFunc,
    requestNextChapter: AsyncFunc,
    refChooserDialog: AsyncFunc,
    saveBookmarkNote: (bookmarkId: IdType, note: Nullable<string>) => void,
    saveGenericBookmarkNote: (bookmarkId: IdType, note: Nullable<string>) => void,
    removeBookmark: (bookmarkId: IdType) => void,
    removeGenericBookmark: (bookmarkId: IdType) => void,
    assignLabels: (bookmarkId: IdType) => void,
    genericAssignLabels: (bookmarkId: IdType) => void,
    console: (loggerName: string, message: string) => void
    selectionCleared: () => void,
    reportInputFocus: (newValue: boolean) => void,
    openExternalLink: (link: string) => void,
    openEpubLink: (bookInitials: string, toKey: string, toId: string) => void,
    openDownloads: () => void,
    setEditing: (enabled: boolean) => void,
    createNewStudyPadEntry: (labelId: IdType, entryType?: StudyPadEntryType, afterEntryId?: IdType) => void,
    deleteStudyPadEntry: (studyPadId: IdType) => void,
    removeBookmarkLabel: (bookmarkId: IdType, labelId: IdType) => void,
    removeGenericBookmarkLabel: (bookmarkId: IdType, labelId: IdType) => void,
    updateOrderNumber: (labelId: IdType, data: JSONString) => void,
    getActiveLanguages: () => string,
    toast: (text: string) => void,
    updateStudyPadTextEntry: (data: JSONString) => void,
    updateStudyPadTextEntryText: (id: IdType, text: string) => void,
    updateBookmarkToLabel: (data: JSONString) => void
    updateGenericBookmarkToLabel: (data: JSONString) => void
    shareBookmarkVerse: (bookmarkId: IdType) => void,
    shareVerse: (bookInitials: string, startOrdinal: number, endOrdinal: number) => void,
    addBookmark: (bookInitials: string, startOrdinal: number, endOrdinal: number, addNote: boolean) => void,
    addGenericBookmark: (bookInitials: string, osisRef: string, startOrdinal: number, endOrdinal: number, addNote: boolean) => void,
    compare: (bookInitials: string, verseOrdinal: number, endOrdinal: number) => void,
    openStudyPad: (labelId: IdType, bookmarkId: IdType) => void,
    openMyNotes: (v11n: string, ordinal: number) => void,
    speak: (bookInitials: string, startOrdinal: number, endOrdinal: number) => void,
    speakGeneric: (bookInitials: string, osisRef: string, startOrdinal: number, endOrdinal: number) => void,
    setAsPrimaryLabel: (bookmarkId: IdType, labelId: IdType) => void,
    setAsPrimaryLabelGeneric: (bookmarkId: IdType, labelId: IdType) => void,
    toggleBookmarkLabel: (bookmarkId: IdType, labelId: IdType) => void,
    toggleGenericBookmarkLabel: (bookmarkId: IdType, labelId: IdType) => void,
    reportModalState: (value: boolean) => void,
    querySelection: (bookmarkId: IdType, value: boolean) => void,
    setBookmarkWholeVerse: (bookmarkId: IdType, value: boolean) => void,
    setGenericBookmarkWholeVerse: (bookmarkId: IdType, value: boolean) => void,
    toggleCompareDocument: (documentId: string) => void,
    helpDialog: (content: string, title: Nullable<string>) => void,
    shareHtml: (html: string) => void,
    helpBookmarks: () => void,
    onKeyDown: (key: string) => void,
}

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
    osisRef: string
    startOrdinal: number,
    startOffset: number,
    endOrdinal: number,
    endOffset: number,
    bookmarks: IdType[],
    text: string
}

export function useAndroid({bookmarks}: { bookmarks: Ref<BaseBookmark[]> }, config: Config) {
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
        const documentElem: HTMLElement = range.startContainer.parentElement!.closest(".document")!;
        if (!documentElem) {
            console.log(`querySelection: returning only selection ${selectionOnly}`)
            return selectionOnly
        }

        const bookInitials = documentElem.dataset.bookInitials!;
        const osisRef = documentElem.dataset.osisRef!;
        let startOrdinal: number, startOffset: number, endOrdinal: number, endOffset: number;

        try {
            ({ordinal: startOrdinal, offset: startOffset} =
                calculateOffsetToVerse(range.startContainer, range.startOffset));

            ({ordinal: endOrdinal, offset: endOffset} =
                calculateOffsetToVerse(range.endContainer, range.endOffset));

        } catch (e) {
            if (e instanceof ReachedRootError) {
                console.log(`querySelection: ReachedRootError, returning only selection ${selectionOnly}`)
                return selectionOnly
            } else {
                throw e;
            }
        }

        function bookmarkRange(b: BaseBookmark): CombinedRange {
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

        const returnSelection: QuerySelection = {
            bookInitials,
            osisRef,
            startOrdinal,
            startOffset,
            endOrdinal,
            endOffset,
            bookmarks: deleteBookmarks,
            text: selection.toString()
        }

        console.log(`querySelection: returning selection`, {returnSelection})
        return returnSelection;
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

    function scrolledToOrdinal(ordinal: Nullable<number>) {
        if (ordinal == null || ordinal < 0) return;
        window.android.scrolledToOrdinal(ordinal)
    }

    function saveBookmarkNote(bookmark: BaseBookmark, noteText: Nullable<string>) {
        if(isBibleBookmark(bookmark)) {
            window.android.saveBookmarkNote(bookmark.id, noteText);
        } else if(isGenericBookmark(bookmark)) {
            window.android.saveGenericBookmarkNote(bookmark.id, noteText);
        }
    }

    function removeBookmark(bookmark: BaseBookmark) {
        if(isBibleBookmark(bookmark)) {
            window.android.removeBookmark(bookmark.id);
        } else if(isGenericBookmark(bookmark)) {
            window.android.removeGenericBookmark(bookmark.id);
        }
    }

    function assignLabels(bookmark: BaseBookmark) {
        if (isBibleBookmark(bookmark)) {
            window.android.assignLabels(bookmark.id);
        } else if(isGenericBookmark(bookmark)) {
            window.android.genericAssignLabels(bookmark.id);
        }
    }

    function toggleBookmarkLabel(bookmark: BaseBookmark, labelId: IdType) {
        if(isBibleBookmark(bookmark)) {
            window.android.toggleBookmarkLabel(bookmark.id, labelId);
        } else if(isGenericBookmark(bookmark)) {
            window.android.toggleGenericBookmarkLabel(bookmark.id, labelId);
        }
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

    function openEpubLink(bookInitials: string, toKey: string, toId: string) {
        window.android.openEpubLink(bookInitials, toKey, toId);
    }

    function setEditing(value: boolean) {
        window.android.setEditing(value);
    }

    function createNewStudyPadEntry(labelId: IdType, afterEntryType: StudyPadEntryType = "none", afterEntryId: IdType = "") {
        window.android.createNewStudyPadEntry(labelId, afterEntryType, afterEntryId);
    }

    function deleteStudyPadEntry(studyPadId: IdType) {
        window.android.deleteStudyPadEntry(studyPadId);
    }

    function getActiveLanguages(): string[] {
        return JSON.parse(window.android.getActiveLanguages());
    }

    function removeBookmarkLabel(bookmark: BaseBookmark, labelId: IdType) {
        if(isBibleBookmark(bookmark)) {
            window.android.removeBookmarkLabel(bookmark.id, labelId);
        } else if(isGenericBookmark(bookmark)) {
            window.android.removeGenericBookmarkLabel(bookmark.id, labelId);
        }
    }

    function shareBookmarkVerse(bookmark: BaseBookmark) {
        if(isBibleBookmark(bookmark)) {
            window.android.shareBookmarkVerse(bookmark.id);
        } else {
            console.error("Only bible bookmarks supported for share feature")
        }
    }

    function shareVerse(bookInitials: string, startOrdinal: number, endOrdinal?: number) {
        window.android.shareVerse(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function addBookmark(bookInitials: string, startOrdinal: number, endOrdinal?: number, addNote: boolean = false) {
        window.android.addBookmark(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1, addNote);
    }

    function addGenericBookmark(bookInitials: string, osisRef: string, startOrdinal: number, endOrdinal?: number, addNote: boolean = false) {
        window.android.addGenericBookmark(bookInitials, osisRef, startOrdinal, endOrdinal ? endOrdinal : -1, addNote);
    }

    function compare(bookInitials: string, startOrdinal: number, endOrdinal?: number) {
        window.android.compare(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function openStudyPad(labelId: IdType, bookmark: BaseBookmark) {
        if(isBibleBookmark(bookmark) || isGenericBookmark(bookmark)) {
            // Exceptionally here bookmark type does not matter
            window.android.openStudyPad(labelId, bookmark.id);
        }
    }

    function openMyNotes(v11n: string, ordinal: number) {
        window.android.openMyNotes(v11n, ordinal);
    }

    function speak(bookInitials: string, startOrdinal: number, endOrdinal?: number) {
        window.android.speak(bookInitials, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function speakGeneric(bookInitials: string, osisRef: string, startOrdinal: number, endOrdinal?: number) {
        window.android.speakGeneric(bookInitials, osisRef, startOrdinal, endOrdinal ? endOrdinal : -1);
    }

    function openDownloads() {
        window.android.openDownloads();
    }

    function updateOrderNumber(
        labelId: IdType,
        bookmarks: StudyPadBibleBookmarkItem[],
        genericBookmarks: StudyPadGenericBookmarkItem[],
        studyPadTextItems: StudyPadTextItem[]
    ) {
        const orderNumberPairs: (l: StudyPadItem[]) => {first: IdType, second: number}[] =
            l => l.map((v: StudyPadItem) => ({first: v.id, second: v.orderNumber}))
        window.android.updateOrderNumber(labelId, JSON.stringify(
            {
                bookmarks: orderNumberPairs(bookmarks),
                genericBookmarks: orderNumberPairs(genericBookmarks),
                studyPadTextItems: orderNumberPairs(studyPadTextItems)
            })
        );
    }

    function toast(text: string) {
        window.android.toast(text);
    }

    function updateStudyPadEntry(entry: StudyPadItem, changes: Partial<StudyPadItem>) {
        const changedEntry = {...entry, ...changes}
        if (entry.type === "journal") {
            const {text, ...rest} = changes;
            if(text !== undefined) {
                window.android.updateStudyPadTextEntryText(entry.id, text);
            }
            if(Object.keys(rest).length > 0) {
                window.android.updateStudyPadTextEntry(JSON.stringify(changedEntry as StudyPadTextItem));
            }
        } else if (entry.type === "bookmark" || entry.type === "generic-bookmark") {
            const changedBookmarkItem = changedEntry as StudyPadBibleBookmarkItem
            const e = {
                bookmarkId: changedBookmarkItem.id,
                labelId: changedBookmarkItem.bookmarkToLabel.labelId,
                indentLevel: changedBookmarkItem.indentLevel,
                orderNumber: changedBookmarkItem.orderNumber,
                expandContent: changedBookmarkItem.expandContent,
            }
            if(isBibleBookmark(entry)) {
                window.android.updateBookmarkToLabel(JSON.stringify(e));
            } else if(isGenericBookmark(entry)) {
                window.android.updateGenericBookmarkToLabel(JSON.stringify(e));
            }
        }
    }

    function setAsPrimaryLabel(bookmark: BaseBookmark, labelId: IdType) {
        if(isBibleBookmark(bookmark)) {
            window.android.setAsPrimaryLabel(bookmark.id, labelId);
        } else if(isGenericBookmark(bookmark)) {
            window.android.setAsPrimaryLabelGeneric(bookmark.id, labelId);
        }
    }

    function setBookmarkWholeVerse(bookmark: BaseBookmark, value: boolean) {
        if(isBibleBookmark(bookmark)) {
            window.android.setBookmarkWholeVerse(bookmark.id, value);
        } else {
            window.android.setGenericBookmarkWholeVerse(bookmark.id, value);
        }
    }

    function reportModalState(value: boolean) {
        window.android.reportModalState(value)
    }

    function toggleCompareDocument(docId: string) {
        window.android.toggleCompareDocument(docId);
    }

    function helpDialog(content: string, title: Nullable<string> = null) {
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
        openEpubLink,
        createNewStudyPadEntry,
        deleteStudyPadEntry,
        removeBookmarkLabel,
        updateOrderNumber,
        updateStudyPadEntry,
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
        addGenericBookmark,
        compare,
        speak,
        speakGeneric,
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
