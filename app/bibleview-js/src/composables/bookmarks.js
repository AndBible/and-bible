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

import {onMounted, reactive, watch} from "@vue/runtime-core";
import {sortBy, uniqWith} from "lodash";
import {intersection, rangesOverlap} from "@/utils";
import highlightRange from "dom-highlight-range";
import {computed, ref} from "@vue/reactivity";
import {findNodeAtOffset, textLength} from "@/dom";
import {Events, setupEventBusListener} from "@/eventbus";

export function useGlobalBookmarks(config) {
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
    setupEventBusListener(Events.REMOVE_RANGES, () => {
        window.getSelection().removeAllRanges();
    })

    setupEventBusListener(Events.DELETE_BOOKMARKS, (bookmarkIds) => {
        for(const bId of bookmarkIds) {
            bookmarks.delete(bId);
        }
    });

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARKS, ({bookmarks, labels}) => {
        updateBookmarkLabels(...labels)
        updateBookmarks(...bookmarks)
    });

    const filteredBookmarks = computed(() => {
        if(!config.showBookmarks) return [];
        const allBookmarks = Array.from(bookmarks.values());
        if(config.bookmarks.showAll) return allBookmarks;
        const configLabels = new Set(config.bookmarks.showLabels);
        return allBookmarks.filter(v => intersection(new Set(v.labels), configLabels).size > 0)
    })

    return {bookmarkLabels, bookmarks: filteredBookmarks, updateBookmarkLabels, updateBookmarks}
}

export function useBookmarks(fragmentKey, ordinalRange, {bookmarks, bookmarkLabels}, book, fragmentReady, config) {
    const isMounted = ref(0);
    onMounted(() => isMounted.value ++)

    function showBookmarkForWholeVerse(bookmark) {
        return bookmark.offsetRange === null || bookmark.book !== book
    }

    const noOrdinalNeeded = (b) => b.ordinalRange === null && ordinalRange === null
    const checkOrdinal = (b) =>
        b.ordinalRange !== null && ordinalRange !== null
        && rangesOverlap(b.ordinalRange, ordinalRange, true);

    const fragmentBookmarks = computed(() => {
        if(!fragmentReady.value) return [];
        return bookmarks.value.filter(b => noOrdinalNeeded(b) || checkOrdinal(b))
    });

    function combinedRange(b) {
        let offsetRange = b.offsetRange;
        if(showBookmarkForWholeVerse(b)) {
            const startOffset = 0;
            const verseElement = document.querySelector(`#f-${fragmentKey} #v-${b.ordinalRange[1]}`);
            const endOffset = textLength(verseElement);
            offsetRange = [startOffset, endOffset];
        }
        return [[b.ordinalRange[0], offsetRange[0]], [b.ordinalRange[1], offsetRange[1]] ]
    }


    const styleRanges = computed(() => {
        if(!isMounted.value) return [];

        let splitPoints = [];
        const bookmarks = fragmentBookmarks.value;

        for(const b of bookmarks) {
            splitPoints.push(combinedRange(b)[0])
            splitPoints.push(combinedRange(b)[1])
        }
        splitPoints = uniqWith(
            sortBy(splitPoints, [v => v[0], v => v[1]]),
            (v1, v2) => v1[0] === v2[0] && v1[1] === v2[1]
        );

        const styleRanges = [];

        const labelsSet = new Set(config.bookmarks.showLabels);

        function filterLabels(labels) {
            if(config.bookmarks.showAll) return labels;
            return intersection(labelsSet, new Set(labels));
        }

        for(let i = 0; i < splitPoints.length-1; i++) {
            const ordinalAndOffsetRange = [splitPoints[i], splitPoints[i+1]];
            const labels = new Set();
            const bookmarksSet = new Set();

            bookmarks
                .filter( b => rangesOverlap(combinedRange(b), ordinalAndOffsetRange))
                .forEach(b => {
                    bookmarksSet.add(b.id);
                    filterLabels(b.labels).forEach(l => labels.add(l))
                });

            styleRanges.push({
                ordinalAndOffsetRange,
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
            const c = `rgba(${s.color[0]}, ${s.color[1]}, ${s.color[2]}, 40%)`
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
        const [[startOrdinal, startOff], [endOrdinal, endOff]] = styleRange.ordinalAndOffsetRange;
        const firstElem = document.querySelector(`#f-${fragmentKey} #v-${startOrdinal}`);
        const secondElem = document.querySelector(`#f-${fragmentKey} #v-${endOrdinal}`);
        const [first, startOff1] = findNodeAtOffset(firstElem, startOff);
        const [second, endOff1] = findNodeAtOffset(secondElem, endOff);
        const range = new Range();
        range.setStart(first, startOff1);
        range.setEnd(second, endOff1);
        const style = styleForLabelIds(styleRange.labels)
        const undo = highlightRange(range, 'span', { style });
        undoHighlights.push(undo);
    }

    watch(styleRanges, (newValue) => {
        console.log("styleRanges changed!", fragmentBookmarks.value, newValue);
        undoHighlights.forEach(v => v())
        undoHighlights.splice(0);
        for (const s of newValue) {
            try {
                highlightStyleRange(s);
            } catch (e) {
                console.error("Error occurred", e);
            }
        }
    }, {flush: 'post'});
}
