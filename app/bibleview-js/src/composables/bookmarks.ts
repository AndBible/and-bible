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

import {computed, onMounted, onUnmounted, reactive, Ref, ref, watch} from "vue";
import {sortBy, uniqWith} from "lodash";
import {
    addEventFunction,
    arrayEq,
    EventPriorities,
    findNodeAtOffsetWithNullOffset,
    intersection,
    rangesOverlap
} from "@/utils";
import {setupEventBusListener} from "@/eventbus";
import {highlightRange} from "@/lib/highlight-range";
import {faBookmark, faEdit, faHeadphones} from "@fortawesome/free-solid-svg-icons";
import {Icon, icon} from "@fortawesome/fontawesome-svg-core";
import {AppSettings, Config, testMode} from "@/composables/config";
import {
    BaseBookmark,
    BibleBookmark,
    BookmarkOrdinalKey,
    CombinedRange,
    GenericBookmark,
    getBookmarkOrdinalKey,
    Label,
    LabelAndStyle,
    OffsetRange,
    OrdinalAndOffsetRange,
    OrdinalOffset,
    OrdinalRange,
} from "@/types/client-objects";
import {ColorParam} from "@/types/common";
import Color from "color";

type LabelId = IdType
type LabelCountMap = Map<LabelId, number>

type StyleRange = {
    highlightedBookmarkIds: Set<IdType>,
    ordinalAndOffsetRange: CombinedRange,
    highlightLabelCount: LabelCountMap,
    underlineLabelCount: LabelCountMap,
    hiddenLabelCount: LabelCountMap,
    highlightLabelIds: IdType[],
    underlineLabelIds: IdType[],
    hiddenLabelIds: IdType[],
    bookmarks: IdType[],
}

type LabelAndId = { id: IdType, label: LabelAndStyle }

const speakIcon = icon(faHeadphones);
const editIcon = icon(faEdit);
const bookmarkIcon = icon(faBookmark);

const allStyleRangeArrays = reactive<Set<Ref<StyleRange[]>>>(new Set());
const allStyleRanges = computed(() => {
    const allStyles = [];
    for (const a of allStyleRangeArrays) {
        allStyles.push(...a.value);
    }
    return allStyles;
});

export function isBibleBookmark(bookmark: BaseBookmark): bookmark is BibleBookmark {
    return bookmark.type === "bookmark"
}

export function isGenericBookmark(bookmark: BaseBookmark): bookmark is GenericBookmark {
    return bookmark.type === "generic-bookmark"
}

