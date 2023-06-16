/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {onBeforeUnmount, onMounted, onUnmounted, Ref, watch} from "vue";
import Color from "color";
import {rybColorMixer} from "@/lib/ryb-color-mixer";
import {get, sortBy} from "lodash";
import {highlightRange} from "@/lib/highlight-range";
import {findNodeAtOffset, lastTextNode} from "./dom";
import {sprintf as sprintfOrig} from "sprintf-js";
import {CombinedRange, NumberRange, OffsetRange, OrdinalOffset, OrdinalRange} from "@/types/client-objects";
import {BibleDocumentInfo, ColorParam, Nullable, VerseInfo} from "@/types/common";

type EventHandler = ((event: any) => void) | (() => void)

export function setupWindowEventListener(
    eventType: string,
    handler: EventHandler,
    options: AddEventListenerOptions | undefined | boolean = undefined
) {
    onMounted(() => window.addEventListener(eventType, handler, options))
    onUnmounted(() => window.removeEventListener(eventType, handler, options))
}

export function setupDocumentEventListener(
    eventType: string,
    handler: EventHandler,
    options: AddEventListenerOptions | undefined | boolean = undefined
) {
    onMounted(() => document.addEventListener(eventType, handler, options))
    onUnmounted(() => document.removeEventListener(eventType, handler, options))
}

export function setupElementEventListener(
    elementRef: Ref<HTMLElement | null>,
    eventType: string,
    handler: EventHandler,
    options: AddEventListenerOptions | undefined | boolean = undefined
) {
    onMounted(() => elementRef.value!.addEventListener(eventType, handler, options))
    onBeforeUnmount(() => elementRef.value!.removeEventListener(eventType, handler, options))
}

export function stubsFor(object: Record<string, any>, defaults: Record<string, any> = {}) {
    const stubs: Record<string, any> = {};
    for (const key in object) {
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
            stubs[key] = (...args: any[]) => {
                console.log(`Stub for ${key}(${args}) called`)
            }
        }
    }
    return stubs;
}

export function getVerseInfo(props: { osisID: string, verseOrdinal: string }) {
    const [book, chapter, verse] = props.osisID.split(".")
    return {
        ordinal: parseInt(props.verseOrdinal),
        osisID: props.osisID,
        book,
        chapter: parseInt(chapter),
        verse: parseInt(verse)
    }
}

export function setFrom<T>(...args: T[]) {
    const set = new Set();
    args.forEach(v => set.add(v))
    return set;
}

export function removeAll<T>(set: Set<T>, ...args: T[]) {
    for (const a of args) {
        set.delete(a);
    }
}

export function addAll<T>(set: Set<T>, ...args: T[]) {
    for (const a of args) {
        set.add(a);
    }
}

export function mapFrom<T, K, V>(arr: T[], keyFn: (v: T) => K, valueFn: (v: T) => V) {
    const map = new Map();
    arr.forEach(v => map.set(keyFn(v), valueFn(v)));
    return map;
}

function nm(v: Nullable<number>): number {
    if (v === null) {
        return Number.MAX_VALUE
    } else return v;
}

export function arrayLeq([v11, v12]: OrdinalOffset, [v21, v22]: OrdinalOffset) {
    if (v11 < v21) return true
    if (v11 === v21) return nm(v12) <= nm(v22);
    return false;
}

export function arrayGeq([v11, v12]: OrdinalOffset, [v21, v22]: OrdinalOffset) {
    if (v11 > v21) return true
    if (v11 === v21) return nm(v12) >= nm(v22);
    return false;
}

export function arrayLe([v11, v12]: OrdinalOffset, [v21, v22]: OrdinalOffset) {
    if (v11 < v21) return true
    if (v11 === v21) return nm(v12) < nm(v22);
    return false;
}

export function arrayGe([v11, v12]: OrdinalOffset, [v21, v22]: OrdinalOffset) {
    if (v11 > v21) return true
    if (v11 === v21) return nm(v12) > nm(v22);
    return false;
}

export function arrayEq([v11, v12]: OrdinalOffset, [v21, v22]: OrdinalOffset) {
    return (v11 === v21) && (v12 === v22)
}

export function rangeInside(range: CombinedRange, testRange: CombinedRange) {
    const [rs, re] = testRange;
    const [bs, be] = range;

    const ex1 = arrayLeq(rs, bs) && arrayLeq(bs, re);
    const ex2 = arrayLeq(rs, be) && arrayLeq(be, re);

    return ex1 && ex2
}


