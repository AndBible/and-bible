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
import {sortBy, uniqWith} from "lodash";
import {
    addEventFunction,
    arrayEq,
    colorLightness,
    findNodeAtOffsetWithNullOffset, intersection,
    mixColors,
    rangesOverlap
} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {Events, setupEventBusListener} from "@/eventbus";
import {highlightRange} from "@/lib/highlight-range";
import {faEdit, faBookmark, faHeadphones} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import Color from "color";
import {bookmarkingModes, testMode} from "@/composables/index";
import {DocumentTypes} from "@/constants";

const speakIcon = icon(faHeadphones);
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

export function useGlobalBookmarks(config, documentType) {
    const bookmarkLabels = reactive(new Map());
    const bookmarks = reactive(new Map());
    let count = 1;

    const labelsUpdated = ref(0);

    function updateBookmarkLabels(...inputData) {
        if(!inputData.length) return
        for(const v of inputData) {
            bookmarkLabels.set(v.id || -(count++), {...v, ...v.style})
        }
        labelsUpdated.value ++;
    }

    function updateBookmarks(...inputData) {
        for(const v of inputData) {
            bookmarks.set(v.id, {...v, hasNote: !!v.notes})
        }
    }

    function clearBookmarks() {
        bookmarks.clear();
    }

    setupEventBusListener(Events.REMOVE_RANGES, function removeRanges() {
        window.getSelection().removeAllRanges();
    })

    setupEventBusListener(Events.DELETE_BOOKMARKS, function deleteBookmarks(bookmarkIds) {
        for (const bId of bookmarkIds) bookmarks.delete(bId)
    });

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARKS, function addOrUpdateBookmarks(bookmarks) {
        updateBookmarks(...bookmarks)
    });

    setupEventBusListener(Events.BOOKMARK_NOTE_MODIFIED, ({id, notes}) => {
        const b = bookmarks.get(id);
        if(b) {
            b.notes = notes;
            b.hasNote = !!notes;
        }
    });

    setupEventBusListener(Events.UPDATE_LABELS, function updateLabels(labels) {
        return updateBookmarkLabels(...labels);
    })

    const filteredBookmarks = computed(() => {
        if(documentType.value === DocumentTypes.BIBLE_DOCUMENT && !config.showBookmarks) return [];
        const allBookmarks = Array.from(bookmarks.values());
        if(documentType.value === DocumentTypes.JOURNAL || config.bookmarksHideLabels.length === 0) return allBookmarks;
        const hideLabels = new Set(config.bookmarksHideLabels);
        return allBookmarks.filter(v => intersection(new Set(v.labels), hideLabels).size === 0)
    })

    window.bibleViewDebug.bookmarks = bookmarks;
    window.bibleViewDebug.allStyleRanges = allStyleRanges;
    window.bibleViewDebug.bookmarkLabels = bookmarkLabels;

    return {
        bookmarkLabels, bookmarkMap: bookmarks, bookmarks: filteredBookmarks, labelsUpdated,
        updateBookmarkLabels, updateBookmarks, allStyleRanges, clearBookmarks,
    }
}

