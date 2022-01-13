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

import {onBeforeUnmount, onMounted, onUnmounted, watch} from "@vue/runtime-core";
import Color from "color";
import {rybColorMixer} from "@/lib/ryb-color-mixer";
import {get, sortBy} from "lodash";
import {highlightRange} from "@/lib/highlight-range";
import {findNodeAtOffset, lastTextNode} from "@/dom";
import {sprintf as sprintfOrig} from "sprintf-js";

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

export function stubsFor(object, defaults={}) {
    const stubs = {};
    for(const key in object) {
        //Implement a separate stub for getActiveLanguages, since it needs to return data
        if (key in defaults) {
            let value = defaults[key]
            if (typeof value != "function") {
                value = () => {
                    return defaults[key]
                }
            }
            stubs[key] = value
        } else {
            stubs[key] = (...args) => {
                console.log(`Stub for ${key}(${args}) called`)
            }
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

export function removeAll(set, ...args) {
    for(const a of args) {
        set.delete(a);
    }
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

function nm(v) {
    if(v === null) {
        return Number.MAX_VALUE
    } else return v;
}

export function arrayLeq([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return nm(v12) <= nm(v22);
    return false;
}

export function arrayGeq([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return nm(v12) >= nm(v22);
    return false;
}

export function arrayLe([v11, v12], [v21, v22]) {
    if(v11 < v21) return true
    if(v11 === v21) return nm(v12) < nm(v22);
    return false;
}

export function arrayGe([v11, v12], [v21, v22]) {
    if(v11 > v21) return true
    if(v11 === v21) return nm(v12) > nm(v22);
    return false;
}

export function arrayEq([v11, v12], [v21, v22]) {
    return (v11 === v21) && (v12 === v22)
}

export function rangeInside(range, testRange) {
    const [rs, re] = testRange;
    const [bs, be] = range;

    const ex1 = arrayLeq(rs, bs) && arrayLeq(bs, re);
    const ex2 = arrayLeq(rs, be) && arrayLeq(be, re);

    return ex1 && ex2
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

export function difference(setA, setB) {
    let _difference = new Set(setA)
    for (let elem of setB) {
        _difference.delete(elem)
    }
    return _difference
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
    if(ms < 0) return new Promise(() => {});
    await new Promise(resolve => setTimeout(resolve, ms));
}

export async function waitNextAnimationFrame() {
    const defer = new Deferred();
    window.requestAnimationFrame(() => defer.resolve())
    await defer.wait();
}

export function cancellableTimer(ms) {
    if(ms < 0) return [new Promise(() => {}), () => {}];
    let cancel = false;
    const promise = new Promise(resolve => setTimeout(() => {
        if(!cancel) {
            resolve()
        }
    }, ms));
    return [promise, () => cancel = true];

}

export function mixColors(...colors) {
    const hexColors = colors.map(v=>v.rgb().hex());
    const mixed = rybColorMixer.mix(...hexColors, {result: "ryb", hex: true});
    return Color.rgb("#"+mixed);
}

export function colorLightness(color) {
    // YIQ equation from Color.isDark()
    const rgb = color.rgb().color;
    const yiq = (rgb[0] * 299 + rgb[1] * 587 + rgb[2] * 114) / 1000;
    return yiq / 255;
}

export function addEventVerseInfo(event, verseInfo) {
    event.verseInfo = verseInfo;
}

export function getEventVerseInfo(event) {
    return event.verseInfo || null;
}

export const EventPriorities = {
    HIDDEN_BOOKMARK: 0,
    VISIBLE_BOOKMARK: 0,
    BOOKMARK_MARKER: 5,
    FOOTNOTE: 15,
    STRONGS_DOTTED: 5,

    // "link-style"
    EXTERNAL_LINK: 10,
    REFERENCE: 10,
    STRONGS_LINK: 10,
}

export function addEventFunction(event, callback, options) {
    if(!event.eventFunctions)
        event.eventFunctions = {};
    const priority = get(options, "priority", 0);
    let array = event.eventFunctions[priority];
    if(!array) {
        array = [];
        event.eventFunctions[priority] = array;
    }
    array.push({callback, options});
}

export function getHighestPriorityEventFunctions(event) {
    if(!event.eventFunctions) return [];
    const priorities = Object.keys(event.eventFunctions);
    priorities.sort();
    return event.eventFunctions[priorities[priorities.length -1]];
}

export function getAllEventFunctions(event) {
    if(!event.eventFunctions) return [];
    const all = [];
    for(const [, items] of Object.entries(event.eventFunctions)) {
        all.push(...items);
    }
    return sortBy(all, [v => -v.options.priority, v => v.options.title]);
}

export function draggableElement(element, dragHandle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;

    dragHandle.addEventListener("touchstart", dragMouseDown, {passive: false});

    function dragMouseDown(e) {
        e.preventDefault();
        const touch = e.touches[0];
        pos3 = touch.clientX;
        pos4 = touch.clientY;
        document.addEventListener("touchend", closeDragElement);
        document.addEventListener("touchmove", elementDrag);
    }

    function elementDrag(e) {
        const touch = e.touches[0];
        pos1 = pos3 - touch.clientX;
        pos2 = pos4 - touch.clientY;
        pos3 = touch.clientX;
        pos4 = touch.clientY;
        element.style.top = (element.offsetTop - pos2) + "px";
        element.style.left = (element.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
        document.removeEventListener("touchend", closeDragElement);
        document.removeEventListener("touchmove", elementDrag);
    }
}

export function stripTags(str) {
    const temp = document.createElement("div");
    temp.innerHTML = str;
    return temp.textContent || temp.innerText;
}

export function osisToTemplateString(osis) {
    return osis
        .replace(/(<\/?)(\w)(\w*)([^>]*>)/g,
            (m, tagStart, tagFirst, tagRest, tagEnd) =>
                `${tagStart}Osis${tagFirst.toUpperCase()}${tagRest}${tagEnd}`);
}

export function findNodeAtOffsetWithNullOffset(elem, offset) {
    let node, off;
    if (offset === null) {
        node = lastTextNode(elem, true);
        off = node.length;
    } else {
        [node, off] = findNodeAtOffset(elem, offset);
    }
    return [node, off];
}

// Bit generalized version from bookmarks:highlightStyleRange
export function highlightVerseRange(selectorPrefix, [startOrdinal, endOrdinal], [startOff, endOff] = [0, null]) {
    const firstElem = document.querySelector(`${selectorPrefix} #v-${startOrdinal}`);
    const secondElem = document.querySelector(`${selectorPrefix} #v-${endOrdinal}`);
    if (firstElem === null || secondElem === null) {
        console.error("Element is not found!", {selectorPrefix, startOrdinal, endOrdinal});
        return;
    }
    const [first, startOff1] = findNodeAtOffsetWithNullOffset(firstElem, startOff);
    const [second, endOff1] = findNodeAtOffsetWithNullOffset(secondElem, endOff);

    if(!(first instanceof Node && second instanceof Node)) {
        console.error("Node not found!");
        return;
    }

    const range = new Range();
    range.setStart(first, startOff1);
    range.setEnd(second, endOff1);
    const highlightResult = highlightRange(range, 'span', {class: "highlight"});
    if (highlightResult) {
        return highlightResult.undo;
    } else {
        console.error("Highlight range failed!", {first,second,firstElem,secondElem,startOff,endOff,startOff1,endOff1})
    }
}

export function isInViewport(el) {
    const rect = el.getBoundingClientRect();
    return (
        rect.top <= window.innerHeight &&
        rect.bottom >= 0
    );
}
export function adjustedColorOrig(color, ratio=0.2) {
    let col = Color(color);
    let cont = true;
    let rv;
    let loops = 0;
    while(cont) {
        cont = false;
        rv = col.darken(ratio);
        if((rv.hex() === "#FFFFFF" || rv.hex() === "#000000") && ++loops < 5) {
            ratio = 0.75*ratio;
            cont = true
        }
    }
    return rv;
}

export function adjustedColor(color, ratio=0.2) {
    return adjustedColorOrig(color, ratio).hsl();
}

export function clickWaiter(handleTouch = true) {
    let clickDeferred = null;

    async function waitForClick(event) {
        if(event.type === "touchstart" && !handleTouch) {
            return false;
        }
        event.stopPropagation();
        if(handleTouch) {
            if (event.type === "click") {
                if (clickDeferred) {
                    clickDeferred.resolve();
                    clickDeferred = null;
                } else {
                    console.error("Deferred not found");
                }
                return false;
            } else if (event.type === "touchstart") {
                clickDeferred = new Deferred();
                await clickDeferred.wait();
                return true;
            }
        } else {
            return true;
        }
    }
    return {waitForClick}
}

export function createDoubleClickDetector(waitMs = 300) {
    let counter = 0;
    async function isDoubleClick() {
        counter ++;
        if(counter > 1) return true;
        await sleep(waitMs);
        const manyClicks = counter > 1
        // eslint-disable-next-line require-atomic-updates
        counter = 0;
        return manyClicks;
    }
    return {isDoubleClick};
}

export function isBottomHalfClicked(event) {
    return event.clientY > (window.innerHeight / 2);
}

export async function waitUntilRefValue(ref_) {
    return await new Promise(resolve => {
        if (ref_.value) {
            resolve(ref_.value);
            return;
        }
        const stop = watch(ref_, newValue => {
            if (newValue) {
                stop();
                resolve(newValue);
            }
        });
    });
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
