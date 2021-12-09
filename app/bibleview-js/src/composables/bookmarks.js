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
    EventPriorities,
    findNodeAtOffsetWithNullOffset, intersection,
    rangesOverlap
} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {Events, setupEventBusListener} from "@/eventbus";
import {highlightRange} from "@/lib/highlight-range";
import {faBookmark, faEdit, faHeadphones} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import Color from "color";
import {testMode} from "@/composables/config";

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

export function verseHighlighting({highlightLabels, highlightLabelCount, underlineLabels, underlineLabelCount, highlightColorFn}) {
    // Percentage heights allocated to background highlight
    const bgHighlightPercentage = 64;
    const bgHighlightTopMargin = 4;
    const underlineHeight = 4;
    const spaceBetweenLines = 2;
    let gradientCSS = '';

    {
        // Generate background gradients
        const highlightColors = [];
        let span = 0;
        for (const {label: s, id} of highlightLabels) {
            highlightColors.push(highlightColorFn(s, highlightLabelCount.get(id)).hsl().string());
        }
        if (highlightColors.length !== 0) {
            span = (bgHighlightPercentage - bgHighlightTopMargin) / highlightColors.length;
            gradientCSS += `transparent 0% ${bgHighlightTopMargin}%, `;
            gradientCSS += highlightColors.map((v, idx) => {
                const percent = `${span * idx + bgHighlightTopMargin}% ${span * (idx + 1) + bgHighlightTopMargin}%`
                return `${v} ${percent}`;
            }).join(", ");
        }
    }
    {
        // Generate underline gradients
        const underlineColors = [];
        let span = 0;
        for (const {label: s, id} of underlineLabels) {
            for(let i = 0; i < underlineLabelCount.get(id); i++) {
                underlineColors.push(new Color(s.color).hsl().string());
            }
        }
        if (underlineColors.length !== 0) {
            span = (100 - bgHighlightPercentage) / underlineColors.length;
            if (span > underlineHeight) {
                span = underlineHeight
            }
            let gradientPosition = bgHighlightPercentage;
            if (gradientCSS !== '') {
                gradientCSS += ','
            }
            gradientCSS += underlineColors.map(v => {
                const spacingGradient = `transparent ${gradientPosition}% ${gradientPosition + spaceBetweenLines}%`;
                gradientPosition += spaceBetweenLines
                const lineGradient = `${gradientPosition}% ${gradientPosition + span}%`
                gradientPosition += span;
                return `${spacingGradient}, ${v} ${lineGradient}`;
            }).join(", ")
        }
    }
    gradientCSS += `,transparent 0%`; // Fills the remainder of the background with a transparent color
    return `padding-bottom: 0.5em; background-image: linear-gradient(to bottom, ${gradientCSS});`;
}