export function verseHighlighting(
    {
        highlightLabels,
        highlightLabelCount,
        underlineLabels,
        underlineLabelCount,
        highlightColorFn,
        appSettings,
    }: {
        highlightLabels: LabelAndId[],
        highlightLabelCount: LabelCountMap,
        underlineLabels: LabelAndId[],
        underlineLabelCount: LabelCountMap,
        highlightColorFn: (label: LabelAndStyle, count: number) => Color,
        appSettings: AppSettings,
    }): string
{
    // Percentage heights allocated to background highlight
    const bgHighlightPercentage = 64;
    const bgHighlightTopMargin = 4;
    const underlineHeight = 4;
    const spaceBetweenLines = 2;
    let gradientCSS = '';

    const monochromeUnderlineColor = Color(appSettings.nightMode ? "white": "black");

    {
        // Generate background gradients
        const highlightColors = [];
        let span = 0;
        for (const {label: s, id} of highlightLabels) {
            highlightColors.push(highlightColorFn(s, highlightLabelCount.get(id)!).hsl().string());
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
            for (let i = 0; i < underlineLabelCount.get(id)!; i++) {
                const color = appSettings.monochromeMode
                    ? monochromeUnderlineColor
                    : new Color(s.color).hsl();
                underlineColors.push(color.string());
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
    if (gradientCSS === "") return "";
    gradientCSS += `,transparent 0%`; // Fills the remainder of the background with a transparent color
    return `linear-gradient(to bottom, ${gradientCSS})`;
}

export function useGlobalBookmarks(config: Config) {
    const bookmarkLabels = reactive<Map<IdType, LabelAndStyle>>(new Map());
    const bookmarks = reactive<Map<IdType, BaseBookmark>>(new Map());
    const bookmarkIdsByOrdinal: Map<BookmarkOrdinalKey, Set<IdType>> = reactive(new Map());

    function addBookmarkToOrdinalMap(b: BaseBookmark) {
        for (let o = b.ordinalRange[0]; o <= b.ordinalRange[1]; o++) {
            const key = getBookmarkOrdinalKey(b, o);
            let bSet: Set<IdType> | undefined = bookmarkIdsByOrdinal.get(key);
            if (!bSet) {
                bSet = reactive<Set<IdType>>(new Set());
                bookmarkIdsByOrdinal.set(key, bSet);
            }
            bSet.add(b.id);
        }
    }

    function removeBookmarkFromOrdinalMap(b: BaseBookmark) {
        for (let o = b.ordinalRange[0]; o <= b.ordinalRange[1]; o++) {
            const bSet = bookmarkIdsByOrdinal.get(getBookmarkOrdinalKey(b, o))
            if (bSet) {
                bSet.delete(b.id);
            }
        }
    }

    let count = 1;

    const labelsUpdated = ref<number>(0);

    function updateBookmarkLabels(inputData: Label[]) {
        if (!inputData.length) return
        for (const v of inputData) {
            bookmarkLabels.set(v.id || `new-id-(${count++})`, {...v, ...v.style})
        }
        labelsUpdated.value++;
    }

    function updateBookmarks(inputData: BaseBookmark[]) {
        for (const v of inputData) {
            const bmark = {...v, hasNote: !!v.notes};
            bookmarks.set(v.id, bmark);
            addBookmarkToOrdinalMap(bmark);
        }
    }

    function clearBookmarks() {
        bookmarks.clear();
        bookmarkIdsByOrdinal.clear();
    }

    setupEventBusListener("remove_ranges", function removeRanges() {
        window.getSelection()!.removeAllRanges();
    })

    setupEventBusListener("delete_bookmarks", function deleteBookmarks(bookmarkIds: IdType[]) {
        for (const bId of bookmarkIds) {
            const bookmark = bookmarks.get(bId)!;
            removeBookmarkFromOrdinalMap(bookmark);
            bookmarks.delete(bId)
        }
    });

    setupEventBusListener("add_or_update_bookmarks", function addOrUpdateBookmarks(bookmarks: BaseBookmark[]) {
        updateBookmarks(bookmarks)
    });

    setupEventBusListener("bookmark_note_modified",
        ({id, notes, lastUpdatedOn}: { id: IdType, notes: string, lastUpdatedOn: number }) => {
            const b = bookmarks.get(id);
            if (b) {
                b.notes = notes;
                b.hasNote = !!notes;
                b.lastUpdatedOn = lastUpdatedOn
            }
        });

    setupEventBusListener("update_labels", function updateLabels(labels: Label[]) {
        return updateBookmarkLabels(labels);
    })

    window.bibleViewDebug.bookmarks = bookmarks;
    window.bibleViewDebug.allStyleRanges = allStyleRanges;
    window.bibleViewDebug.bookmarkLabels = bookmarkLabels;

    return {
        bookmarkLabels,
        bookmarkMap: bookmarks,
        bookmarks: computed(() => {
            if (config.disableBookmarking) return [];
            return Array.from(bookmarks.values());
        }),
        labelsUpdated,
        updateBookmarkLabels,
        updateBookmarks,
        allStyleRanges,
        clearBookmarks,
        bookmarkIdsByOrdinal,
    }
}

export function useBookmarks(
    documentId: string,
    ordinalRange: OrdinalRange,
    {bookmarks, bookmarkMap, bookmarkLabels, labelsUpdated}: {
        bookmarks: Ref<BaseBookmark[]>,
        bookmarkMap: Map<IdType, BaseBookmark>,
        bookmarkLabels: Map<IdType, LabelAndStyle>,
        labelsUpdated: Ref<number>
    },
    bookInitials: string,
    key: string|null,
    isBibleDocument: boolean,
    documentReady: Ref<boolean>,
    {adjustedColor}: { adjustedColor: (color: ColorParam) => Color },
    config: Config,
    appSettings: AppSettings)
{
    const isMounted = ref(0);

    onMounted(() => isMounted.value++);
    onUnmounted(() => isMounted.value--);

    const noOrdinalNeeded = (b: BaseBookmark) => b.ordinalRange === null && ordinalRange === null
    const checkOrdinal = (b: BaseBookmark) => {
        return b.ordinalRange !== null && ordinalRange !== null
            && rangesOverlap(b.ordinalRange, ordinalRange, {addRange: true, inclusive: true})
    };

    const checkOrdinalEnd = (b: BaseBookmark) => {
        if (b.ordinalRange == null && ordinalRange == null) return false
        const bOrdinalRange: OrdinalRange = [b.ordinalRange[1], b.ordinalRange[1]]
        return rangesOverlap(bOrdinalRange, ordinalRange, {addRange: true, inclusive: true})
    };

    function getBookmarkStyleLabel(bookmark: BaseBookmark) {
        return bookmarkLabels.get(bookmark.primaryLabelId || bookmark.labels[0])!;
    }

    function hasSpeakLabel(bookmark: BaseBookmark) {
        return bookmark.labels.some(l => bookmarkLabels.get(l)!.isSpeak);
    }

    const documentBookmarks = computed(() => {
        if (!documentReady.value) return [];
        return bookmarks.value.filter(b => {
            if(isBibleDocument) {
                return isBibleBookmark(b) && (noOrdinalNeeded(b) || checkOrdinal(b));
            } else {
                return isGenericBookmark(b) && bookInitials === b.bookInitials && key == b.key;
            }
        })
    });

    function truncateToOrdinalRange(bookmark: BaseBookmark): OrdinalAndOffsetRange {
        const b = {
            ordinalRange: bookmark.ordinalRange && bookmark.ordinalRange.slice() as OrdinalRange,
            offsetRange: bookmark.offsetRange && bookmark.offsetRange.slice() as OffsetRange,
        };
        b.offsetRange = b.offsetRange || [0, null]
        if (b.ordinalRange[0] < ordinalRange[0]) {
            b.ordinalRange[0] = ordinalRange[0];
            b.offsetRange[0] = 0;
        }
        if (b.ordinalRange[1] > ordinalRange[1]) {
            b.ordinalRange[1] = ordinalRange[1];
            b.offsetRange[1] = null;
        }
        return b as OrdinalAndOffsetRange;
    }

    function combinedRange(b: BaseBookmark): CombinedRange {
        const r = truncateToOrdinalRange(b);
        if (b.bookInitials !== bookInitials) {
            r.offsetRange[0] = 0;
            r.offsetRange[1] = null;
        }
        return [[r.ordinalRange[0], r.offsetRange[0]], [r.ordinalRange[1], r.offsetRange[1]]]
    }

    function removeZeroLengthRanges(splitPoints: OrdinalOffset[]) {
        const arr2 = [];
        for (let i = 0; i < splitPoints.length - 1; i++) {
            const [[ord1, off1], [ord2, off2]] = [splitPoints[i], splitPoints[i + 1]];

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
    function splitMore(splitPoints: OrdinalOffset[]): OrdinalOffset[] {
        const arr2: OrdinalOffset[] = [];
        for (let i = 0; i < splitPoints.length - 1; i++) {
            const [[startOrd, startOff], [endOrd, endOff]] = [splitPoints[i], splitPoints[i + 1]];
            arr2.push([startOrd, startOff])

            if (startOrd !== endOrd) {
                if (startOff) arr2.push([startOrd + 1, 0]);
                if (endOff && (!startOff || endOrd > startOrd + 1)) arr2.push([endOrd - 1, null]);
            }
        }

        if (splitPoints.length > 0) {
            arr2.push(splitPoints[splitPoints.length - 1]);
        }
        return arr2;
    }

    function sortedUniqueSplitPoints(splitPoints: OrdinalOffset[]) {
        let sps = sortBy(splitPoints, [v => v[0], v => {
            const val = v[1];
            if (val === null) return Number.MAX_VALUE;
            else return val;
        }
        ])
        sps = uniqWith(sps, (v1, v2) => v1[0] === v2[0] && v1[1] === v2[1]);
        sps = removeZeroLengthRanges(sps);
        sps = splitMore(sps);
        return sps;
    }

    function startPoint(point: OrdinalOffset): OrdinalOffset {
        if (point[1] === null) {
            return [point[0] + 1, 0];
        } else
            return point;
    }

    function endPoint(point: OrdinalOffset): OrdinalOffset {
        if (point[1] === 0) {
            return [point[0] - 1, null];
        } else
            return point;
    }

    function isHiddenBookmark(b: BaseBookmark, label = getBookmarkStyleLabel(b)) {
        return (b.wholeVerse && label.hideStyleWholeVerse) || (!b.wholeVerse && label.hideStyle);
    }

    function isMarkerBookmark(b: BaseBookmark, label = getBookmarkStyleLabel(b)) {
        return (b.wholeVerse && label.markerStyleWholeVerse) || (!b.wholeVerse && label.markerStyle);
    }

    function showHighlight(b: BaseBookmark) {
        return b.offsetRange == null || b.bookInitials === bookInitials;
    }

    const highlightBookmarks = computed<BaseBookmark[]>(() => documentBookmarks.value.filter(b => showHighlight(b)))

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

    const styleRanges = computed<StyleRange[]>(function styleRanges(): StyleRange[] {
        isMounted.value;
        if (!testMode && !isMounted.value) return [];
        labelsUpdated.value;

        let splitPoints: OrdinalOffset[] = [];

        for (const b of highlightBookmarks.value.map(v => {
            v.hasNote; // make hasNote a dependency for this styleRanges computed property
            return combinedRange(v)
        })) {
            splitPoints.push(b[0])
            splitPoints.push(b[1])
        }

        splitPoints = sortedUniqueSplitPoints(splitPoints)

        const styleRanges: StyleRange[] = [];
        const hideLabels = new Set(config.bookmarksHideLabels);

        for (let i = 0; i < splitPoints.length - 1; i++) {
            const ordinalAndOffsetRange: CombinedRange = [startPoint(splitPoints[i]), endPoint(splitPoints[i + 1])];
            const highlightLabels: Set<IdType> = new Set();
            const underlineLabels: Set<IdType> = new Set();

            const highlightLabelCount: LabelCountMap = new Map();
            const underlineLabelCount: LabelCountMap = new Map();

            const hiddenLabels: Set<IdType> = new Set();
            const hiddenLabelCount: LabelCountMap = new Map();
            const highlightedBookmarkIds: Set<IdType> = new Set();

            const filteredBookmarks = highlightBookmarks.value
                .filter(b => rangesOverlap(combinedRange(b), ordinalAndOffsetRange));

            filteredBookmarks.forEach(b => {
                const label = getBookmarkStyleLabel(b);
                const labelId = label.id;

                if (isHiddenBookmark(b, label) || isMarkerBookmark(b, label) || intersection(new Set(b.labels), hideLabels).size > 0) {
                    hiddenLabels.add(labelId);
                    hiddenLabelCount.set(labelId, (hiddenLabelCount.get(labelId) || 0) + 1);
                } else if ((b.wholeVerse && label.underlineWholeVerse) || (!b.wholeVerse && label.underline)) {
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

            if (highlightLabels.size > 0 || underlineLabels.size > 0 || hiddenLabels.size > 0) {
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

    function styleForStyleRange(styleRange: StyleRange) {
        const {highlightLabelIds, underlineLabelIds, highlightLabelCount, underlineLabelCount} = styleRange;
        const highlightLabels = Array.from(highlightLabelIds)
            .map(v => ({
                id: v,
                label: bookmarkLabels.get(v)!
            })).filter(l => !l.label.isSpeak);
        const underlineLabels = Array.from(underlineLabelIds)
            .map(v => ({
                id: v,
                label: bookmarkLabels.get(v)!
            })).filter(l => !l.label.isSpeak);

        return verseHighlighting({
            highlightLabels,
            highlightLabelCount,
            underlineLabels,
            underlineLabelCount,
            highlightColorFn: highlightColor,
            appSettings
        });
    }

    const monochromeHighlightColor = appSettings.nightMode
        ? Color.rgb(180, 180, 180)
        : Color.rgb(210, 210, 210);

    function highlightColor(label: LabelAndStyle, count: number): Color {
        if (appSettings.monochromeMode) {
            return monochromeHighlightColor;
        }
        let c = new Color(label.color)
        c = c.alpha(appSettings.nightMode ? 0.4 : 0.3)
        for (let i = 0; i < count - 1; i++) {
            c = c.opaquer(0.3).darken(0.2);
        }
        return c;
    }

    const undoHighlights: (() => void)[] = [];
    const undoMarkers: (() => void)[] = [];

    function getIconElement(faIcon: Icon, iconColor: string) {
        const icon = document.createElement("span")
        icon.appendChild(faIcon.node[0])
        icon.style.color = iconColor;
        icon.classList.add("bookmark-marker");
        icon.classList.add("skip-offset");
        return icon;
    }

    function highlightStyleRange(styleRange: StyleRange) {
        const [[startOrdinal, startOff], [endOrdinal, endOff]] = styleRange.ordinalAndOffsetRange;
        let firstElement: Element, lastElement: Element;
        const style = config.showBookmarks ? styleForStyleRange(styleRange) : "";
        const bookmarks = styleRange.bookmarks.map(bId => bookmarkMap.get(bId)!);

        function addBookmarkEventFunctions(event: MouseEvent) {
            for (const b of bookmarks) {
                const hidden = !styleRange.highlightedBookmarkIds.has(b.id);
                const priority = hidden ? EventPriorities.HIDDEN_BOOKMARK : EventPriorities.VISIBLE_BOOKMARK;
                addEventFunction(event, null, {bookmarkId: b.id, priority, hidden});
            }
        }

        // Handle whole verse spanning styling
        if (!startOff && !endOff) {
            firstElement = document.querySelector(`#doc-${documentId} #o-${startOrdinal}`) as HTMLElement;
            const lastOrdinal = (endOff === null ? endOrdinal : endOrdinal - 1)
            for (let ord = startOrdinal; ord <= lastOrdinal; ord++) {
                const elem = document.querySelector(`#doc-${documentId} #o-${ord}`) as HTMLElement;
                if (elem == null) {
                    console.error("Element is not found!", documentId, ord);
                    continue;
                }
                lastElement = elem;
                const oldStyle = elem.style.backgroundImage;
                elem.classList.add("bookmarked")
                elem.style.backgroundImage = style;
                elem.addEventListener("click", addBookmarkEventFunctions)
                undoHighlights.push(() => {
                    elem.style.backgroundImage = oldStyle;
                    elem.classList.remove("bookmarked")
                    elem.removeEventListener("click", addBookmarkEventFunctions)
                });

            }
        } else { // Handle styling of ranges that start or end within the verse
            const firstElem = document.querySelector(`#doc-${documentId} #o-${startOrdinal}`);
            const secondElem = document.querySelector(`#doc-${documentId} #o-${endOrdinal}`);
            if (firstElem === null || secondElem === null) {
                console.error("Element is not found!", documentId, startOrdinal, endOrdinal);
                return;
            }
            const first = findNodeAtOffsetWithNullOffset(firstElem, startOff);
            const second = findNodeAtOffsetWithNullOffset(secondElem, endOff);
            if (!(first && second)) {
                console.error("Node not found!", {first, second, firstElem, startOff, secondElem, endOff});
                return;
            }
            const range = new Range();
            range.setStart(first.node, first.offset);
            range.setEnd(second.node, second.offset);
            if (!range.collapsed) {
                const highlightResult = highlightRange(range, 'span', {style: `background-image: ${style};`, class: 'bookmarked'});
                if (highlightResult) {
                    const {undo, highlightElements} = highlightResult;
                    firstElement = highlightElements[0];
                    lastElement = highlightElements[highlightElements.length - 1];
                    highlightElements.forEach(elem => elem.addEventListener("click", (event: MouseEvent) => addBookmarkEventFunctions(event)));
                    undoHighlights.push(undo);
                } else {
                    console.error("Highlight range failed!", {
                        first, second,
                        firstElem, secondElem,
                        startOff, endOff,
                        startOrdinal, endOrdinal,
                        range,
                        style,
                    });
                    firstElement = firstElem;
                    lastElement = secondElem;
                }
            } else {
                firstElement = firstElem;
                lastElement = secondElem;
            }
        }
        // Add speak labels to the beginning of verse
        if (config.showBookmarks) {
            for (const b of bookmarks.filter(b => arrayEq(combinedRange(b)[0], [startOrdinal, startOff]))) {
                if (hasSpeakLabel(b)) {
                    const color = adjustedColor(appSettings.monochromeMode ? "black" : "red").string()
                    const iconElement = getIconElement(speakIcon, color);

                    iconElement.addEventListener("click", event => addEventFunction(event,
                        null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER}));
                    firstElement.parentElement!.insertBefore(iconElement, firstElement);
                    undoHighlights.push(() => iconElement.remove());
                }
            }
        }
        // Add bookmark marker for marker style bookmarks & My Notes symbols & to the end of bookmark
        if (config.showBookmarks || config.showMyNotes) {
            const bookmarkList: BaseBookmark[] = [];
            let hasNote = false;

            for (const b of bookmarks.filter(b => arrayEq(combinedRange(b)[1], [endOrdinal, endOff]))) {
                const bookmarkLabel = getBookmarkStyleLabel(b);
                if ((config.showBookmarks && isMarkerBookmark(b, bookmarkLabel)) || (config.showMyNotes && b.hasNote)) {
                    bookmarkList.push(b)
                    if (b.hasNote) {
                        hasNote = true;
                    }
                }
            }

            const bookmark = bookmarkList[0];
            if (bookmark) {
                const bookmarkLabel = getBookmarkStyleLabel(bookmark);
                const color = adjustedColor(appSettings.monochromeMode ? "black" : bookmarkLabel.color).string();
                const iconElement = getIconElement(hasNote ? editIcon : bookmarkIcon, color);
                iconElement.addEventListener("click", event => {
                    for (const b of bookmarkList) {
                        addEventFunction(event, null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER});
                    }
                });
                lastElement!.parentNode!.insertBefore(iconElement, lastElement!.nextSibling);
                if (bookmarkList.length > 1) {
                    iconElement.appendChild(document.createTextNode(`×${bookmarkList.length}`));
                }
                undoHighlights.push(() => iconElement.remove());
            }

        }
    }

    function addMarkers() {
        const bookmarkMap = new Map();
        const hideLabels = new Set(config.bookmarksHideLabels);

        for (const b of markerBookmarks.value) {
            // Add event listener to all verses within bookmark range
            for (let ordinal = b.ordinalRange[0]; ordinal <= b.ordinalRange[1]; ordinal++) {
                const elem = document.querySelector(`#doc-${documentId} #o-${ordinal}`) as HTMLElement;
                if (!elem) continue;
                const func = (event: MouseEvent) => {
                    addEventFunction(event, null, {
                        bookmarkId: b.id,
                        hidden: true,
                        priority: EventPriorities.HIDDEN_BOOKMARK
                    })
                };
                elem.addEventListener("click", func)
                undoMarkers.push(() => elem.removeEventListener("click", func));
            }

            // Marker will be put to the last verse, collect those to a map.
            const key = b.ordinalRange[1];
            if (intersection(new Set(b.labels), hideLabels).size === 0) {
                const value = bookmarkMap.get(key) || [];
                value.push(b);
                bookmarkMap.set(key, value);
            }
        }
        for (const [lastOrdinal, bookmarkList] of bookmarkMap) {
            const lastElement = document.querySelector(`#doc-${documentId} #o-${lastOrdinal}`) as HTMLElement;
            const b = bookmarkList[0];
            const bookmarkLabel = getBookmarkStyleLabel(b);
            const color = adjustedColor(appSettings.monochromeMode ? "black" : bookmarkLabel.color).string();
            const iconElement = getIconElement(b.hasNote ? editIcon : bookmarkIcon, color);
            iconElement.addEventListener("click", event => {
                for (const b of bookmarkList) {
                    addEventFunction(event, null, {bookmarkId: b.id, priority: EventPriorities.BOOKMARK_MARKER});
                }
            });
            if (bookmarkList.length > 1) {
                iconElement.appendChild(document.createTextNode(`×${bookmarkList.length}`));
            }
            lastElement.parentNode!.insertBefore(iconElement, lastElement.nextSibling);
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
        if (!isMounted.value) return;
        removeHighLights();
        addHighLights();
    }, {flush: 'post'});

    watch(markerBookmarks, () => {
        if (!isMounted.value) return;
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
