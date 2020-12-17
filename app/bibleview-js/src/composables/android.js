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
import {Deferred} from "@/code/utils";
import {setupDocumentEventListener, setupWindowEventListener, stubsFor} from "@/utils";
import {onMounted} from "@vue/runtime-core";
import {calculateOffsetToVerse} from "@/dom";

let callId = 0;

export function useAndroid() {
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
        if(selection.rangeCount < 1) return;
        const range = selection.getRangeAt(0);

        const {ordinal: startOrdinal, offset: startOffset} =
            calculateOffsetToVerse(range.startContainer, range.startOffset, true);
        const {ordinal: endOrdinal, offset: endOffset} =
            calculateOffsetToVerse(range.endContainer, range.endOffset);

        const fragmentId = range.startContainer.parentElement.closest(".fragment").id;
        const [bookInitials, bookOrdinals] = fragmentId.slice(2, fragmentId.length).split("--");

        const returnValue = {bookInitials, startOrdinal, startOffset, endOrdinal, endOffset};
        console.log("Querying selection: ", returnValue);
        return returnValue
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

    async function requestMoreTextAtTop() {
        return await deferredCall((callId) => android.requestMoreTextAtTop(callId));
    }

    async function requestMoreTextAtEnd() {
        return await deferredCall((callId) => android.requestMoreTextAtEnd(callId));
    }

    function scrolledToVerse(ordinal) {
        android.scrolledToVerse(ordinal)
    }

    function setClientReady() {
        android.setClientReady();
    }

    const exposed = {requestMoreTextAtTop, requestMoreTextAtEnd, scrolledToVerse, setClientReady, querySelection}

    let lblCount = 0;
    if(process.env.NODE_ENV === 'development') return {
        ...stubsFor(exposed),
        querySelection
    }

    setupDocumentEventListener("selectionchange" , () => {
        if(window.getSelection().getRangeAt(0).collapsed) {
            android.selectionCleared();
        }
    });

    onMounted(() => {
        setClientReady();
    });

    return exposed;
}
