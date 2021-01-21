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
import {emit} from "@/eventbus";
import {Deferred, rangeInside, setupDocumentEventListener, sleep, stubsFor} from "@/utils";
import {onMounted} from "@vue/runtime-core";
import {calculateOffsetToVerse} from "@/dom";
import {isFunction, union} from "lodash";
import {reactive} from "@vue/reactivity";

let callId = 0;

export const logEntries = reactive([])
const logEntriesTemp = [];

function addLog(logEntry) {
    const previous = logEntriesTemp.find(v => v.msg === logEntry.msg && v.type === logEntry.type);
    if(previous) {
        previous.count ++;
        return;
    }
    logEntriesTemp.push({...logEntry, count: 1});
}

let logSyncEnabled = false;

export async function enableLogSync(value) {
    logSyncEnabled = value;
    while(logSyncEnabled) {
        await sleep(1000)
        if(logEntriesTemp.length > logEntries.length) {
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
        _msg(s, args) {
            const printableArgs = args.map(v => isFunction(v) ? v : v ? JSON.stringify(v).slice(0, 500): v);
            return `${s} ${printableArgs}`
        },
        flog(s, ...args) {
            if(enableAndroidLogging) android.console('flog', this._msg(s, args))
            origConsole.log(this._msg(s, args))
        },
        log(s, ...args) {
            if(enableAndroidLogging) android.console('log', this._msg(s, args))
            origConsole.log(s, ...args)
        },
        error(s, ...args) {
            addLog({type: "ERROR", msg: this._msg(s, args)});
            if(enableAndroidLogging) android.console('error', this._msg(s, args))
            origConsole.error(s, ...args)
        },
        warn(s, ...args) {
            addLog({type: "WARN", msg: this._msg(s, args)});
            if(enableAndroidLogging) android.console('warn', this._msg(s, args))
            origConsole.warn(s, ...args)
        }
    }
}

export function useAndroid({bookmarks}, config) {
    const responsePromises = new Map();

    function response(callId, returnValue) {
        const val = responsePromises.get(callId);
        if(val) {
            const {promise, func} = val;
            responsePromises.delete(callId);
            console.log("Returning response from async android function: ", func, callId, returnValue);
            promise.resolve(returnValue);
        } else {
            console.error("Promise not found for callId", callId)
        }
    }

    function querySelection() {
        const selection = window.getSelection();
        if (selection.rangeCount < 1 || selection.collapsed) return null;
        const range = selection.getRangeAt(0);
        const documentElem = range.startContainer.parentElement.closest(".bible-document");
        if(!documentElem) return null

        const bookInitials = documentElem.dataset.bookInitials;

        const {ordinal: startOrdinal, offset: startOffset} =
            calculateOffsetToVerse(range.startContainer, range.startOffset, true);
        const {ordinal: endOrdinal, offset: endOffset} =
            calculateOffsetToVerse(range.endContainer, range.endOffset);

        function bookmarkRange(b) {
            const offsetRange = b.offsetRange || [0, null]
            if(b.bookInitials !== bookInitials) {
                offsetRange[0] = 0;
                offsetRange[1] = null;
            }
            return [[b.ordinalRange[0], offsetRange[0]], [b.ordinalRange[1], offsetRange[1]]]
        }

        const filteredBookmarks = bookmarks.value.filter(b => rangeInside(
            bookmarkRange(b), [[startOrdinal, startOffset], [endOrdinal, endOffset]])
        );

        const deleteBookmarks = union(filteredBookmarks.map(b => b.id));

        const result = {bookInitials, startOrdinal, startOffset, endOrdinal, endOffset, bookmarks: deleteBookmarks};
        console.log("querySelection", result);
        return result;
    }

    window.bibleView.response = response;
    window.bibleView.emit = emit;
    window.bibleView.querySelection = querySelection

    async function deferredCall(func) {
        const promise = new Deferred();
        const thisCall = callId ++;
        responsePromises.set(thisCall, {func, promise});
        console.log("Calling async android function: ", func, thisCall);
        func(thisCall);
        const returnValue = await promise.wait();
        console.log("Response from async android function: ", thisCall, returnValue);
        return returnValue
    }

    async function requestPreviousChapter() {
        return await deferredCall((callId) => android.requestPreviousChapter(callId));
    }

    async function requestNextChapter() {
        return await deferredCall((callId) => android.requestNextChapter(callId));
    }

    function scrolledToVerse(ordinal) {
        android.scrolledToVerse(ordinal)
    }

    function saveBookmarkNote(bookmarkId, noteText) {
        android.saveBookmarkNote(bookmarkId, noteText);
    }

    function removeBookmark(bookmarkId) {
        android.removeBookmark(bookmarkId);
    }

    function assignLabels(bookmarkId) {
        android.assignLabels(bookmarkId);
    }

    function setClientReady() {
        android.setClientReady();
    }

    function reportInputFocus(value) {
        android.reportInputFocus(value);
    }

    function openExternalLink(link) {
        android.openExternalLink(link);
    }

    const exposed = {
        reportInputFocus,
        saveBookmarkNote,
        requestPreviousChapter,
        requestNextChapter,
        scrolledToVerse,
        setClientReady,
        querySelection,
        removeBookmark,
        assignLabels,
        openExternalLink,
    }

    if(config.developmentMode) return {
        ...stubsFor(exposed),
        querySelection
    }

    setupDocumentEventListener("selectionchange" , () => {
        if(window.getSelection().rangeCount > 0 && window.getSelection().getRangeAt(0).collapsed) {
            android.selectionCleared();
        }
    });

    onMounted(() => {
        setClientReady();
    });

    return exposed;
}
