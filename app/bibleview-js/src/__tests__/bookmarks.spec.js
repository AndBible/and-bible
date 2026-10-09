/*
 * Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import {useBookmarks, useGlobalBookmarks, verseHighlighting, bookmarkHighlightColor, monoFrameEdges} from "@/composables/bookmarks";
import {ref, defineComponent, h, nextTick} from "vue";
import {mount} from "@vue/test-utils";
import Color from "color";
import {useConfig} from "@/composables/config";
import {abbreviated} from "@/utils";
import { describe, it, expect, beforeEach, afterEach } from 'vitest'

window.bibleViewDebug = {}

describe("verseHighlight tests", () => {
    function test(highlightColors, underlineColors, result) {
        const highlightLabels = [];
        const highlightLabelCount = new Map();
        const underlineLabelCount = new Map();
        for(let i = 1; i<=highlightColors; i++) {
            highlightLabels.push({label: {color: i}, id: i});
            highlightLabelCount.set(i, 1);
        }
        const underlineLabels = [];
        for(let i = 1; i<=underlineColors; i++) {
            underlineLabels.push({label: {color: i}, id: i});
            underlineLabelCount.set(i, 1);
        }

        const highlightColorFn = (v) => Color(v.color);

        const css = verseHighlighting({highlightLabels, highlightLabelCount, underlineLabels, underlineLabelCount, highlightColorFn, appSettings: {nightMode: false}});
        expect(css).toBe(result);
    }

    it("test 1 highlight and 1 underline", () =>
        test(1, 1,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 64%,transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%,transparent 0%)"));


    it("test 2 highlight and 1 underline", () =>
        test(2, 1,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 34%, hsl(240, 100%, 0.4%) 34% 64%,transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%,transparent 0%)"));

    it("test 3 highlight and 1 underline", () =>
        test(3, 1,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 24%, hsl(240, 100%, 0.4%) 24% 44%, hsl(240, 100%, 0.6%) 44% 64%,transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%,transparent 0%)"));

    it("test 3 highlight and 2 underline", () =>
        test(3, 2,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 24%, hsl(240, 100%, 0.4%) 24% 44%, hsl(240, 100%, 0.6%) 44% 64%,transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%, transparent 70% 72%, hsl(240, 100%, 0.4%) 72% 76%,transparent 0%)"));

    it("test 3 highlight and 3 underline", () =>
        test(3, 2,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 24%, hsl(240, 100%, 0.4%) 24% 44%, hsl(240, 100%, 0.6%) 44% 64%,transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%, transparent 70% 72%, hsl(240, 100%, 0.4%) 72% 76%,transparent 0%)"));

    it("test 0 highlight and 1 underline", () =>
        test(0, 1,
            "linear-gradient(to bottom, transparent 64% 66%, hsl(240, 100%, 0.2%) 66% 70%,transparent 0%)"));

    it("test 1 highlight and 0 underline", () =>
        test(1,  0,
            "linear-gradient(to bottom, transparent 0% 4%, hsl(240, 100%, 0.2%) 4% 64%,transparent 0%)"));
});

describe("useBookmark tests", () => {
    let gb, b;
    let startOrd, startOff, endOrd, endOff;
    beforeEach(() => {
        const {config, appSettings} = useConfig();
        gb = useGlobalBookmarks(config, {value: "bible"});
        const fragmentReady = ref(true);
        b = useBookmarks(
            "fragKey",
            [10,20],
            gb,
            "KJV",
            null,
            true,
            fragmentReady,
            {adjustedColor: () => null},
            config,
            appSettings,
        );
        gb.updateBookmarkLabels([{
            id: 1,
            color: 1,
            displayStyle: "HIGHLIGHT",
            displayStyleWholeVerse: "HIGHLIGHT",
        }])
    });

    function addBookmarkId(id, ordinalRange, offsetRange = null) {
        gb.updateBookmarks([{
            id,
            ordinalRange,
            offsetRange,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            wholeVerse: false,
            type: "bookmark",
        }]);
    }

    function addBookmark(ordinalRange, offsetRange) {
        addBookmarkId(1, ordinalRange, offsetRange)
    }

    it("stylerange 1", () => {
        addBookmark([10, 10]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
    });
    it("stylerange 1.5", () => {
        addBookmark([9, 10]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
    });
    it("stylerange 2", () => {
        addBookmark([10, 10], [1, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 1]);
        expect([endOrd, endOff]).toEqual([10, 5]);
    });
    it("stylerange 3", () => {
        addBookmark([10, 11], null);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
    });
    it("stylerange 4", () => {
        addBookmark([10, 11], [0, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(2);
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [10, null]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[11, 0], [11, 5]]);
    });
    it("stylerange 5", () => {
        addBookmark([9, 11], [1, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(2);
        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 0]);
        expect([endOrd, endOff]).toEqual([11, 5]);
    });
    it("stylerange 6", () => {
        addBookmarkId(1, [10, 11], null);
        addBookmarkId(2, [10, 11], [2, 5]);
        const rs = b.styleRanges.value;
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [10, 2]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[10, 2], [10, null]]);
        expect(rs[2].ordinalAndOffsetRange).toEqual([[11, 0], [11, 5]]);
        expect(rs[3].ordinalAndOffsetRange).toEqual([[11, 5], [11, null]]);
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1,2]);
        expect(rs[3].bookmarks).toEqual([1]);
        expect(rs.length).toBe(4);

    });
    it("stylerange 7", () => {
        addBookmarkId(1, [10, 11], null);
        addBookmarkId(2, [10, 11], [0, 5]);
        const rs = b.styleRanges.value;

        expect(rs[0].bookmarks).toEqual([1,2]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1]);

        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 0]);
        expect([endOrd, endOff]).toEqual([11, 5]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[2].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 5]);
        expect([endOrd, endOff]).toEqual([11, null]);
        expect(rs.length).toBe(3);

    });
    it("stylerange 8", () => {
        addBookmarkId(1, [10, 15], null);
        addBookmarkId(2, [12, 12], null);
        const rs = b.styleRanges.value;
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1]);

        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([11, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([12, 0]);
        expect([endOrd, endOff]).toEqual([12, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[2].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([13, 0]);
        expect([endOrd, endOff]).toEqual([15, null]);
        expect(rs.length).toBe(3);
    });
    it("stylerange 9", () => {
        addBookmarkId(1, [10, 15], null);
        addBookmarkId(2, [12, 12], [3,5]);
        const rs = b.styleRanges.value;
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1]);
        expect(rs[2].bookmarks).toEqual([1,2]);
        expect(rs[3].bookmarks).toEqual([1]);
        expect(rs[4].bookmarks).toEqual([1]);

        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[2].ordinalAndOffsetRange).toEqual([[12, 3], [12, 5]]);
        expect(rs[3].ordinalAndOffsetRange).toEqual([[12, 5], [12, null]]);
        expect(rs[4].ordinalAndOffsetRange).toEqual([[13, 0], [15, null]]);
        expect(rs.length).toBe(5);
    });
    it("stylerange 10", () => {
        addBookmarkId(1, [10, 17], null);
        addBookmarkId(2, [12, 15], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1, 2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [15, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 5], [15, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[16, 0], [17, null]]);
        expect(rs.length).toBe(7);
    });
    it("stylerange 11", () => {
        addBookmarkId(1, [10, 16], null);
        addBookmarkId(2, [12, 14], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [13, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 0], [14, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 5], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [16, null]]);
        expect(rs.length).toBe(7);

    });

    it("stylerange 12", () => {
        addBookmarkId(1, [5, 30], null);
        addBookmarkId(2, [12, 14], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [13, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 0], [14, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 5], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [20, null]]);
        expect(rs.length).toBe(7);

    });

});

describe("marker visibility tests", () => {
    let gb, b;
    beforeEach(() => {
        const {config, appSettings} = useConfig();
        gb = useGlobalBookmarks(config, {value: "bible"});
        const fragmentReady = ref(true);
        b = useBookmarks(
            "fragKey",
            [10,20],
            gb,
            "KJV",
            null,
            true,
            fragmentReady,
            {adjustedColor: () => null},
            config,
            appSettings,
        );
    });

    it("hidden bookmark should be hidden from style ranges", () => {
        gb.updateBookmarkLabels([{id: 1, color: 1, displayStyle: "HIDDEN", displayStyleWholeVerse: "HIDDEN"}]);

        // Add a bookmark with this label
        gb.updateBookmarks([{
            id: 1,
            ordinalRange: [10, 10],
            offsetRange: null,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            wholeVerse: false,
            type: "bookmark",
        }]);

        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        
        // The bookmark should be marked as hidden, not highlighted
        expect(rs[0].hiddenLabelIds).toContain(1);
        expect(rs[0].highlightLabelIds).not.toContain(1);
        expect(rs[0].underlineLabelIds).not.toContain(1);
    });

    it("hidden bookmark (whole verse) should be hidden from style ranges", () => {
        gb.updateBookmarkLabels([{id: 1, color: 1, displayStyle: "HIGHLIGHT", displayStyleWholeVerse: "HIDDEN"}]);

        // Add a whole verse bookmark with this label
        gb.updateBookmarks([{
            id: 1,
            ordinalRange: [10, 10],
            offsetRange: null,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            wholeVerse: true,
            type: "bookmark",
        }]);

        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        
        // The bookmark should be marked as hidden, not highlighted
        expect(rs[0].hiddenLabelIds).toContain(1);
        expect(rs[0].highlightLabelIds).not.toContain(1);
        expect(rs[0].underlineLabelIds).not.toContain(1);
    });

    it("marker bookmark is moved to the hidden bucket for highlight processing", () => {
        gb.updateBookmarkLabels([{id: 1, color: 1, displayStyle: "MARKER", displayStyleWholeVerse: "MARKER"}]);

        // Add a bookmark with this label
        gb.updateBookmarks([{
            id: 1,
            ordinalRange: [10, 10],
            offsetRange: null,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            wholeVerse: false,
            type: "bookmark",
        }]);

        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        
        // MARKER shares the hidden bucket on purpose (see styleRanges): it draws an icon, no text decoration.
        expect(rs[0].hiddenLabelIds).toContain(1);
        expect(rs[0].highlightLabelIds).not.toContain(1);
        expect(rs[0].underlineLabelIds).not.toContain(1);
    });
});

describe("AI doc marker visibility tests", () => {
    // Page represents a single chapter spanning ordinals [10, 20].
    let gb, b;
    beforeEach(() => {
        const {config, appSettings} = useConfig();
        gb = useGlobalBookmarks(config, {value: "bible"});
        const fragmentReady = ref(true);
        b = useBookmarks(
            "fragKey",
            [10, 20],
            gb,
            "KJV",
            null,
            true,
            fragmentReady,
            {adjustedColor: () => null},
            config,
            appSettings,
        );
    });

    function addAiDocMarker(id, ordinalRange) {
        gb.updateBookmarks([{
            id,
            ordinalRange,
            offsetRange: null,
            labels: [],
            bookInitials: "KJV",
            notes: null,
            wholeVerse: false,
            type: "ai-doc-marker",
        }]);
    }

    const markerIds = () => b.markerBookmarks.value.map(m => m.id);

    it("shows the marker on the chapter where its range ends", () => {
        // Range ends at ordinal 18, which is on this page.
        addAiDocMarker(1, [12, 18]);
        expect(markerIds()).toEqual([1]);
    });

    it("shows the marker on the ending chapter even when the range starts in a previous chapter", () => {
        // Range started in the previous chapter (ordinal 5) but ends here at 18.
        addAiDocMarker(1, [5, 18]);
        expect(markerIds()).toEqual([1]);
    });

    it("does NOT show the marker on a chapter the range merely crosses (range ends in a later chapter)", () => {
        // Range starts here (15) but ends in the next chapter (25), beyond this page.
        // The robot icon must appear only on the chapter where the range ends - same as bookmarks.
        addAiDocMarker(1, [15, 25]);
        expect(markerIds()).toEqual([]);
    });

    it("does not show the marker on a chapter entirely after the range", () => {
        addAiDocMarker(1, [2, 8]);
        expect(markerIds()).toEqual([]);
    });
});

describe("abbreviate tests", () => {
    it("test 1", () => {
        expect(abbreviated("turhanpäiväisissä ajatuksissaan", 15)).toBe("turhanpäiväisi...")
        expect(abbreviated("höpö turhanpäiväisissä ajatuksissaan", 15)).toBe("höpö...")
        expect(abbreviated("höpö höpö turhanpäiväisissä ajatuksissaan", 15)).toBe("höpö höpö...")
        expect(abbreviated("höpö höpö", 15)).toBe("höpö höpö")
        expect(abbreviated("höpö höpö höpö", 15)).toBe("höpö höpö höpö")
        expect(abbreviated("höpö höpö höpö höpö", 15)).toBe("höpö höpö...")
    });
});

describe("color e-ink accent colors", () => {
    const label = {color: 0xFF0000};

    it("highlight: bw mode returns gray, color-eink returns the real color (day)", () => {
        const normal = bookmarkHighlightColor(label, 1, {monochromeMode: false, colorEinkMode: false, nightMode: false});
        const bw = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: false, nightMode: false});
        const colorEink = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: true, nightMode: false});
        expect(bw.hex()).toEqual("#D2D2D2");          // 210,210,210
        expect(colorEink.string()).toEqual(normal.string());
        expect(colorEink.hex()).not.toEqual(bw.hex());
    });

    it("highlight: bw night mode returns darker gray", () => {
        const bwNight = bookmarkHighlightColor(label, 1, {monochromeMode: true, colorEinkMode: false, nightMode: true});
        expect(bwNight.hex()).toEqual("#B4B4B4");      // 180,180,180
    });

    function underlineCss(appSettings) {
        return verseHighlighting({
            highlightLabels: [],
            highlightLabelCount: new Map(),
            underlineLabels: [{label: {color: 0xFF0000}, id: 1}],
            underlineLabelCount: new Map([[1, 1]]),
            highlightColorFn: (v) => Color(v.color),
            appSettings,
        });
    }

    it("underline: bw mode uses black, color-eink uses the label color (day)", () => {
        const bw = underlineCss({monochromeMode: true, colorEinkMode: false, nightMode: false});
        const colorEink = underlineCss({monochromeMode: true, colorEinkMode: true, nightMode: false});
        expect(bw).toContain(Color("black").string());
        expect(colorEink).toContain(new Color(0xFF0000).hsl().string());
        expect(colorEink).not.toContain(Color("black").string());
    });
});

describe("pure monochrome frame edges", () => {
    const r = (s, e, highlighted = true) => ({start: [s, null], end: [e, null], highlighted});
    const both = {frameStart: true, frameEnd: true};
    const neither = {frameStart: false, frameEnd: false};
    it("single range", () => expect(monoFrameEdges([r(1, 2)])).toEqual([both]));
    it("contiguous overlap splits", () => expect(monoFrameEdges([r(1, 2), r(2, 3), r(3, 4)])).toEqual([
        {frameStart: true, frameEnd: false}, neither, {frameStart: false, frameEnd: true},
    ]));
    it("gap", () => expect(monoFrameEdges([r(1, 2), r(5, 6)])).toEqual([both, both]));
    it("underline-only breaks a run", () => expect(monoFrameEdges([r(1, 2), r(2, 3, false), r(3, 4)])).toEqual([both, neither, both]));
    it("empty and unhighlighted", () => {
        expect(monoFrameEdges([])).toEqual([]);
        expect(monoFrameEdges([r(1, 2, false)])).toEqual([neither]);
    });
    it("joins canonical verse boundaries but not offset gaps", () => {
        expect(monoFrameEdges([
            {start: [1, 2], end: [1, 4], highlighted: true},
            {start: [1, 4], end: [1, null], highlighted: true},
            {start: [2, 0], end: [2, 3], highlighted: true},
            {start: [2, 4], end: [2, 5], highlighted: true},
        ])).toEqual([{frameStart: true, frameEnd: false}, neither, {frameStart: false, frameEnd: true}, both]);
    });
    it("transparent MONO, unchanged BW light and dark", () => {
        const label = {color: 0xFFFF0000};
        const settings = {monochromeMode: true, colorEinkMode: false, nightMode: false};
        expect(bookmarkHighlightColor(label, 1, {...settings, pureMonochromeMode: true}).alpha()).toBe(0);
        expect(bookmarkHighlightColor(label, 1, settings).rgb().array()).toEqual([210, 210, 210]);
        expect(bookmarkHighlightColor(label, 1, {...settings, nightMode: true}).rgb().array()).toEqual([180, 180, 180]);
    });
});

describe("pure monochrome bookmark DOM", () => {
    let wrapper, gb, appSettings, config;
    const bookmark = (id, ordinalRange, offsetRange = null) => ({
        id, ordinalRange, offsetRange, labels: [1], bookInitials: "KJV", notes: null,
        wholeVerse: false, type: "bookmark", editAction: {},
    });
    beforeEach(async () => {
        wrapper = mount(defineComponent({setup() {
            ({config, appSettings} = useConfig());
            Object.assign(appSettings, {pureMonochromeMode: true, monochromeMode: true, colorEinkMode: false});
            gb = useGlobalBookmarks(config, ref("bible"));
            useBookmarks("mono", [1, 3], gb, "KJV", null, true, ref(true), {adjustedColor: c => Color(c)}, config, appSettings);
            return () => h("div", {id: "doc-mono"}, [1, 2, 3].map(ord => h("span", {id: `o-${ord}`}, ["abc", h("em", "def"), "ghi"])));
        }}), {attachTo: document.body});
        gb.updateBookmarkLabels([{id: 1, color: 0xFFFF0000, displayStyle: "HIGHLIGHT", displayStyleWholeVerse: "HIGHLIGHT"}]);
        await nextTick();
    });
    afterEach(() => {
        window.bibleViewDebug.removeHighLights();
        wrapper.unmount();
    });
    const frames = () => document.querySelectorAll("#doc-mono .mono-frame");
    const outerEdges = () => {
        expect(document.querySelectorAll("#doc-mono .mono-frame-start")).toHaveLength(1);
        expect(document.querySelectorAll("#doc-mono .mono-frame-end")).toHaveLength(1);
    };
    it("whole-verse overlap has one frame and cleans up", async () => {
        gb.updateBookmarks([bookmark(1, [1, 2]), bookmark(2, [2, 3])]);
        await nextTick();
        expect(frames()).toHaveLength(3);
        outerEdges();
        frames().forEach(e => expect(e.style.backgroundImage).toBe(""));
        window.bibleViewDebug.removeHighLights();
        expect(frames()).toHaveLength(0);
        expect(document.querySelectorAll("#doc-mono .bookmarked, #doc-mono .mono-frame-start, #doc-mono .mono-frame-end")).toHaveLength(0);
    });
    it("partial overlap preserves nested text, offsets and only outer edges", async () => {
        gb.updateBookmarks([bookmark(1, [1, 3], [2, 7]), bookmark(2, [1, 2], [4, 5])]);
        await nextTick();
        outerEdges();
        expect(document.querySelector(".mono-frame-start").textContent).toBe("c");
        expect(document.querySelector(".mono-frame-end").textContent).toBe("g");
        expect(wrapper.text()).toBe("abcdefghi".repeat(3));
        window.bibleViewDebug.removeHighLights();
        expect(frames()).toHaveLength(0);
        expect(wrapper.text()).toBe("abcdefghi".repeat(3));
        expect(wrapper.findAll("em")).toHaveLength(3);
    });
    it("switches MONO to BW to NORMAL and back", async () => {
        gb.updateBookmarks([bookmark(1, [1, 3])]);
        await nextTick();
        outerEdges();
        appSettings.pureMonochromeMode = false;
        await nextTick();
        expect(frames()).toHaveLength(0);
        const bw = document.querySelector("#o-1").style.backgroundImage;
        expect(bw).toContain("linear-gradient");
        appSettings.monochromeMode = false;
        await nextTick();
        expect(document.querySelector("#o-1").style.backgroundImage).not.toBe(bw);
        Object.assign(appSettings, {monochromeMode: true, pureMonochromeMode: true});
        await nextTick();
        outerEdges();
        expect(document.querySelector("#o-1").style.backgroundImage).toBe("");
    });
    it.each(["UNDERLINE", "HIDDEN", "MARKER", "SPEAK"])("%s does not create frames", async style => {
        gb.updateBookmarkLabels([{id: 1, color: 0xFFFF0000, isSpeak: style === "SPEAK",
            displayStyle: style === "SPEAK" ? "HIGHLIGHT" : style,
            displayStyleWholeVerse: style === "SPEAK" ? "HIGHLIGHT" : style}]);
        gb.updateBookmarks([bookmark(1, [1, 3])]);
        await nextTick();
        expect(frames()).toHaveLength(0);
        if (style === "UNDERLINE") expect(document.querySelector("#o-1").style.backgroundImage).toContain("linear-gradient");
    });
    it("underline-only verse separates two highlight frames", async () => {
        gb.updateBookmarkLabels([{id: 2, color: 0xFF00FF00, displayStyle: "UNDERLINE", displayStyleWholeVerse: "UNDERLINE"}]);
        gb.updateBookmarks([bookmark(1, [1, 1]), {...bookmark(2, [2, 2]), labels: [2]}, bookmark(3, [3, 3])]);
        await nextTick();
        expect(frames()).toHaveLength(2);
        expect(document.querySelectorAll("#doc-mono .mono-frame-start")).toHaveLength(2);
        expect(document.querySelectorAll("#doc-mono .mono-frame-end")).toHaveLength(2);
        expect(document.querySelector("#o-2").style.backgroundImage).toContain("linear-gradient");
    });
    it("hidden and speak overlap splits do not add internal frame edges", async () => {
        gb.updateBookmarkLabels([
            {id: 2, color: 0xFF00FF00, displayStyle: "HIDDEN", displayStyleWholeVerse: "HIDDEN"},
            {id: 3, color: 0xFF0000FF, isSpeak: true, displayStyle: "HIGHLIGHT", displayStyleWholeVerse: "HIGHLIGHT"},
        ]);
        gb.updateBookmarks([bookmark(1, [1, 3]), {...bookmark(2, [2, 2]), labels: [2]}, {...bookmark(3, [2, 2]), labels: [3]}]);
        await nextTick();
        expect(frames()).toHaveLength(3);
        outerEdges();
    });
    it("highlight and underline overlap keeps the ink underline and the frame", async () => {
        gb.updateBookmarkLabels([{id: 2, color: 0xFF00FF00, displayStyle: "UNDERLINE", displayStyleWholeVerse: "UNDERLINE"}]);
        gb.updateBookmarks([bookmark(1, [1, 3]), {...bookmark(2, [2, 2]), labels: [2]}]);
        await nextTick();
        outerEdges();
        expect(frames()).toHaveLength(3);
        const light = document.querySelector("#o-2").style.backgroundImage;
        expect(light).toContain("rgb(0, 0, 0)");
        appSettings.nightMode = true;
        await nextTick();
        expect(document.querySelector("#o-2").style.backgroundImage).toContain("rgb(255, 255, 255)");
        expect(document.querySelector("#o-1").style.backgroundImage).toBe("");
        outerEdges();
    });
    it("hidden labels and disabled bookmarks suppress frames", async () => {
        config.bookmarksHideLabels = [1];
        gb.updateBookmarks([bookmark(1, [1, 3])]);
        await nextTick();
        expect(frames()).toHaveLength(0);
        config.bookmarksHideLabels = [];
        config.showBookmarks = false;
        await nextTick();
        expect(frames()).toHaveLength(0);
    });
});