export function rangesOverlap(
    bookmarkRange: OrdinalRange | CombinedRange,
    testRange: OrdinalRange | CombinedRange,
    {
        addRange = false,
        inclusive = false
    } = {}
) {
    let rs: OrdinalOffset, re: OrdinalOffset, bs: OrdinalOffset, be: OrdinalOffset;
    if (addRange) {
        const tr = testRange as OrdinalRange
        const br = bookmarkRange as OrdinalRange

        rs = [tr[0], 0];
        re = [tr[1], 0];
        bs = [br[0], 0];
        be = [br[1], 0];
    } else {
        const tr = testRange as CombinedRange
        const br = bookmarkRange as CombinedRange

        [rs, re] = tr;
        [bs, be] = br;
    }

    //https://stackoverflow.com/questions/325933/determine-whether-two-date-ranges-overlap
    if (inclusive) {
        // Same condition as in kotlin side BookmarksDao.bookmarksForVerseRange
        return arrayLeq(rs, be) && arrayLeq(bs, re);
    } else {
        return arrayLe(rs, be) && arrayLe(bs, re);
    }
}

export class AutoSleep {
    _sleepTime: number
    _processTime: number
    lastSleep: number = 0

    constructor(processTime = 20, sleepTime = 0) {
        this._sleepTime = sleepTime;
        this._processTime = processTime;
        this.reset();
    }

    reset() {
        this.lastSleep = performance.now();
    }

    async autoSleep() {
        if (performance.now() - this.lastSleep > this._processTime) {
            // Give time for UI updates.
            await sleep(this._sleepTime);
            this.reset();
        }
    }
}


export function intersection<T>(setA: Set<T>, setB: Set<T>): Set<T> {
    const _intersection: Set<T> = new Set()
    for (const elem of setB) {
        if (setA.has(elem)) {
            _intersection.add(elem)
        }
    }
    return _intersection
}

export function difference<T>(setA: Set<T>, setB: Set<T>): Set<T> {
    const _difference: Set<T> = new Set(setA);
    for (const elem of setB) {
        _difference.delete(elem)
    }
    return _difference
}

export class Deferred<T = undefined> {
    _promise: Promise<T | undefined>
    _waiting: boolean
    _resolve: (arg?: T) => void
    _reject: (arg?: T) => void
    _resolution?: T

    constructor() {
        this._resolve = () => {
        };
        this._reject = () => {
        };
        this._promise = this.getNewPromise()
        this._waiting = false
    }

    reset() {
        if (this._promise != null) {
            throw new Error("Previous promise is still there!");
        }
        this._promise = this.getNewPromise()
        this._waiting = false;
    }

    getNewPromise(): Promise<T | undefined> {
        return new Promise((resolve, reject) => {
            this._resolve = (arg) => {
                resolve(arg);
                this._resolve = () => {
                };
                this._reject = () => {
                };
            };
            this._reject = (args) => {
                reject(args);
                this._resolve = () => {
                };
                this._reject = () => {
                };
            }
        });
    }

    async wait(): Promise<T | undefined> {
        if (this._waiting) {
            throw new Error("Multiple waits!");
        }

        this._waiting = true;
        return await this._promise;
    }

    get isWaiting() {
        return this._waiting;
    }

    resolve(arg?: T) {
        this._resolve(arg);
    }

    reject(arg?: T) {
        this._reject(arg);
    }
}

export async function sleep(ms: number) {
    if (ms < 0) return new Promise(() => {
    });
    await new Promise(resolve => setTimeout(resolve, ms));
}

export async function waitNextAnimationFrame() {
    const defer = new Deferred();
    window.requestAnimationFrame(() => defer.resolve())
    await defer.wait();
}

export function cancellableTimer(ms: number) {
    if (ms < 0) return [new Promise(() => {
    }), () => {
    }];
    let cancel = false;
    const promise = new Promise(resolve => setTimeout(() => {
        if (!cancel) {
            resolve(undefined)
        }
    }, ms));
    return [promise, () => cancel = true];

}

export function mixColors(...colors: Color[]) {
    const hexColors = colors.map(v => v.rgb().hex());
    const mixed = rybColorMixer.mix(...hexColors, {result: "ryb", hex: true});
    return Color.rgb("#" + mixed);
}