export function useGlobalBookmarks(config) {
    const bookmarkLabels = reactive(new Map());
    const bookmarks = reactive(new Map());
    const bookmarkIdsByOrdinal = reactive(new Map());

    function addBookmarkToOrdinalMap(b) {
        for(let o = b.ordinalRange[0]; o <= b.ordinalRange[1]; o++) {
            let bSet = bookmarkIdsByOrdinal.get(o);
            if(!bSet) {
                bSet = new reactive(new Set());
                bookmarkIdsByOrdinal.set(o, bSet);
            }
            bSet.add(b.id);
        }
    }

    function removeBookmarkFromOrdinalMap(b) {
        for(let o = b.ordinalRange[0]; o <= b.ordinalRange[1]; o++) {
            const bSet = bookmarkIdsByOrdinal.get(o)
            if(bSet) {
                bSet.delete(b.id);
            }
        }
    }

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
            const bmark = {...v, hasNote: !!v.notes};
            bookmarks.set(v.id, bmark);
            addBookmarkToOrdinalMap(bmark);
        }
    }

    function clearBookmarks() {
        bookmarks.clear();
        bookmarkIdsByOrdinal.clear();
    }

    setupEventBusListener(Events.REMOVE_RANGES, function removeRanges() {
        window.getSelection().removeAllRanges();
    })

    setupEventBusListener(Events.DELETE_BOOKMARKS, function deleteBookmarks(bookmarkIds) {
        for (const bId of bookmarkIds) {
            const bookmark = bookmarks.get(bId);
            removeBookmarkFromOrdinalMap(bookmark);
            bookmarks.delete(bId)
        }
    });

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARKS, function addOrUpdateBookmarks(bookmarks) {
        updateBookmarks(...bookmarks)
    });

    setupEventBusListener(Events.BOOKMARK_NOTE_MODIFIED, ({id, notes, lastUpdatedOn}) => {
        const b = bookmarks.get(id);
        if(b) {
            b.notes = notes;
            b.hasNote = !!notes;
            b.lastUpdatedOn = lastUpdatedOn
        }
    });

    setupEventBusListener(Events.UPDATE_LABELS, function updateLabels(labels) {
        return updateBookmarkLabels(...labels);
    })

    window.bibleViewDebug.bookmarks = bookmarks;
    window.bibleViewDebug.allStyleRanges = allStyleRanges;
    window.bibleViewDebug.bookmarkLabels = bookmarkLabels;

    return {
        bookmarkLabels,
        bookmarkMap: bookmarks,
        bookmarks: computed(() => {
            if(config.disableBookmarking) return [];
            return Array.from(bookmarks.values());
        }),
        labelsUpdated,
        updateBookmarkLabels, updateBookmarks, allStyleRanges, clearBookmarks, bookmarkIdsByOrdinal,
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
        const b = {
            ordinalRange: bookmark.ordinalRange && bookmark.ordinalRange.slice(),
            offsetRange: bookmark.offsetRange && bookmark.offsetRange.slice(),
        };
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
        return b.offsetRange == null || b.bookInitials === bookInitials;
    }

    const highlightBookmarks = computed(() => {
        return documentBookmarks.value.filter(b => showHighlight(b))
    })

    const markerBookmarks = computed(
        () => {
            isMounted.value;
            return documentBookmarks.value
                .filter(b =>
                    !showHighlight(b) &&
                    checkOrdinalEnd(b) &&
                    ((b.hasNote && config.showMyNotes) || config.showBookmarks)
                )
        }
    )

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
        const hideLabels = new Set(config.bookmarksHideLabels);

        for(let i = 0; i < splitPoints.length-1; i++) {
            const ordinalAndOffsetRange = [startPoint(splitPoints[i]), endPoint(splitPoints[i+1])];
            const highlightLabels = new Set();
            const underlineLabels = new Set();

            const highlightLabelCount = new Map();
            const underlineLabelCount = new Map();

            const hiddenLabels = new Set();
            const hiddenLabelCount = new Map();
            const highlightedBookmarkIds = new Set();

            const filteredBookmarks = bookmarks
                .filter(b => rangesOverlap(combinedRange(b), ordinalAndOffsetRange));

            filteredBookmarks.forEach(b => {
                const labelId = b.primaryLabelId || b.labels[0];
                const label = bookmarkLabels.get(labelId);

                if(intersection(new Set(b.labels), hideLabels).size > 0) {
                    hiddenLabels.add(labelId);
                    hiddenLabelCount.set(labelId, (hiddenLabelCount.get(labelId) || 0) + 1);
                }
                else if((b.wholeVerse && label.underlineWholeVerse) || (!b.wholeVerse && label.underline)) {
                    underlineLabels.add(labelId);
                    underlineLabelCount.set(labelId, (underlineLabelCount.get(labelId) || 0) + 1);
                    highlightedBookmarkIds.add(b.id);
                } else {
                    highlightLabels.add(labelId);
                    highlightLabelCount.set(labelId, (highlightLabelCount.get(labelId) || 0) + 1);
                    highlightedBookmarkIds.add(b.id);
                }
            });

            const containedBookmarks = filteredBookmarks.map(b => b.id);

            if(highlightLabels.size > 0 || underlineLabels.size > 0 || hiddenLabels.size > 0) {
                styleRanges.push({
                    highlightedBookmarkIds,
                    ordinalAndOffsetRange,
                    highlightLabelCount,
                    underlineLabelCount,
                    hiddenLabelCount,
                    highlightLabelIds: Array.from(highlightLabels),
                    underlineLabelIds: Array.from(underlineLabels),
                    hiddenLabelIds: Array.from(hiddenLabels),
                    bookmarks: containedBookmarks,
                });
            }
        }
        return styleRanges;
    })

    function styleForStyleRange(styleRange) {
        const {highlightLabelIds, underlineLabelIds, highlightLabelCount, underlineLabelCount} = styleRange;
        const highlightLabels = Array.from(highlightLabelIds)
            .map(v => ({
                id: v,
                label: bookmarkLabels.get(v)
            })).filter(l => !l.label.noHighlight);
        const underlineLabels = Array.from(underlineLabelIds)
            .map(v => ({
                id: v,
                label: bookmarkLabels.get(v)
            })).filter(l => !l.label.noHighlight);

        return verseHighlighting({highlightLabels, highlightLabelCount, underlineLabels, underlineLabelCount, highlightColorFn: highlightColor});
    }

    function highlightColor(label, count) {
        let c = new Color(label.color)
        c = c.alpha(appSettings.nightMode? 0.4 : 0.3)
        for(let i = 0; i<count-1; i++) {
            c = c.opaquer(0.3).darken(0.2);
        }
        return c;
    }

    const undoHighlights = [];
    const undoMarkers = [];

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
        const style = config.showBookmarks ? styleForStyleRange(styleRange) : "";
        const bookmarks = styleRange.bookmarks.map(bId => bookmarkMap.get(bId));

        function addBookmarkEventFunctions(event) {
            for (const b of bookmarks) {
                const hidden = !styleRange.highlightedBookmarkIds.has(b.id);
                const priority = hidden ? EventPriorities.HIDDEN_BOOKMARK : EventPriorities.VISIBLE_BOOKMARK;
                addEventFunction(event, null, {bookmarkId: b.id, priority, hidden});
            }
        }

        if(!startOff && !endOff) {
            firstElement = document.querySelector(`#doc-${documentId} #o-${startOrdinal}`);
            const lastOrdinal = (endOff === null ? endOrdinal : endOrdinal - 1)
            for(let ord = startOrdinal; ord <= lastOrdinal; ord ++) {
                const elem = document.querySelector(`#doc-${documentId} #o-${ord}`);
                if(elem == null) {
                    console.error("Element is not found!", documentId, ord);
                    return;
                }
                lastElement = elem;
                const oldStyle = elem.style;
                elem.style = style;
                elem.addEventListener("click", addBookmarkEventFunctions)
                undoHighlights.push(() => {
                    elem.style = oldStyle;
                    elem.removeEventListener("click", addBookmarkEventFunctions)
                });

            }
        } else {
            const firstElem = document.querySelector(`#doc-${documentId} #o-${startOrdinal}`);
            const secondElem = document.querySelector(`#doc-${documentId} #o-${endOrdinal}`);
            if (firstElem === null || secondElem === null) {
                console.error("Element is not found!", documentId, startOrdinal, endOrdinal);
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
            const highlightResult = highlightRange(range, 'span', {style});
            if(highlightResult) {
                const {undo, highlightElements} = highlightResult;
                firstElement = highlightElements[0];
                lastElement = highlightElements[highlightElements.length - 1];
                highlightElements.forEach(elem => elem.addEventListener("click", event => addBookmarkEventFunctions(event)));
                undoHighlights.push(undo);
            } else {
                console.error("Highlight range failed!", {first, second, firstElem, secondElem, startOff, endOff, startOff1, endOff1})
                return;
            }
        }
        if(config.showBookmarks) {
            for (const b of bookmarks.filter(b => arrayEq(combinedRange(b)[0], [startOrdinal, startOff]))) {
                const speakLabel = b.labels.map(l => bookmarkLabels.get(l)).find(v => v.icon === "headphones");
                if (speakLabel) {
                    const color = adjustedColor("red").string()
                    const iconElement = getIconElement(speakIcon, color);

                    iconElement.addEventListener("click", event => addEventFunction(event,
                        null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER}));
                    firstElement.parentElement.insertBefore(iconElement, firstElement);
                    undoHighlights.push(() => iconElement.remove());
                }
            }
        }
        if(config.showMyNotes) {
            for (const b of bookmarks.filter(b => b.hasNote && arrayEq(combinedRange(b)[1], [endOrdinal, endOff]))) {
                const bookmarkLabel = bookmarkLabels.get(b.primaryLabelId || b.labels[0]);
                const color = adjustedColor(bookmarkLabel.color).string();
                const iconElement = getIconElement(b.hasNote ? editIcon : bookmarkIcon, color);

                iconElement.addEventListener("click", event => addEventFunction(event,
                    null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER}));
                lastElement.parentNode.insertBefore(iconElement, lastElement.nextSibling);
                undoHighlights.push(() => iconElement.remove());
            }
        }
    }

    function addMarkers() {
        const bookmarkMap = new Map();
        const hideLabels = new Set(config.bookmarksHideLabels);

        for (const b of markerBookmarks.value) {
            for(let ordinal = b.ordinalRange[0]; ordinal <= b.ordinalRange[1]; ordinal++) {
                const elem = document.querySelector(`#doc-${documentId} #o-${ordinal}`);
                const func = event => {
                    addEventFunction(event, null, {bookmarkId: b.id, hidden: true, priority: EventPriorities.HIDDEN_BOOKMARK})
                };
                elem.addEventListener("click", func)
                undoMarkers.push(() => elem.removeEventListener("click", func));
            }

            const key = b.ordinalRange[1];
            if(intersection(new Set(b.labels), hideLabels).size === 0) {
                const value = bookmarkMap.get(key) || [];
                value.push(b);
                bookmarkMap.set(key, value);
            }
        }
        for(const [lastOrdinal, bookmarkList] of bookmarkMap) {
            const lastElement = document.querySelector(`#doc-${documentId} #o-${lastOrdinal}`);
            const b = bookmarkList[0];
            const bookmarkLabel = bookmarkLabels.get(b.primaryLabelId || b.labels[0]);
            const color = adjustedColor(bookmarkLabel.color).string();
            const iconElement = getIconElement(b.hasNote ? editIcon : bookmarkIcon, color);
            iconElement.addEventListener("click", event => {
                for(const b of bookmarkList) {
                    addEventFunction(event, null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER});
                }
            });
            if(bookmarkList.length>1) {
                iconElement.appendChild(document.createTextNode(`×${bookmarkList.length}`));
            }
            lastElement.parentNode.insertBefore(iconElement, lastElement.nextSibling);

            undoMarkers.push(() => iconElement.remove());
        }
    }

    function removeHighLights() {
        undoHighlights.forEach(v => v())
        undoHighlights.splice(0);
    }

    function removeMarkers() {
        undoMarkers.forEach(v => v())
        undoMarkers.splice(0);
    }

    function addHighLights() {
        for (const s of styleRanges.value) {
            highlightStyleRange(s);
        }
    }

    window.bibleViewDebug.removeHighLights = removeHighLights;
    window.bibleViewDebug.addHighLights = addHighLights;

    watch(styleRanges, () => {
        if(!isMounted.value) return;
        removeHighLights();
        addHighLights();
    }, {flush: 'post'});

    watch(markerBookmarks, () => {
        if(!isMounted.value) return;
        removeMarkers();
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
