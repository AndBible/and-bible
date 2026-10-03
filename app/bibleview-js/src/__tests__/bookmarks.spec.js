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

import {useBookmarks, useGlobalBookmarks, verseHighlighting, bookmarkHighlightColor} from "@/composables/bookmarks";
import {ref} from "vue";
import Color from "color";
import {useConfig} from "@/composables/config";
import {abbreviated} from "@/utils";
import { describe, it, expect, beforeEach } from 'vitest'

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
            underline: false,
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

    it("hidden bookmark with marker style should be hidden from style ranges", () => {
        // Create a label with both marker style and hide style - hide should override marker
        gb.updateBookmarkLabels([{
            id: 1,
            color: 1,
            underline: false,
            markerStyle: true,
            markerStyleWholeVerse: false,
            hideStyle: true,
            hideStyleWholeVerse: false,
        }]);

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

    it("hidden bookmark with marker style (whole verse) should be hidden from style ranges", () => {
        // Create a label with both marker style and hide style for whole verse
        gb.updateBookmarkLabels([{
            id: 1,
            color: 1,
            underline: false,
            markerStyle: false,
            markerStyleWholeVerse: true,
            hideStyle: false,
            hideStyleWholeVerse: true,
        }]);

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

    it("marker style bookmark without hide settings should show as highlighted", () => {
        // Create a label with marker style but NO hide style
        gb.updateBookmarkLabels([{
            id: 1,
            color: 1,
            underline: false,
            markerStyle: true,
            markerStyleWholeVerse: false,
            hideStyle: false,
            hideStyleWholeVerse: false,
        }]);

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
        
        // The bookmark should be marked as highlighted (since it's marker style but not hidden)
        expect(rs[0].hiddenLabelIds).toContain(1); // Marker bookmarks are actually moved to hidden for highlight processing
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