export function colorLightness(color: Color) {
    // YIQ equation from Color.isDark()
    const rgb = color.rgb().array();
    const yiq = (rgb[0] * 299 + rgb[1] * 587 + rgb[2] * 114) / 1000;
    return yiq / 255;
}

export type EventVerseInfo = VerseInfo & {
    bookInitials: string
    bibleBookName: string
    bibleDocumentInfo?: BibleDocumentInfo
    verseTo?: string
}

type EventWithVerseInfo = Event & {
    verseInfo?: EventVerseInfo
}

export function addEventVerseInfo(event: EventWithVerseInfo, verseInfo: EventVerseInfo) {
    event.verseInfo = verseInfo;
}

export function getEventVerseInfo(event: EventWithVerseInfo): Nullable<EventVerseInfo> {
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

type CallbackFunc = () => void
type CallbackOpts = Record<string, any> & {
    priority: number
}
export type Callback = {
    type: "callback"
    callback: Nullable<(() => void)>
    options: CallbackOpts
}

type EventWithEventFunctions = Event & {
    eventFunctions?: Record<string, Callback[]>
}

export function addEventFunction(event: EventWithEventFunctions, callback: Nullable<CallbackFunc>, options: CallbackOpts) {
    if (!event.eventFunctions)
        event.eventFunctions = {};
    const priority = get(options, "priority", 0);
    let array = event.eventFunctions[priority];
    if (!array) {
        array = [];
        event.eventFunctions[priority] = array;
    }
    array.push({type: "callback", callback, options});
}

export function getHighestPriorityEventFunctions(event: EventWithEventFunctions): Callback[] {
    if (!event.eventFunctions) return [];
    const priorities = Object.keys(event.eventFunctions);
    priorities.sort();
    return event.eventFunctions[priorities[priorities.length - 1]];
}

export function getAllEventFunctions(event: EventWithEventFunctions): Callback[] {
    if (!event.eventFunctions) return [];
    const all = [];
    for (const [, items] of Object.entries(event.eventFunctions)) {
        all.push(...items);
    }
    return sortBy(all, [v => -v.options.priority, v => v.options.title]);
}

export function draggableElement(element: HTMLElement, dragHandle: HTMLElement) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;

    dragHandle.addEventListener("touchstart", dragTouchStart, {passive: false});
    dragHandle.addEventListener("mousedown", dragMouseDown, {passive: false});

    function dragMouseDown(e: MouseEvent) {
        e.preventDefault();
        pos3 = e.clientX;
        pos4 = e.clientY;
        document.addEventListener("mouseup", closeMouseDragElement);
        document.addEventListener("mousemove", elementDragMouseMove);
    }

    function dragTouchStart(e: TouchEvent) {
        e.preventDefault();
        const touch = e.touches[0];
        pos3 = touch.clientX;
        pos4 = touch.clientY;
        document.addEventListener("touchend", closeDragElement);
        document.addEventListener("touchmove", elementDragTouch);
    }

    function elementDragMouseMove(e: MouseEvent) {
        pos1 = pos3 - e.clientX;
        pos2 = pos4 - e.clientY;
        pos3 = e.clientX;
        pos4 = e.clientY;
        element.style.top = (element.offsetTop - pos2) + "px";
        element.style.left = (element.offsetLeft - pos1) + "px";
    }

    function elementDragTouch(e: TouchEvent) {
        const touch = e.touches[0];
        pos1 = pos3 - touch.clientX;
        pos2 = pos4 - touch.clientY;
        pos3 = touch.clientX;
        pos4 = touch.clientY;
        element.style.top = (element.offsetTop - pos2) + "px";
        element.style.left = (element.offsetLeft - pos1) + "px";
    }

    function closeMouseDragElement() {
        document.removeEventListener("mouseup", closeMouseDragElement);
        document.removeEventListener("mousemove", elementDragMouseMove);
    }

    function closeDragElement() {
        document.removeEventListener("touchend", closeDragElement);
        document.removeEventListener("touchmove", elementDragTouch);
    }
}

export function stripTags(str: string) {
    const temp = document.createElement("div");
    temp.innerHTML = str;
    return temp.textContent || temp.innerText;
}

export function osisToTemplateString(osis: string) {
    return osis
        .replace(/(<\/?)(\w)(\w*)([^>]*>)/g,
            (m, tagStart, tagFirst, tagRest, tagEnd) =>
                `${tagStart}Osis${tagFirst.toUpperCase()}${tagRest}${tagEnd}`);
}

