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

import {onBeforeUnmount, onMounted, onUnmounted} from "@vue/runtime-core";

export function setupWindowEventListener(eventType, handler, options) {
    onMounted(() => window.addEventListener(eventType, handler, options))
    onUnmounted(() => window.removeEventListener(eventType, handler, options))
}

export function setupDocumentEventListener(eventType, handler, options) {
    onMounted(() => document.addEventListener(eventType, handler, options))
    onUnmounted(() => document.removeEventListener(eventType, handler, options))
}

export function setupElementEventListener(elementRef, eventType, handler, options) {
    onMounted(() => elementRef.value.addEventListener(eventType, handler, options))
    onBeforeUnmount(() => elementRef.value.removeEventListener(eventType, handler, options))
}

export function stubsFor(object) {
    const stubs = {};
    for(const key in object) {
        stubs[key] = (...args) => {
            console.log(`Stub for ${key}(${args}) called`)
        }
    }
    return stubs;
}

export function getVerseInfo(props) {
    if(!props.verseOrdinal) return null;
    const [book, chapter, verse] = props.osisID.split(".")
    return {ordinal: parseInt(props.verseOrdinal), osisID: props.osisID, book, chapter: parseInt(chapter), verse: parseInt(verse)}
}

export function setFrom(...args) {
    const set = new Set();
    args.forEach(v => set.add(v))
    return set;
}

export function addAll(set, ...args) {
    for(const a of args) {
        set.add(a);
    }
}

export function mapFrom(arr, keyFn, valueFn) {
    const map = new Map();
    arr.forEach(v => map.set(keyFn(v), valueFn(v)));
    return map;
}

export function arrayLeq([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return v12 <= v22;
    return false;
}

export function arrayGeq([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return v12 >= v22;
    return false;
}

export function arrayLe([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return v12 < v22;
    return false;
}

export function arrayGe([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return v12 > v22;
    return false;
}

export function arrayEq([v11, v12], [v21, v22]) {
    return (v11 === v21) && (v12 === v22)
}

export function rangesOverlap(bookmarkRange, testRange, {addRange = false, inclusive = false} = {}) {
    let rs, re, bs, be;
    if(addRange) {
        rs = [testRange[0], 0];
        re = [testRange[1], 0];
        bs = [bookmarkRange[0], 0];
        be = [bookmarkRange[1], 0];
    } else {
        [rs, re] = testRange;
        [bs, be] = bookmarkRange;
    }

    if(inclusive) {
        const ex1 = arrayLeq(rs, bs) && arrayLeq(bs, re);
        const ex2 = arrayLeq(rs, be) && arrayLeq(be, re);
        const ex4 = arrayLeq(bs, rs) && arrayLeq(rs, be) && arrayLeq(bs, re) && arrayLeq(re, be);

        return (ex1 || ex2 || ex4)
    } else {
        // Same condition as in kotlin side BookmarksDao.bookmarksForVerseRange
        const ex1 = arrayLeq(rs, bs) && arrayLe(bs, re);
        const ex2 = arrayLe(rs, be) && arrayLeq(be, re);
        const ex4 = arrayLeq(bs, rs) && arrayLe(rs, be) && arrayLe(bs, re) && arrayLeq(re, be);

        return (ex1 || ex2 || ex4)
    }
}

export class AutoSleep {
    constructor(processTime = 20, sleepTime = 0) {
        this._sleepTime = sleepTime;
        this._processTime = processTime;
        this.reset();
    }

    reset() {
        this.lastSleep = performance.now();
    }

    async autoSleep() {
        if(performance.now() - this.lastSleep > this._processTime) {
            // Give time for UI updates.
            await sleep(this._sleepTime);
            this.reset();
        }
    }
}


export function intersection(setA, setB) {
    let _intersection = new Set()
    for (let elem of setB) {
        if (setA.has(elem)) {
            _intersection.add(elem)
        }
    }
    return _intersection
}

export class Deferred {
    constructor() {
        this.promise = null;
        this.reset();
    }

    reset() {
        if(this.promise != null) {
            throw new Error("Previous promise is still there!");
        }
        this.promise = new Promise((resolve, reject) => {
            this._resolve = (...args) => {
                resolve(...args);
                this.promise = null;
                this._resolve = () => {};
                this._reject = () => {};
            };
            this._reject = (...args) => {
                reject(...args);
                this.promise = null;
                this._resolve = () => {};
                this._reject = () => {};
            }
        });
        this._waiting = false;
    }

    async wait() {
        if(this._waiting) {
            throw new Error("Multiple waits!");
        }

        if(this.promise) {
            this._waiting = true;
            return await this.promise;
        }
    }

    get isWaiting() {
        return this._waiting;
    }

    get isRunning() {
        return this.promise !== null;
    }

    resolve(...args) {
        this._resolve(...args);
    }

    reject(...args) {
        this._reject(...args);
    }
}

export async function sleep(ms) {
    await new Promise(resolve => setTimeout(resolve, ms));
}

