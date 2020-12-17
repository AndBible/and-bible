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
import {stubsFor} from "@/utils";
import {onMounted} from "@vue/runtime-core";

export function useAndroid() {
    let callId = 0;
    const responsePromises = new Map();

    function response(callId, returnValue) {
        const promise = responsePromises.get(callId);
        if(promise) {
            responsePromises.delete(callId);
            promise.resolve(returnValue);
        } else {
            console.error("Promise not found for callId", callId)
        }
    }

    window.bibleView.response = response;
    window.bibleView.emit = emit;

    async function deferredCall(func, ...args) {
        const promise = new Deferred();
        const thisCall = callId ++;
        responsePromises.set(thisCall, promise);
        console.log("Calling function", func, thisCall, args);
        func(thisCall, ...args);
        const returnValue = await promise.wait();
        console.log("Response came to", thisCall, args);
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
    const exposed = {requestMoreTextAtTop, requestMoreTextAtEnd, scrolledToVerse, setClientReady}

    if(process.env.NODE_ENV === 'development') return stubsFor(exposed)

    onMounted(() => {
        setClientReady();
    });

    return exposed;
}