type TextNodeAndOffset = { node: Text, offset: number }

export function findNodeAtOffsetWithNullOffset(elem: Element, offset: Nullable<number>): Nullable<TextNodeAndOffset> {
    let
        node: Nullable<Text>,
        off: Nullable<number>;

    if (offset === null) {
        node = lastTextNode(elem);
        off = node.length;
    } else {
        [node, off] = findNodeAtOffset(elem, offset);
    }
    if (!node || off === null) return null
    return {node, offset: off};
}

// Bit generalized version from bookmarks:highlightStyleRange
export function highlightVerseRange(
    selectorPrefix: string,
    [startOrdinal, endOrdinal]: NumberRange,
    [startOff, endOff]: OffsetRange = [0, null]
) {
    const firstElem = document.querySelector(`${selectorPrefix} #v-${startOrdinal}`) as HTMLElement;
    const secondElem = document.querySelector(`${selectorPrefix} #v-${endOrdinal}`) as HTMLElement;
    if (firstElem === null || secondElem === null) {
        console.error("Element is not found!", {selectorPrefix, startOrdinal, endOrdinal});
        return;
    }
    const first = findNodeAtOffsetWithNullOffset(firstElem, startOff);
    const second = findNodeAtOffsetWithNullOffset(secondElem, endOff);

    if (!(first && second)) {
        console.error("Node not found!");
        return;
    }

    const range = new Range();
    range.setStart(first.node, first.offset);
    range.setEnd(second.node, second.offset);
    const highlightResult = highlightRange(range, 'span', {class: "highlight"});
    if (highlightResult) {
        return highlightResult.undo;
    } else {
        console.error("Highlight range failed!", {first, second, firstElem, secondElem, startOff, endOff})
    }
}

export function isInViewport(el: Element) {
    const rect = el.getBoundingClientRect();
    return (
        rect.top <= window.innerHeight &&
        rect.bottom >= 0
    );
}

export function adjustedColorOrig(color: ColorParam, ratio = 0.2): Color {
    const col = Color(color);
    let cont = true;
    let rv: Color;
    let loops = 0;
    while (cont) {
        cont = false;
        rv = col.darken(ratio);
        if ((rv.hex() === "#FFFFFF" || rv.hex() === "#000000") && ++loops < 5) {
            ratio = 0.75 * ratio;
            cont = true
        }
    }
    return rv!;
}

export function adjustedColor(color: ColorParam, ratio = 0.2) {
    return adjustedColorOrig(color, ratio).hsl();
}

export function clickWaiter(handleTouch = true) {
    let clickDeferred: Nullable<Deferred> = null;

    async function waitForClick(event: MouseEvent | TouchEvent) {
        if ((event.type === "touchstart" || event.type === "mousedown") && !handleTouch) {
            return false;
        }
        event.stopPropagation();
        if (handleTouch) {
            if (event.type === "click") {
                if (clickDeferred) {
                    clickDeferred.resolve();
                    clickDeferred = null;
                } else {
                    console.error("Deferred not found");
                }
                return false;
            } else if (event.type === "touchstart" || event.type === "mousedown") {
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
        counter++;
        if (counter > 1) return true;
        await sleep(waitMs);
        const manyClicks = counter > 1
        // eslint-disable-next-line require-atomic-updates
        counter = 0;
        return manyClicks;
    }

    return {isDoubleClick};
}

export function isBottomHalfClicked(event: MouseEvent) {
    return event.clientY > (window.innerHeight / 2);
}

export async function waitUntilRefValue(ref_: Ref) {
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

export function abbreviated(str: string, n: number, useWordBoundary = true) {
    if (!str) return ""
    if (str.length <= n) {
        return str;
    }
    const subString = str.substr(0, n - 1); // the original check
    let splitPoint = subString.lastIndexOf(" ");
    if (splitPoint <= 0) {
        splitPoint = n - 1;
    }
    return (useWordBoundary
        ? subString.substr(0, splitPoint)
        : subString) + "...";
}


export function sprintf(format: string, ...args: any[]) {
    return sprintfOrig(format, ...args);
}

export function formatExportLink({ref, v11n, doc}: {ref: string, v11n: string, doc?: string}) {
    const docStr = doc ? `&document=${doc}`: ""
    return `https://stepbible.org/?q=reference=${ref}&v11n=${v11n}${docStr}`
}