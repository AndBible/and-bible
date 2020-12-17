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

import {reactive, watch} from "@vue/runtime-core";
import {sortBy, uniqWith} from "lodash";
import {rangesOverlap} from "@/utils";
import highlightRange from "dom-highlight-range";
import {computed} from "@vue/reactivity";
import {calculateOffsetToVerse, findNodeAtOffset} from "@/dom";
import {Events, setupEventBusListener} from "@/eventbus";

export function useGlobalBookmarks({makeBookmark}) {
    const bookmarkLabels = reactive(new Map());
    const bookmarks = reactive(new Map());
    let count = 1;

    function updateBookmarkLabels(...inputData) {
        for(const v of inputData) {
            bookmarkLabels.set(v.id || -(count++), v.style)
        }
    }

    function updateBookmarks(...inputData) {
        for(const v of inputData) {
            bookmarks.set(v.id, v)
        }
    }

    async function makeBookmarkFromSelection() {
        const selection = window.getSelection();
        if(selection.rangeCount < 1) return;
        const range = selection.getRangeAt(0);

        const {ordinal: startOrdinal, offset: startOffset} =
            calculateOffsetToVerse(range.startContainer, range.startOffset, true);
        const {ordinal: endOrdinal, offset: endOffset} =
            calculateOffsetToVerse(range.endContainer, range.endOffset);

        selection.removeAllRanges();

        const fragmentId = range.startContainer.parentElement.closest(".fragment").id;
        const [bookInitials, bookOrdinals] = fragmentId.slice(2, fragmentId.length).split("--");

        updateBookmarks(await makeBookmark(bookInitials, startOrdinal, startOffset, endOrdinal, endOffset));
    }

    setupEventBusListener(Events.MAKE_BOOKMARK, makeBookmarkFromSelection)

    return {bookmarkLabels, bookmarks, updateBookmarkLabels, updateBookmarks, makeBookmarkFromSelection}
}

export function useBookmarks(props, {bookmarks, bookmarkLabels}, book) {
    function showBookmarkForWholeVerse(bookmark) {
        return bookmark.offsetRange === null || bookmark.book !== book
    }

    const noOrdinalNeeded = (b) => b.ordinalRange === null && props.ordinalRange === null
    const checkOrdinal = (b) =>
        b.ordinalRange !== null && props.ordinalRange !== null
        && rangesOverlap(b.ordinalRange, props.ordinalRange, true);

    const fragmentBookmarks = computed(() => {
        return Array.from(bookmarks.values()).filter(b => noOrdinalNeeded(b) || checkOrdinal(b));
    });

    const bookmarksForWholeVerse = computed(() => {
        return fragmentBookmarks.value.filter(b => showBookmarkForWholeVerse(b));
    });

    const accurateBookmarks = computed(() => {
        return fragmentBookmarks.value.filter(b => !showBookmarkForWholeVerse(b));
    });

    function combinedRange(b) {
        return [[b.ordinalRange[0], b.offsetRange[0]], [b.ordinalRange[1], b.offsetRange[1]] ]
    }

    const styleRanges = computed(() => {
        let splitPoints = [];
        const bookmarks = accurateBookmarks.value;

        for(const b of bookmarks) {
            splitPoints.push(combinedRange(b)[0])
            splitPoints.push(combinedRange(b)[1])
        }
        splitPoints = uniqWith(
            sortBy(splitPoints, [v => v[0], v => v[1]]),
            (v1, v2) => v1[0] === v2[0] && v1[1] === v2[1]
        );

        const styleRanges = [];

        for(let i = 0; i < splitPoints.length-1; i++) {
            const elementRange = [splitPoints[i], splitPoints[i+1]];
            const labels = new Set();
            const bookmarksSet = new Set();

            bookmarks
                .filter( b => rangesOverlap(combinedRange(b), elementRange))
                .forEach(b => {
                    bookmarksSet.add(b.id);
                    console.log(combinedRange(b), elementRange, rangesOverlap(combinedRange(b), elementRange));
                    b.labels.forEach(l => labels.add(l))
                });

            styleRanges.push({
                elementRange,
                labels: Array.from(labels),
                bookmarks: Array.from(bookmarksSet),
            });
        }
        return styleRanges.filter(v => v.labels.length > 0);
    })

    function styleForLabelIds(bookmarkLabelIds) {
        return styleForLabels(Array.from(bookmarkLabelIds).map(v => bookmarkLabels.get(v)));
    }

    function styleForLabels(bookmarkLabels) {
        let colors = [];
        for(const s of bookmarkLabels) {
            const c = `rgba(${s.color[0]}, ${s.color[1]}, ${s.color[2]}, 15%)`
            colors.push(c);
        }
        if(colors.length === 1) {
            colors.push(colors[0]);
        }
        const span = 100/colors.length;
        const colorStr = colors.map((v, idx) => {
            let percent;
            if (idx === 0) {
                percent = `${span}%`
            } else if (idx === colors.length - 1) {
                percent = `${span * (colors.length - 1)}%`
            } else {
                percent = `${span * idx}% ${span * (idx + 1)}%`
            }
            return `${v} ${percent}`;
        }).join(", ")

        return `background-image: linear-gradient(to bottom, ${colorStr})`;
    }

    const undoHighlights = [];

    function highlightStyleRange(styleRange) {
        const [[startOrdinal, startOff], [endOrdinal, endOff]] = styleRange.elementRange;
        const firstElem = document.querySelector(`#f-${props.fragmentKey} #v-${startOrdinal}`);
        const secondElem = document.querySelector(`#f-${props.fragmentKey} #v-${endOrdinal}`);
        console.log("styleRange", {styleRange, fragKey: props.fragmentKey, startOrdinal, endOrdinal, startOff, endOff, firstElem, secondElem});
        const [first, startOff1] = findNodeAtOffset(firstElem, startOff);
        const [second, endOff1] = findNodeAtOffset(secondElem, endOff);
        console.log("styleRange", {first, second});
        const range = new Range();
        range.setStart(first, startOff1);
        range.setEnd(second, endOff1);
        const style = styleForLabelIds(styleRange.labels)
        const undo = highlightRange(range, 'span', { style });
        undoHighlights.push(undo);
    }

    watch(styleRanges, (newValue) => {
        console.log("styleRanges changed!", accurateBookmarks.value, newValue);
        undoHighlights.forEach(v => {
            console.log("Running undo", v);
            v()
        })
        undoHighlights.splice(0);
        for (const s of newValue) {
            try {
                highlightStyleRange(s);
            } catch (e) {
                console.error("Error occurred", e);
            }
        }
    });

    return {bookmarksForWholeVerse, styleForLabels, styleRanges}
}
