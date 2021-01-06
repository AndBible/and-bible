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

import {onMounted, onUnmounted, reactive, watch} from "@vue/runtime-core";
import {cloneDeep, sortBy, uniqWith} from "lodash";
import {arrayEq, intersection, rangesOverlap, toRgba} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {findNodeAtOffset, textLength} from "@/dom";
import {Events, setupEventBusListener, emit} from "@/eventbus";
import {highlightRange} from "@/highlight-range";
import {faEdit, faBookmark} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import Color from "color";

const editIcon = icon(faEdit);
const bookmarkIcon = icon(faBookmark);

const allStyleRangeArrays = reactive(new Set());
const allStyleRanges = computed(() => {
    const allStyles = [];
    for(const a of allStyleRangeArrays) {
        allStyles.push(...a.value);
    }
    return allStyles;
});
const combinedRangeCache = new Map();

export function useGlobalBookmarks(config) {
    const bookmarkLabels = reactive(new Map());
    const bookmarks = reactive(new Map());
    let count = 1;

    const labelsUpdated = ref(0);

    function updateBookmarkLabels(...inputData) {
        if(!inputData.length) return
        for(const v of inputData) {
            bookmarkLabels.set(v.id || -(count++), v.style)
        }
        labelsUpdated.value ++;
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

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARKS, ({bookmarks = [], labels = []} = {}) => {
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

    window.bibleViewDebug.bookmarks = bookmarks;
    window.bibleViewDebug.allStyleRanges = allStyleRanges;

    return {
        bookmarkLabels, bookmarkMap: bookmarks, bookmarks: filteredBookmarks, labelsUpdated,
        updateBookmarkLabels, updateBookmarks, allStyleRanges
    }
}

export function useBookmarks(fragmentKey, ordinalRange, {bookmarks, bookmarkMap, bookmarkLabels, labelsUpdated}, book, fragmentReady, config) {
    const isMounted = ref(0);
    onMounted(() => isMounted.value ++)

    function showBookmarkForWholeVerse(bookmark) {
        return bookmark.offsetRange === null || bookmark.book !== book
    }

    const noOrdinalNeeded = (b) => b.ordinalRange === null && ordinalRange === null
    const checkOrdinal = (b) => {
        return b.ordinalRange !== null && ordinalRange !== null
        && rangesOverlap(b.ordinalRange, ordinalRange, {addRange: true, inclusive: true})
    };

    const fragmentBookmarks = computed(() => {
        if(!fragmentReady.value) return [];
        return bookmarks.value.filter(b => noOrdinalNeeded(b) || checkOrdinal(b))
    });

    function truncateToOrdinalRange(bookmark) {
        const b = cloneDeep(bookmark);
        b.offsetRange = b.offsetRange || [0, null]
        if(b.ordinalRange[0] < ordinalRange[0]) {
            b.ordinalRange[0] = ordinalRange[0];
            b.offsetRange[0] = 0;
        }
        if(b.ordinalRange[1] > ordinalRange[1]) {
            b.ordinalRange[1] = ordinalRange[1];
            const verseElement = document.querySelector(`#f-${fragmentKey} #v-${ordinalRange[1]}`);
            b.offsetRange[1] = textLength(verseElement);
        }
        if(b.offsetRange[1] == null) {
            const verseElement = document.querySelector(`#f-${fragmentKey} #v-${b.ordinalRange[1]}`);
            b.offsetRange[1] = textLength(verseElement);
        }
        return b;
    }
    function combinedRange(b) {
        let cached = combinedRangeCache.get(b.id);
        if(!cached) {
            b = truncateToOrdinalRange(b);
            let offsetRange = b.offsetRange;
            if (showBookmarkForWholeVerse(b)) {
                const startOffset = 0;
                const verseElement = document.querySelector(`#f-${fragmentKey} #v-${b.ordinalRange[1]}`);
                const endOffset = textLength(verseElement);
                offsetRange = [startOffset, endOffset];
            }

            cached = [[b.ordinalRange[0], offsetRange[0]], [b.ordinalRange[1], offsetRange[1]]]
            combinedRangeCache.set(b.id, cached);
        }
        return cached;
    }

    const styleRanges = computed(() => {
        if(!isMounted.value) return [];
        labelsUpdated.value;

        let splitPoints = [];
        const bookmarks = fragmentBookmarks.value;

        for(const b of bookmarks) {
            const r = combinedRange(b);
            splitPoints.push(r[0])
            splitPoints.push(r[1])
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
            const labelCount = new Map();
            const bookmarksSet = new Set();

            bookmarks
                .filter( b => rangesOverlap(combinedRange(b), ordinalAndOffsetRange))
                .forEach(b => {
                    bookmarksSet.add(b.id);
                    filterLabels(b.labels).forEach(l => {
                        labels.add(l);
                        labelCount.set(l, (labelCount.get(l) || 0) + 1);
                    })
                });

            styleRanges.push({
                ordinalAndOffsetRange,
                labelCount,
                labels: Array.from(labels),
                bookmarks: Array.from(bookmarksSet),
            });
        }
        return styleRanges.filter(v => v.labels.length > 0);
    })

    function styleForStyleRange({labels, labelCount}) {
        return styleForLabels(Array.from(labels).map(v => ({id: v, label: bookmarkLabels.get(v)})), labelCount);
    }

    function styleForLabels(bookmarkLabels, labelCount) {
        let colors = [];
        for(const {label: s, id} of bookmarkLabels) {
            let c = new Color(s.color)
            c = c.alpha(0.3)
            for(let i = 0; i<labelCount.get(id)-1; i++) {
                c = c.opaquer(0.3).darken(0.1);
            }
            colors.push(c.hsl().string())
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
        const style = styleForStyleRange(styleRange)
        const {undo, highlightElements} = highlightRange(range, 'span', { style });
        undoHighlights.push(undo);

        const bookmarks = styleRange.bookmarks.map(bId => bookmarkMap.get(bId));

        for(const b of bookmarks.filter(b=>arrayEq(combinedRange(b)[0], [startOrdinal, startOff]))) {
            const icon = document.createElement("i")
            const faIcon = b.notes? editIcon : bookmarkIcon;
            icon.appendChild(faIcon.node[0])
            const bookmarkLabel = bookmarkLabels.get(b.labels[0]);
            const iconColor = new Color(bookmarkLabel.color).darken(0.2).hsl().string();
            icon.style = `color: ${iconColor};`;
            icon.classList.add("icon");
            icon.classList.add("skip-offset");
            icon.addEventListener("click", () => {
                emit(Events.NOTE_CLICKED, b);
            })
            const element = highlightElements[0];
            element.parentElement.insertBefore(icon, element);
            undoHighlights.push(() => icon.remove());
        }
    }

    watch(styleRanges, (newValue) => {
        undoHighlights.reverse();
        undoHighlights.forEach(v => v())
        undoHighlights.splice(0);
        for (const s of newValue) {
            highlightStyleRange(s);
        }
    }, {flush: 'post'});

    onMounted(() => {
        allStyleRangeArrays.add(styleRanges);
    })
    onUnmounted(() => {
        allStyleRangeArrays.delete(styleRanges);
    });

    return {styleRanges};
}