export function useBookmarks(documentId,
                             ordinalRange,
                             {bookmarks, bookmarkMap, bookmarkLabels, labelsUpdated},
                             bookInitials,
                             documentReady,
                             {adjustedColor},
                             config, appSettings) {

    const isMounted = ref(0);

    onMounted(() => isMounted.value ++);
    onUnmounted( () => isMounted.value --);

    const noOrdinalNeeded = (b) => b.ordinalRange === null && ordinalRange === null
    const checkOrdinal = (b) => {
        return b.ordinalRange !== null && ordinalRange !== null
        && rangesOverlap(b.ordinalRange, ordinalRange, {addRange: true, inclusive: true})
    };

    const checkOrdinalEnd = (b) => {
        if(b.ordinalRange == null && ordinalRange == null) return false
        const bOrdinalRange = [b.ordinalRange[1], b.ordinalRange[1]]
        return rangesOverlap(bOrdinalRange, ordinalRange, {addRange: true, inclusive: true})
    };

    const documentBookmarks = computed(() => {
        if(!documentReady.value) return [];
        return bookmarks.value.filter(b => (noOrdinalNeeded(b) || checkOrdinal(b)))
    });

    function truncateToOrdinalRange(bookmark) {
        const b = {ordinalRange: bookmark.ordinalRange, offsetRange: bookmark.offsetRange};
        b.offsetRange = b.offsetRange || [0, null]
        if(b.ordinalRange[0] < ordinalRange[0]) {
            b.ordinalRange[0] = ordinalRange[0];
            b.offsetRange[0] = 0;
        }
        if(b.ordinalRange[1] > ordinalRange[1]) {
            b.ordinalRange[1] = ordinalRange[1];
            b.offsetRange[1] = null;
        }
        return b;
    }

    function combinedRange(b) {
        const r = truncateToOrdinalRange(b);
        if(b.bookInitials !== bookInitials) {
            r.offsetRange[0] = 0;
            r.offsetRange[1] = null;
        }
        return [[r.ordinalRange[0], r.offsetRange[0]], [r.ordinalRange[1], r.offsetRange[1]]]
    }

    function removeZeroLengthRanges(splitPoints) {
        const arr2 = [];
        for (let i = 0; i < splitPoints.length - 1; i++) {
            const [[ord1, off1], [ord2, off2]] = [splitPoints[i], splitPoints[i+1]];

            if (!(ord2 === ord1 + 1 && off2 === 0 && off1 === null)) {
                arr2.push(splitPoints[i]);
            }
        }
        if (splitPoints.length > 0) {
            arr2.push(splitPoints[splitPoints.length - 1]);
        }
        return arr2;
    }

    /*
     Arbitrary (not verse-boundary-starting/ending) ranges that span multiple verses need to be split
     For example [1,1 - 2,end] => [1,1 - 1,end],[2,0 - 2,end], such that second range can be optimized
     when rendering highlight.
    */
    function splitMore(splitPoints) {
        const arr2 = [];
        for(let i = 0; i<splitPoints.length - 1; i++) {
            const [[startOrd, startOff], [endOrd, endOff]] = [splitPoints[i], splitPoints[i+1]];
            arr2.push([startOrd, startOff])

            if(startOrd !== endOrd) {
                if(startOff) arr2.push([startOrd  + 1, 0]);
                if(endOff && (!startOff || endOrd > startOrd + 1)) arr2.push([endOrd - 1, null]);
            }
        }

        if(splitPoints.length > 0) {
            arr2.push(splitPoints[splitPoints.length - 1]);
        }
        return arr2;

    }

    function sortedUniqueSplitPoints(splitPoints) {
        let sps = sortBy(splitPoints, [v => v[0], v => {
            const val = v[1];
            if(val === null) return Number.MAX_VALUE;
            else return val;
        }
        ])
        sps = uniqWith(sps, (v1, v2) => v1[0] === v2[0] && v1[1] === v2[1]);
        sps = removeZeroLengthRanges(sps);
        sps = splitMore(sps);
        return sps;
    }

    function startPoint(point) {
        if(point[1] === null) {
            return [point[0] +1, 0];
        } else
            return point;
    }

    function endPoint(point) {
        if(point[1] === 0) {
            return [point[0] -1, null];
        } else
            return point;
    }

    function showHighlight(b) {
        return b.offsetRange == null || b.bookInitials === bookInitials
    }

    const highlightBookmarks = computed(() => documentBookmarks.value.filter(b => showHighlight(b)))
    const markerBookmarks = computed(() => documentBookmarks.value.filter(b => !showHighlight(b) && checkOrdinalEnd(b)))

    const styleRanges = computed(function styleRanges() {
        isMounted.value;
        if(!testMode && !isMounted.value) return [];
        labelsUpdated.value;

        let splitPoints = [];
        const bookmarks = highlightBookmarks.value;

        for(const b of bookmarks.map(v => {
            v.hasNote; // make hasNote a dependency for this styleRanges computed property
            return combinedRange(v)
        })) {
            splitPoints.push(b[0])
            splitPoints.push(b[1])
        }

        splitPoints = sortedUniqueSplitPoints(splitPoints)

        const styleRanges = [];

        for(let i = 0; i < splitPoints.length-1; i++) {
            const ordinalAndOffsetRange = [startPoint(splitPoints[i]), endPoint(splitPoints[i+1])];
            const labels = new Set();
            const labelCount = new Map();

            const filteredBookmarks = bookmarks
                .filter(b => rangesOverlap(combinedRange(b), ordinalAndOffsetRange));

            filteredBookmarks.forEach(b => {
                const l = b.primaryLabelId || b.labels[0];
                labels.add(l);
                labelCount.set(l, (labelCount.get(l) || 0) + 1);
            });

            const containedBookmarks = filteredBookmarks.map(b => b.id);

            if(labels.size > 0) {
                styleRanges.push({
                    ordinalAndOffsetRange,
                    labelCount,
                    labels: Array.from(labels),
                    bookmarks: containedBookmarks,
                });
            }
        }
        return styleRanges;
    })

    function styleForStyleRange({labels, labelCount}) {
        const _bookmarkLabels = Array.from(labels).map(v => ({
            id: v,
            label: bookmarkLabels.get(v)
        })).filter(l => !l.label.noHighlight);
        switch(config.bookmarkingMode) {
            case bookmarkingModes.verticalColorBars:
                return verticalColorbarStyleForLabels(_bookmarkLabels, labelCount);
            case bookmarkingModes.blend:
                return blendingStyleForLabels(_bookmarkLabels, labelCount);
        }
    }

    function blendingStyleForLabels(bookmarkLabels, labelCount) {
        let colors = [];
        let darkenCoef = 0.0;

        for(const {label: s, id} of bookmarkLabels) {
            let c = new Color(s.color)
            darkenCoef += 0.3*(labelCount.get(id)-1);
            for(let i = 0; i<labelCount.get(id); i++) {
                colors.push(c);
            }
        }

        let color = mixColors(...colors)
            .alpha(0.4)
            .darken(Math.min(1.0, darkenCoef));

        if(colorLightness(color) > 0.9) {
            color = color.darken(0.2);
        }

        return `background-color: ${color.hsl().string()}`
    }

    function verticalColorbarStyleForLabels(bookmarkLabels, labelCount) {
        let colors = [];
        for(const {label: s, id} of bookmarkLabels) {
            let c = new Color(s.color)
            c = c.alpha(appSettings.nightMode? 0.4 : 0.3)
            for(let i = 0; i<labelCount.get(id)-1; i++) {
                c = c.opaquer(0.3).darken(0.2);
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

    function getIconElement(faIcon, iconColor) {
        const icon = document.createElement("span")
        icon.appendChild(faIcon.node[0])
        icon.style = `color: ${iconColor};`;
        icon.classList.add("bookmark-marker");
        icon.classList.add("skip-offset");
        return icon;
    }

    function highlightStyleRange(styleRange) {
        const [[startOrdinal, startOff], [endOrdinal, endOff]] = styleRange.ordinalAndOffsetRange;
        let firstElement, lastElement;
        const style = styleForStyleRange(styleRange)

        const bookmarks = styleRange.bookmarks.map(bId => bookmarkMap.get(bId));

        function addBookmarkEventFunctions(event) {
            for (const b of bookmarks) {
                addEventFunction(event, null, {bookmarkId: b.id, documentId});
            }
        }

        if(!startOff && !endOff) {
            firstElement = document.querySelector(`#doc-${documentId} #v-${startOrdinal}`);
            lastElement = firstElement;
            const lastOrdinal = (endOff === null ? endOrdinal : endOrdinal - 1)
            for(let ord = startOrdinal; ord <= lastOrdinal; ord ++) {
                const elem = document.querySelector(`#doc-${documentId} #v-${ord}`);
                const oldStyle = elem.style;
                elem.style = style;
                elem.addEventListener("click", addBookmarkEventFunctions)
                undoHighlights.push(() => {
                    elem.style = oldStyle;
                    elem.removeEventListener("click", addBookmarkEventFunctions)
                });

            }
        } else {
            const firstElem = document.querySelector(`#doc-${documentId} #v-${startOrdinal}`);
            const secondElem = document.querySelector(`#doc-${documentId} #v-${endOrdinal}`);
            if (firstElem === null || secondElem === null) {
                console.error("Element is not found!", documentId, startOrdinal, endOrdinal);
                return;
            }
            const [first, startOff1] = findNodeAtOffsetWithNullOffset(firstElem, startOff);
            const [second, endOff1] = findNodeAtOffsetWithNullOffset(secondElem, endOff);

            const range = new Range();
            range.setStart(first, startOff1);
            range.setEnd(second, endOff1);
            const highlightResult = highlightRange(range, 'span', {style});
            if(highlightResult) {
                const {undo, highlightElements} = highlightResult;
                firstElement = highlightElements[0];
                lastElement = highlightElements[highlightElements.length - 1];
                highlightElements.forEach(elem => elem.addEventListener("click", event => addBookmarkEventFunctions(event)));
                undoHighlights.push(undo);
            } else {
                console.error("Highlight range failed!", {first, second, firstElem, secondElem, startOff, endOff, startOff1, endOff1})
            }
        }

        for(const b of bookmarks.filter(b=>arrayEq(combinedRange(b)[0], [startOrdinal, startOff]))) {
            const speakLabel = b.labels.map(l => bookmarkLabels.get(l)).find(v => v.icon === "headphones");
            if (speakLabel) {
                const color = adjustedColor("red").string()
                const iconElement = getIconElement(speakIcon, color);

                iconElement.addEventListener("click", event => addEventFunction(event,
                    null, {bookmarkId: b.id, documentId}));
                firstElement.parentElement.insertBefore(iconElement, firstElement);
                undoHighlights.push(() => iconElement.remove());
            }
        }
        if(config.showMyNotes) {
            for (const b of bookmarks.filter(b => b.notes && arrayEq(combinedRange(b)[1], [endOrdinal, endOff]))) {
                const bookmarkLabel = bookmarkLabels.get(b.primaryLabelId || b.labels[0]);
                const color = adjustedColor(bookmarkLabel.color).string();
                const iconElement = getIconElement(b.notes ? editIcon : bookmarkIcon, color);

                iconElement.addEventListener("click", event => addEventFunction(event,
                    null, {bookmarkId: b.id, documentId}));
                lastElement.parentNode.insertBefore(iconElement, lastElement.nextSibling);
                undoHighlights.push(() => iconElement.remove());
            }
        }
    }

    function addMarkers() {
        const bookmarkMap = new Map();
        for (const b of markerBookmarks.value) {
            const key = b.ordinalRange[1];
            const value = bookmarkMap.get(key) || [];
            value.push(b);
            bookmarkMap.set(key, value);
        }
        for(const [lastOrdinal, bookmarkList] of bookmarkMap) {
            const lastElement = document.querySelector(`#doc-${documentId} #v-${lastOrdinal}`);
            const b = bookmarkList[0];
            const bookmarkLabel = bookmarkLabels.get(b.primaryLabelId || b.labels[0]);
            const color = adjustedColor(bookmarkLabel.color).string();
            const iconElement = getIconElement(bookmarkIcon, color);
            iconElement.addEventListener("click", event => {
                for(const b of bookmarkList) {
                    addEventFunction(event, null, {bookmarkId: b.id, documentId});
                }
            });
            if(bookmarkList.length>1) {
                iconElement.appendChild(document.createTextNode(`×${bookmarkList.length}`));
            }
            lastElement.parentNode.insertBefore(iconElement, lastElement.nextSibling);

            undoHighlights.push(() => iconElement.remove());
        }
    }

    watch(styleRanges, (newValue) => {
        if(!isMounted.value) return;
        undoHighlights.reverse();
        undoHighlights.forEach(v => v())
        undoHighlights.splice(0);
        for (const s of newValue) {
            highlightStyleRange(s);
        }
        addMarkers();
    }, {flush: 'post'});

    onMounted(() => {
        allStyleRangeArrays.add(styleRanges);
    })
    onUnmounted(() => {
        allStyleRangeArrays.delete(styleRanges);
    });

    return {styleRanges};
}
